package com.qingluan.ledger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun decodeAvatar(context: Context, uri: Uri): Bitmap =
    withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
        val options =
            BitmapFactory.Options().apply {
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                inSampleSize = sample
            }
        val raw =
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("无法读取图片")
        val orientation =
            context.contentResolver.openInputStream(uri)?.use {
                runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
                    .getOrDefault(1)
            } ?: 1
        val matrix =
            Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> {
                        setRotate(90f)
                        postScale(-1f, 1f)
                    }
                    6 -> setRotate(90f)
                    7 -> {
                        setRotate(-90f)
                        postScale(-1f, 1f)
                    }
                    8 -> setRotate(-90f)
                }
            }
        val oriented = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (oriented !== raw) raw.recycle()
        oriented
    }

fun avatarCropRect(image: Bitmap, zoom: Float, panX: Float, panY: Float): android.graphics.Rect {
    val side = (minOf(image.width, image.height) / zoom.coerceIn(1f, 4f)).toInt().coerceAtLeast(1)
    val x = (image.width / 2f - panX * side - side / 2f).toInt().coerceIn(0, image.width - side)
    val y = (image.height / 2f - panY * side - side / 2f).toInt().coerceIn(0, image.height - side)
    return android.graphics.Rect(x, y, x + side, y + side)
}

fun avatarCrop(image: Bitmap, zoom: Float, panX: Float, panY: Float): Bitmap {
    val rect = avatarCropRect(image, zoom, panX, panY)
    val square = Bitmap.createBitmap(image, rect.left, rect.top, rect.width(), rect.height())
    val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
    if (square !== image && square !== scaled) square.recycle()
    return if (scaled === image) image.copy(Bitmap.Config.ARGB_8888, false) else scaled
}

suspend fun commitAvatar(context: Context, image: Bitmap) =
    withContext(Dispatchers.IO) {
        val file = android.util.AtomicFile(File(context.filesDir, "avatar.jpg"))
        val stream = file.startWrite()
        try {
            check(image.compress(Bitmap.CompressFormat.JPEG, 90, stream))
            file.finishWrite(stream)
        } catch (e: Throwable) {
            file.failWrite(stream)
            throw e
        }
    }

@Composable
fun ProfileHeader(vm: LedgerViewModel, rows: List<Entry>) {
    val context = LocalContext.current
    val profile by vm.profile.collectAsState()
    val hideAmounts = LocalExperience.current.hideAmounts
    val stats = remember(rows) { profileStats(rows) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var nickname by rememberSaveable(profile.nickname) { mutableStateOf(profile.nickname) }
    val feedback = rememberAppFeedback()
    var image by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(profile.avatarRevision) {
        image =
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(File(context.filesDir, "avatar.jpg").path)
            }
    }
    DisposableEffect(image) {
        val old = image
        onDispose { old?.recycle() }
    }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var cropCameraPath by remember { mutableStateOf("") }
    var cameraPath by rememberSaveable { mutableStateOf("") }
    val photo = rememberImagePicker({ cropUri = it }, { vm.message.value = "无法打开系统图片选择器" })
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (cameraPath.isNotEmpty()) {
                val file = File(cameraPath)
                if (success) {
                    val uri =
                        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    cropCameraPath = file.path
                    cropUri = uri
                } else file.delete()
                cameraPath = ""
            }
        }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(76.dp).elasticClickable { editing = true }.testTag("profile-avatar")) {
            Box(Modifier.fillMaxSize().clip(CircleShape)) {
                val avatarImage = image
                if (avatarImage != null)
                    Image(
                        avatarImage.asImageBitmap(),
                        "头像",
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                else
                    Image(
                        painterResource(R.drawable.brand_logo),
                        "头像",
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
            }
            Surface(
                Modifier.align(Alignment.BottomEnd).size(24.dp).testTag("profile-edit-badge"),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Outlined.Edit,
                    null,
                    Modifier.padding(4.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        TextButton(onClick = { editing = true }) { Text(profile.nickname.ifBlank { "青鸾记账" }) }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                    "天数" to stats.days.toString(),
                    "笔数" to stats.count.toString(),
                    "结余" to
                        stats.balances.entries.joinToString("\n") { (code, amount) ->
                            "${currencyPrefix(code)} ${visibleMoney(amount, code, hideAmounts)}"
                        },
                )
                .forEach { (label, value) ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            value,
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
        }
    }
    if (editing)
        GlassAlertDialog(
            onDismissRequest = {
                editing = false
                nickname = profile.nickname
            },
            title = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("个人资料", Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            vm.resetProfile()
                            nickname = ""
                            editing = false
                        }
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Outlined.RestartAlt, "重置个人资料")
                    }
                }
            },
            text = {
                Column {
                    OutlinedTextField(
                        nickname,
                        { nickname = it.take(24) },
                        Modifier.fillMaxWidth().testTag("profile-nickname"),
                        singleLine = true,
                        label = { Text("昵称") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            onClick = {
                                feedback()
                                try {
                                    photo()
                                } catch (_: Exception) {
                                    vm.message.value = "无法打开相册"
                                }
                            }
                        ) {
                            Text("相册")
                        }
                        TextButton(
                            onClick = {
                                feedback()
                                try {
                                    val directory =
                                        File(context.cacheDir, "camera").apply { mkdirs() }
                                    val file = File.createTempFile("avatar-", ".jpg", directory)
                                    cameraPath = file.path
                                    camera.launch(
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.files",
                                            file,
                                        )
                                    )
                                } catch (e: Exception) {
                                    if (cameraPath.isNotEmpty()) File(cameraPath).delete()
                                    cameraPath = ""
                                    vm.message.value =
                                        if (e is android.content.ActivityNotFoundException)
                                            "没有可用的相机"
                                        else "无法打开相机"
                                }
                            }
                        ) {
                            Text("拍照")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.nickname(nickname)
                        editing = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editing = false
                        nickname = profile.nickname
                    }
                ) {
                    Text("取消")
                }
            },
        )
    cropUri?.let { uri ->
        AvatarCropDialog(
            uri,
            dismiss = {
                cropUri = null
                if (cropCameraPath.isNotBlank()) File(cropCameraPath).delete()
                cropCameraPath = ""
            },
            save = { bitmap ->
                vm.action {
                    try {
                        commitAvatar(context, bitmap)
                        context.preferences.edit {
                            it[
                                androidx.datastore.preferences.core.longPreferencesKey(
                                    "avatarRevision"
                                )] = System.nanoTime()
                        }
                        if (cropCameraPath.isNotBlank()) File(cropCameraPath).delete()
                        cropCameraPath = ""
                        cropUri = null
                    } finally {
                        cropUri = null
                        bitmap.recycle()
                    }
                }
            },
        )
    }
}

data class LocalProfile(val nickname: String = "", val avatarRevision: Long = 0)
