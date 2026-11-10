package com.qingluan.ledger

import android.animation.ValueAnimator
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupTransitionTest {
    @Test
    fun nativeExitFinishesOnceAndDisabledMotionIsImmediate() {
        val view = View(RuntimeEnvironment.getApplication())
        var removed = 0
        assertNull(animateStartupExit(view, false) { removed++ })
        assertEquals(1, removed)
        removed = 0
        val animation = animateStartupExit(view, true) { removed++ }
        if (ValueAnimator.areAnimatorsEnabled()) {
            assertNotNull(animation)
            assertEquals(180, animation!!.duration)
            animation.end()
            animation.cancel()
            assertEquals(1, removed)
            assertEquals(0f, view.alpha, .001f)
            assertEquals(1.025f, view.scaleX, .001f)
        } else assertEquals(1, removed)
    }
}
