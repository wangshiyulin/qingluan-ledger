package com.qingluan.ledger

import android.animation.ValueAnimator

/** Keep user preference and Android's reduced-motion setting consistent across surfaces. */
fun motionAllowed(effects: UiEffects): Boolean =
    effects.motion && ValueAnimator.areAnimatorsEnabled()

object UiMotion {
    const val ShortMillis = 180
    const val FeedbackMillis = 100
}
