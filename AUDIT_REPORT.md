# Orbit Home — Grid Pager and Floating Wheel Edge Handle Audit

**Scope:** the supplied source snapshot, focusing on Grid mode edge-page creation, drag/pager settling, and floating shortcut-wheel edge-handle visibility.

## Findings and fixes

### 1. Grid mode did not create a page at the final edge

`GridOrder.cells()` deliberately trims trailing empty cells. `GridHome` derived its page count only from that compact list, so the last page had no next destination for the edge-dwell gesture to animate into. The old edge logic also only requested a page turn if a next page already existed.

**Fix:** `GridOrder.pageCount()` centralizes page counting and guarantees at least one page. During a carried app's edge dwell, `GridHome` temporarily exposes a new empty page. If the app is dropped there, `GridOrder.place()` grows the saved cell layout and that page becomes permanent; if the drag is cancelled or dropped elsewhere, the temporary page is removed.

### 2. Intermittent half-page / split-page stall

Two code paths could interrupt the pager at unsafe moments:

- Drawer-app auto-paging used an effect keyed by `pager.currentPage`. That value changes around the midpoint of an animation, so recomposition could cancel the coroutine executing `animateScrollToPage()` before it reached the endpoint.
- The in-grid drag-end path cancelled the edge-turn job on release. If that job had already started animating, cancellation could strand the pager between pages. In addition, the pager's own horizontal gesture recognizer could compete with auto-paging during a long-press carry.

**Fix:** a stable coroutine now owns drawer edge-turns and observes the latest drag state without being restarted by `currentPage`. In-grid turns re-check the live drag position after the dwell, and user scrolling is disabled during a carried item / committed page turn. A committed turn is allowed to settle before drop/cancel cleanup resolves the destination. A drop uses the current page and the final finger coordinates rather than a cell index captured before the turn. Temporary pages are cleaned up on cancellation, and a timeout fails back to stored pages rather than leaving a phantom page.

### 3. Floating wheel edge handle appeared to vanish and return

The handle's alpha was calculated as `1 - progress * 3`, clamped to 0–1. Therefore, once the wheel was roughly one-third open, the handle reached alpha 0; it then became visible again as the wheel closed. This matched the reported visual symptom directly.

**Fix:** the handle no longer has progress-driven opacity. It is rendered at full opacity throughout open/close and remains in the same handle window / physical position. Its touchability still follows the existing open/closed policy so the visible tab does not steal touches from the open wheel.

## Changed files

- `app/src/main/java/com/orbit/home/ui/GridHome.kt` — temporary-page paging, stable external edge-turn owner, animation-settle handling, live drop target calculation, and safer drag cleanup.
- `app/src/main/java/com/orbit/home/ui/GridOrder.kt` — reusable, minimum-one-page page-count helper.
- `app/src/test/java/com/orbit/home/GridOrderTest.kt` — page-count and persistence-through-new-page unit cases.
- `app/src/main/java/com/orbit/home/overlay/ShortcutWheelService.kt` — permanently opaque edge handle.
- `TESTING.md` — added focused manual regression matrices for grid paging and edge-handle visibility.

## Additional observations / limits

- Android can hide or suppress `TYPE_APPLICATION_OVERLAY` windows on OS-controlled, secure, permission-sensitive, or otherwise restricted surfaces. An app cannot guarantee its handle is visible over every system-owned screen. With overlay permission granted and the service alive, the app-level handle is now not faded by its own animation.
- `WindowManager.updateViewLayout()` failures were previously logged after the in-memory `LayoutParams` had already been updated. That could make a later refresh incorrectly conclude the failed change had been applied. The patch now rolls those params back on an update failure, allowing a subsequent refresh to retry. It does not blindly reattach a detached window: Android can reject window attachment for legitimate reasons, and automatic reattachment without device-level evidence could create duplicate or improperly touchable overlays. If the handle still disappears after this patch, capture `Diagnostics` logs and device/Android version to determine whether a window was detached, the overlay permission was changed, or the OS obscured it.
- Grid page-count and page-turn fixes are static-source changes. Device testing is still required for long-press timing, Android/Compose gesture arbitration, release/cancel during an animation, and screen-density/rotation cases.

## Validation

- `GridOrder.kt` compiled successfully in isolation with the installed `kotlinc` compiler.
- The complete `testDebugUnitTest` Gradle task could not start because the Gradle wrapper distribution is not cached and the environment could not resolve `services.gradle.org` (`UnknownHostException`). This is an environment/network blocker, **not a passing Gradle test run**.
- Manual test cases for the remaining device-only scenarios have been added to `TESTING.md`.
