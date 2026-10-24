package com.orbit.home.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.view.View

/**
 * A frame that records the raw screen position of the latest touch.
 *
 * This exists for one reason: the floating wheel's window **moves under the finger**
 * while it is being pulled out or repositioned, and local coordinates are useless
 * when that happens — the window and the finger travel together, so every local
 * delta reads as zero and the gesture appears frozen.
 *
 * `dispatchTouchEvent` runs before any child sees the event, so the position is
 * always current by the time the gesture reads it.
 */
internal class RawTouchLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** The last touch's position in screen coordinates. */
    var rawX: Float = 0f
        private set
    var rawY: Float = 0f
        private set

    /** True while a finger is down, so a stale position is never acted on. */
    var isDown: Boolean = false
        private set

    /** Called for a touch outside the window, which needs FLAG_WATCH_OUTSIDE_TOUCH. */
    var onOutsideTouch: (() -> Unit)? = null

    /**
     * True when the last touch on this view was cancelled rather than released.
     *
     * Android sends ACTION_CANCEL when it takes a gesture away — which it does when a
     * window is reconfigured underneath it. Without this a cancelled drag is
     * indistinguishable from a completed one, and the wheel treats a partial pull as
     * though the user had finished it.
     */
    var lastWasCancel = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // ACTION_OUTSIDE is handled here, not in onTouchEvent: a child that consumes
        // the event would stop it ever reaching this view, and then a tap outside the
        // window would silently do nothing.
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch?.invoke()
            return true
        }
        rawX = ev.rawX
        rawY = ev.rawY
        if (ev.actionMasked == MotionEvent.ACTION_CANCEL) lastWasCancel = true
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) lastWasCancel = false
        isDown = ev.actionMasked != MotionEvent.ACTION_UP &&
            ev.actionMasked != MotionEvent.ACTION_CANCEL
        return super.dispatchTouchEvent(ev)
    }
}

/**
 * The least Compose needs to run somewhere that is not an Activity.
 *
 * A window added by a service has no Activity behind it, so the tree has no
 * lifecycle, view-model store or saved-state registry. Compose reads all three, so
 * they are provided here rather than pulling the whole launcher activity into the
 * overlay.
 */
internal class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    /** Restores state and marks the tree resumed, so composition actually runs. */
    fun create() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

/** Gives [view] the owners Compose looks for up the tree. */
internal fun View.attachOverlayOwners(owner: OverlayLifecycleOwner) {
    setViewTreeLifecycleOwner(owner)
    setViewTreeViewModelStoreOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
}
