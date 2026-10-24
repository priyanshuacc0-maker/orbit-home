package com.orbit.home.ui

import android.content.Context
import android.database.ContentObserver
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.orbit.home.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Orbit's one haptic system, for Activity UI and TYPE_APPLICATION_OVERLAY UI alike.
 *
 * How a haptic is produced, in order:
 *  1. The in-app switch (Settings > Haptic feedback). Off means silent, everywhere.
 *  2. A rate limit ([HapticThrottle]), shared by the whole process, so a fast spin cannot
 *     flood the vibrator and a tick can never swallow a press.
 *  3. `View.performHapticFeedback` on the host view. This is the platform's own touch-haptic
 *     path: the system classifies it as touch feedback, so it follows the user's system
 *     "Touch feedback" setting and intensity, and it works from the floating-wheel overlay
 *     window. The constants are the richest ones the running Android version has.
 *  4. Only if the view route is unavailable (view detached, binder failure): a direct
 *     [Vibrator] call tagged as touch feedback, and only while the system's touch-feedback
 *     setting is on (observed, not queried per tick).
 *
 * Every step is wrapped: haptics must never break the gesture that triggered them.
 */
class OrbitHaptics internal constructor(private val view: View) {

    /** A very light detent: the wheel crossing an app slot. */
    fun slotTick() = fire(HapticKind.SLOT_TICK)

    /** A light detent: a page settling into place. */
    fun pageSnap() = fire(HapticKind.PAGE_SNAP)

    /** A light acknowledgement of a plain tap that has no other feedback (e.g. wallpaper swap). */
    fun tap() = fire(HapticKind.TAP)

    /** A switch changing state. */
    fun toggle(on: Boolean) = fire(if (on) HapticKind.TOGGLE_ON else HapticKind.TOGGLE_OFF)

    /** A firm press: long-press recognised, app lifted, centre freed for repositioning. */
    fun press() = fire(HapticKind.PRESS)

    /** A completed action: drop, drawer opened, floating wheel revealed or put away. */
    fun confirm() = fire(HapticKind.CONFIRM)

    /**
     * A slider moved from [previous] to [next]. A stepped slider ticks on every step it lands
     * on; a continuous one stays quiet while it slides and gives one light tap when it reaches
     * either end, so the limit can be felt without a buzz for every pixel.
     */
    fun slider(previous: Float, next: Float, range: ClosedFloatingPointRange<Float>, steps: Int) {
        if (next == previous) return
        if (steps > 0) {
            slotTick()
        } else if (
            (next <= range.start && previous > range.start) ||
            (next >= range.endInclusive && previous < range.endInclusive)
        ) {
            tap()
        }
    }

    /**
     * For the master switch itself. Turning haptics OFF plays its last haptic while they are
     * still on; turning them ON applies the value first so the confirmation is felt at once,
     * without waiting for the stored setting to round-trip.
     */
    fun masterToggled(enabled: Boolean) {
        if (!enabled) fire(HapticKind.TOGGLE_OFF)
        HapticsCore.userEnabled = enabled
        if (enabled) fire(HapticKind.TOGGLE_ON)
    }

    private fun fire(kind: HapticKind) {
        if (!HapticsCore.userEnabled) return
        if (!HapticsCore.throttle.tryAcquire(kind, SystemClock.uptimeMillis())) return

        val performed = runCatching {
            view.isAttachedToWindow &&
                view.performHapticFeedback(
                    HapticsCore.constantFor(kind),
                    // The view's own flag is irrelevant: the in-app switch is the authority.
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
        }.getOrDefault(false)

        if (!performed) HapticsCore.fallback(view.context, kind)
    }
}

/** What a haptic means. Strong kinds are never held back by a weaker one just before them. */
internal enum class HapticKind(val minGapMs: Long, val strong: Boolean) {
    SLOT_TICK(minGapMs = 30L, strong = false),
    PAGE_SNAP(minGapMs = 60L, strong = false),
    TAP(minGapMs = 60L, strong = false),
    TOGGLE_ON(minGapMs = 80L, strong = true),
    TOGGLE_OFF(minGapMs = 80L, strong = true),
    PRESS(minGapMs = 120L, strong = true),
    CONFIRM(minGapMs = 120L, strong = true)
}

/**
 * The rate limit. Pure, so it can be tested without a device.
 *
 * Weak kinds obey a short global gap (so ticks cannot stack up) plus their own gap; strong
 * kinds only obey their own gap, so a press is never dropped because a tick fired just before.
 */
internal class HapticThrottle(private val globalGapMs: Long = 18L) {
    private var lastAny = Long.MIN_VALUE / 2
    private val lastByKind = LongArray(HapticKind.entries.size) { Long.MIN_VALUE / 2 }

    @Synchronized
    fun tryAcquire(kind: HapticKind, nowMs: Long): Boolean {
        if (!kind.strong && nowMs - lastAny < globalGapMs) return false
        if (nowMs - lastByKind[kind.ordinal] < kind.minGapMs) return false
        lastAny = nowMs
        lastByKind[kind.ordinal] = nowMs
        return true
    }
}

/** Process-wide state shared by every [OrbitHaptics]: the Activity and the overlay service. */
internal object HapticsCore {
    /**
     * The in-app switch. False until the stored value has been read, which is milliseconds
     * after launch; staying silent for that moment is better than buzzing for someone who
     * turned haptics off.
     */
    @Volatile
    var userEnabled: Boolean = false

    /** The system's Touch feedback setting, used only by the fallback path. */
    @Volatile
    private var systemEnabled: Boolean = true

    val throttle = HapticThrottle()

    private val lock = Any()
    private var started = false

    /** Starts following the stored switch and the system setting. Safe to call repeatedly. */
    fun ensureStarted(context: Context) {
        val app = context.applicationContext ?: return
        synchronized(lock) {
            if (started) return
            started = true
        }

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            AppPreferences(app).hapticsEnabled.collect { userEnabled = it }
        }

        runCatching {
            val uri = Settings.System.getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED)
            val read = {
                systemEnabled = runCatching {
                    Settings.System.getInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
                }.getOrDefault(true)
            }
            read()
            app.contentResolver.registerContentObserver(
                uri,
                false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) = read()
                }
            )
        }
    }

    /** The richest platform constant for [kind] on this Android version. */
    fun constantFor(kind: HapticKind): Int {
        val api34 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        return when (kind) {
            HapticKind.SLOT_TICK ->
                if (api34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            HapticKind.PAGE_SNAP ->
                if (api34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            HapticKind.TAP -> HapticFeedbackConstants.CONTEXT_CLICK
            HapticKind.TOGGLE_ON ->
                if (api34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CONTEXT_CLICK
            HapticKind.TOGGLE_OFF ->
                if (api34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CONTEXT_CLICK
            HapticKind.PRESS -> HapticFeedbackConstants.LONG_PRESS
            HapticKind.CONFIRM ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY
        }
    }

    /** Direct vibrator route, tagged as touch feedback. Only used when the view route fails. */
    @Suppress("DEPRECATION")
    fun fallback(context: Context, kind: HapticKind) {
        if (!systemEnabled) return
        runCatching {
            val app = context.applicationContext ?: return
            val vibrator: Vibrator = (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    app.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                ) ?: return
            if (!vibrator.hasVibrator()) return

            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(
                    when (kind) {
                        HapticKind.PRESS -> VibrationEffect.EFFECT_HEAVY_CLICK
                        HapticKind.CONFIRM, HapticKind.TOGGLE_ON, HapticKind.TOGGLE_OFF -> VibrationEffect.EFFECT_CLICK
                        else -> VibrationEffect.EFFECT_TICK
                    }
                )
            } else {
                when (kind) {
                    HapticKind.PRESS -> VibrationEffect.createOneShot(26L, 160)
                    HapticKind.CONFIRM, HapticKind.TOGGLE_ON, HapticKind.TOGGLE_OFF ->
                        VibrationEffect.createOneShot(20L, 130)
                    else -> VibrationEffect.createOneShot(10L, 70)
                }
            }
            vibrator.vibrate(effect, TOUCH_ATTRIBUTES)
        }
    }

    private val TOUCH_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
}

/** The haptics for whatever window this composable lives in (Activity or overlay). */
@Composable
fun rememberOrbitHaptics(): OrbitHaptics {
    val view = LocalView.current
    return remember(view) {
        if (!view.isInEditMode) HapticsCore.ensureStarted(view.context)
        OrbitHaptics(view)
    }
}
