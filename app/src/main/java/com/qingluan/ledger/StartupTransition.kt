package com.qingluan.ledger

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator

const val STARTUP_REVEAL_MILLIS = 180

/** Native splash fades over the already drawn destination. Never delays app initialization. */
fun animateStartupExit(view: View, motion: Boolean, remove: () -> Unit): Animator? {
    if (!motion || !ValueAnimator.areAnimatorsEnabled()) {
        remove()
        return null
    }
    var removed = false
    fun finish() {
        if (!removed) {
            removed = true
            remove()
        }
    }
    return AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f),
            ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.025f),
            ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.025f),
        )
        duration = STARTUP_REVEAL_MILLIS.toLong()
        interpolator = DecelerateInterpolator()
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()

                override fun onAnimationCancel(animation: Animator) = finish()
            }
        )
        start()
    }
}
