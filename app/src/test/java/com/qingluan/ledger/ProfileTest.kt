package com.qingluan.ledger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileTest {
    @Test
    fun avatarImportIsBoundedAndFailureKeepsPreviousImage() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val original = File(context.cacheDir, "test-avatar.png")
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        storeAvatar(context, Uri.fromFile(original))
        val target = File(context.filesDir, "avatar.jpg")
        val result = BitmapFactory.decodeFile(target.path)
        assertEquals(512, result.width)
        assertEquals(512, result.height)
        result.recycle()
        val before = target.readBytes()
        original.writeText("invalid image")
        assertTrue(runCatching { storeAvatar(context, Uri.fromFile(original)) }.isFailure)
        assertArrayEquals(before, target.readBytes())
        original.delete()
        target.delete()
        Unit
    }
}
