# Orbit Home — on-device checklist

Nothing in this project has been run on a real device. These are the checks to run,
written as manual steps with the failure each one would catch.

## The wheel

- [ ] Spin the wheel with a slow drag and let go. It settles on an icon rather than
      stopping between two, and a light tick is felt as slots pass.
- [ ] Flick it hard. It coasts, slows, and settles. It does not spin forever.
- [ ] Flick it, then tap the screen while it is still moving. The wheel stops and
      **no app opens**. Tap again deliberately: that app opens.
- [ ] Same with a tap on an icon, and with a tap in the middle.
- [ ] Tap the centre. The app shown there opens.
- [ ] Tap between two icons on the ring. Nothing opens.
- [ ] Install an app from the Play Store while the wheel is on screen. The new app
      joins the ring and the wheel does **not** jump back to the first slot.
- [ ] Uninstall an app that is on the ring. It disappears and the wheel stays put.
- [ ] With no apps installed at all (or none launchable): the wheel shows the Orbit
      logo and a "No apps found" message, and no button that does nothing.

## The dock

- [ ] On a fresh install the dock fills with up to five apps (dialer, browser,
      messaging, gallery, contacts), skipping any the device does not have.
- [ ] Tap a dock icon: it opens. Drag the dock upwards: the drawer opens, and no
      dock app was launched by the drag.
- [ ] A device with none of those apps installed: the dock is simply absent, not an
      empty pill.

## The all-apps drawer

- [ ] **Pull-up.** Put a finger on the strip below the dock, and then on the dock itself, and
      move up a little each time. Neither dims anything now - the dim was removed. Moving up
      18dp opens the drawer, and it opens the moment the line is crossed rather than when the
      finger lifts. Let go short of it and nothing happens. Move downward: nothing.
- [ ] **Dock taps still work.** With all of that in place, tap an app on the dock: it
      opens immediately, as before, with no dim. The pull-up must not have taken the tap.

- [ ] Open it from the "All apps" button at the top, and from a dock swipe-up.
- [ ] The search field is focused when it opens.
- [ ] Type "ph": Phone appears at the top. Type "gm": Google Maps appears (initialism).
      Type "emile": Émile appears (accents ignored). Type a package name: it matches.
- [ ] Press the keyboard's Search key: the best match opens.
- [ ] Scroll down, then change the query: the list jumps back to the top.
- [ ] Type nonsense: the "No apps match" state appears.
- [ ] Drag the sheet down: it closes. Press Back: it closes.
- [ ] With the drawer open, tap where a wheel icon is underneath: the app behind is
      **not** launched.

## Reduced motion and system behaviour

- [ ] Developer options → Animator duration scale → Off. The drawer still opens and
      closes (instantly), and the wheel still stops when you release it.
- [ ] Turn it back on: both animate again.
- [ ] Change the phone language. App labels in the wheel and drawer update without
      restarting the app, and the drawer's ordering follows the new locale.
- [ ] Press Home on the home screen, and from another app: Orbit Home comes forward.
- [ ] Back on the home screen does nothing (there is nothing to leave).
- [ ] Rotate the phone: the home screen stays portrait.

## Setup

- [ ] Open Orbit Home from the app drawer. The Setup screen appears with the app
      count and a "Set as my home screen" button.
- [ ] Set it as home: Android's own confirmation appears, and after choosing it the
      screen says Orbit Home is your home screen.
- [ ] Switch back: Android's home-app settings open.
- [ ] TalkBack: reach the "All apps" button and a wheel icon. Each announces its
      label and role, and double-tap opens the app.
- [ ] Update an installed app whose icon changes but whose name and launch activity do not;
      visible wheel/drawer icons refresh without leaving and reopening the launcher.

## The wheel's controls (Settings -> Apps wheel)

The centre-hold panel is gone. The centre of the wheel is a plain tap now: it opens
the app shown there, and nothing else. Every control lives in Settings, in the
three-dot menu.

- [ ] Open the three-dot menu. Settings is the first item.
- [ ] Settings -> Apps wheel. The wheel's controls appear with a live preview beside
      them, and a selector for each wheel you have.
- [ ] Change a control and press **Save**: the value is applied to the home wheel.
- [ ] Change a control and press **Back**: the change is discarded, and the wheel is
      exactly as it was.
- [ ] With more than one wheel: every wheel appears in the selector, and selecting one
      shows that wheel's own values.
- [ ] **Match the settings for all wheels**: the first time it asks first; after that
      it applies straight away. Each wheel keeps its own apps.
- [ ] **Apps in wheel**: drag to 4 and to 12. The ring redraws with that many slots,
      never more than the apps available.
- [ ] **App size**: drag to both ends. Icons grow and shrink and do not overlap at
      the maximum or collapse at the minimum.
- [ ] **Wheel size**: drag to both ends. The ring grows and shrinks and stays inside
      the screen at both extremes.
- [ ] **Centre size**: drag to both ends. The app icon in the middle grows and
      shrinks with it.
- [ ] **Move**: the up arrow raises the wheel, the down arrow lowers it. At each
      limit the arrow greys out, and the ring never leaves the screen.
- [ ] Close the panel, then reopen it: every value is exactly as you left it.
- [ ] Kill the app and reopen: the values are still there.
- [ ] Rotate the phone and come back: the values are unchanged.
- [ ] **Edit wheel apps**: tap it. The picker lists every app, alphabetical, with the
      apps already on the wheel showing their ring position. Type to search.
- [ ] Tap an app to add it: it appears in the ring immediately behind the picker.
- [ ] Tap it again to remove it: the ring loses it.
- [ ] Tap Save, close the panel, reopen: the wheel shows exactly the apps you chose,
      in the order you tapped them.
- [ ] Tap Cancel in the picker: nothing changes.
- [ ] TalkBack: focus a slider in the panel. It announces its name and value and can
      be adjusted with swipe up/down. Reach the up and down arrows.
- [ ] Reduced motion off: the wheel still moves up and down (instantly).

## The floating shortcut wheel (edge handle)

Record the screen and step through the recording frame by frame; the defects here are
a few frames long and cannot be judged by eye in real time.

- [ ] **Close.** Open the wheel, then close it (tap outside, or launch an app). The
      purple handle appears at its resting place and **stays there**. It must never
      appear somewhere else and slide in. *(Catches a window frame change at the end of
      the close.)*
- [ ] **Open by tap on the handle.** The wheel slides out from the edge in one motion,
      with no blank gap between the handle disappearing and the wheel appearing, and no
      step backwards at the end. *(Catches a window frame change at the start or end
      of the open, and a second window update landing mid-animation.)*
- [ ] **Open by pulling the handle left**, releasing at about a quarter, a half and
      three quarters of the way. Each completes smoothly from where the finger left it.
- [ ] **Interrupt.** Tap the handle while the wheel is closing, and tap outside while it
      is opening. It reverses from where it is, with no jump to either end.
- [ ] **Collapsed pass-through, Android 12 or newer.** With the wheel docked, tap and
      scroll an app in the strip where the wheel opens (about 105dp wide, 210dp tall at
      the edge). Everything works exactly as without Orbit Home running.
- [ ] **Reposition.** Drag the handle up and down. It follows the finger; after release,
      open the wheel at once: it opens at the new height with no sideways or vertical
      slide.
- [ ] **Pull it out smoothly.** Drag the handle sideways slowly, and again in short jerky
      movements, and again in one quick flick. In all three the wheel comes out smoothly
      and stays under the finger; it does not jump at the start of the pull and does not
      stutter when the finger does. A quick flick may trail slightly behind the finger and
      then finish as it settles — that is the smoothing, not a missed drag.
- [ ] **Preview.** Hold the middle of the home wheel to open the panel while the floating
      wheel is docked, change a slider, and close the panel. The floating wheel opens,
      updates in place, and closes back to the handle, with no jump at either end.

## The floating wheel on every edge

Do these with three-button navigation, then repeat the bottom-edge checks with gesture navigation.

- [ ] **Slide along the edge.** Drag the handle up and down the right edge. It follows the finger from
      the first movement and reaches both ends; the wheel never goes under the status bar or onto the
      navigation buttons.
- [ ] **Hop to the top.** Keep dragging past the top end of the right edge until the finger is near the top
      of the screen (just under the status bar). The handle hops onto the top edge with a light tap and
      follows the finger left and right along it.
- [ ] **Hop to the left and bottom.** From the top edge drag to the left edge, then to the bottom edge. On
      the bottom edge the handle sits just above the navigation buttons and never covers them.
- [ ] **Right in the middle.** Drag with the finger in the middle of the screen: the handle stays on its edge
      rather than flicking between edges.
- [ ] **End of travel does not hop.** On each edge slide the handle all the way to each end with the finger
      kept near that edge. It stops at the end and stays on the edge.
- [ ] **Open on every edge.** On the left, top, right and bottom edges: tap the handle, and pull it away from
      the edge. The wheel comes out of that edge, the icons rest in the middle of the visible half, it spins,
      launches an app, and a tap outside closes it.
- [ ] **Remembered.** Leave the handle on the top edge, force-stop and reopen Orbit Home, and restart the
      phone. It comes back on the same edge at the same place.
- [ ] **Upgrade.** Install over a build from before this change: the handle is still on the right edge where
      it was.
- [ ] **Keyboard.** With the keyboard up, the bottom-edge handle rests above it; with it down it drops back
      to the navigation bar. Side-edge handles lift out of the keyboard's way as before.
- [ ] **Rotate.** Rotate the phone with the handle on each edge: it stays on that edge, inside the usable area.
- [ ] **Cancelled drag.** Start a drag and pull the notification shade down mid-drag. Nothing is left half-way
      out, and the handle is still usable afterwards.

## The floating wheel's settings panel

## The floating wheel's own panel (not currently reachable)

This panel is drawn when `editingShortcutWheel` is set, and nothing sets it today —
`setEditingShortcutWheel(true)` has no callers. The steps below describe it as it
behaves if it is wired up again; they cannot be run as the app stands. Kept because
the panel is still in the code and the audit (F10) flags two traps in reviving it.

- [ ] Open the panel (Edit wheel). The floating wheel comes out and **stays out**
      while you touch any slider, the up/down arrows, the app-size or wheel-size
      controls, or the empty space inside the panel.
- [ ] Tap anywhere outside the panel: it closes, the values are kept, and the wheel
      goes back to the handle (or stays out if it was out before).
- [ ] Press Back with the panel open: same result.
- [ ] Press Home with the panel open: same result, and the wheel is not left out.
- [ ] Open the recents screen, or launch another app, with the panel open: the panel
      is gone when you return and the wheel is docked.
- [ ] Open the panel with the floating wheel switched **off**: it appears for the
      preview and disappears on close, as before.
- [ ] Move slider: drag the thumb up and the coloured part ends at the thumb and
      moves up with it; drag down and it moves down. Same in the standard wheel panel.

## Grid-mode paging regression checks

- [ ] In Grid mode, long-press an app on the final page and hold it at the right edge. After the edge dwell, a new empty page appears and the app can be dropped anywhere in its grid.
- [ ] Repeat when the current page is full, when it contains gaps, and when the only page has just one app. No app is lost, duplicated, or unexpectedly rearranged.
- [ ] Drag an app from page 1 to page 2 and release during the page animation. The animation finishes, the drop lands on the page actually shown, and the workspace never remains split between two pages.
- [ ] During a page turn, move the finger away from the edge, lift, and cancel the gesture in separate runs. Each run finishes or safely returns to a settled page; there is no half-page offset.
- [ ] Drag an app from the drawer onto the last-page edge so a temporary page is created, then drop during the transition. Repeat by cancelling the drawer drag. A successful drop persists the new page; cancellation removes temporary pages and returns to a stored page.
- [ ] After every drag test, swipe between pages normally in both directions, switch to another screen and back, and relaunch the launcher. Pager snapping and saved placement still work.

## Floating wheel edge-handle visibility regression checks

- [ ] Keep the wheel docked and open/close it by tap, pull, outside tap, and app launch. The edge handle stays at full opacity and the same physical position; it does not fade away, blink, or slide back in.
- [ ] Repeat while another app, the app drawer, Settings, and the keyboard are visible. The handle remains visible and the overlay does not cover touch interaction with the underlying app while docked.
- [ ] Reposition the handle vertically, open the wheel, close it, and reopen it. Both windows remain aligned and the handle never jumps at animation start/end.
- [ ] Rotate/configure the display where supported, turn the screen off/on, and revoke/restore overlay permission. With permission and the service enabled, the handle returns; after permission revocation it is removed rather than silently lingering.
- [ ] Distinguish app-level behavior from system-owned surfaces: Android may hide application overlays on permission/security-sensitive or OS-controlled screens. Do not treat those surfaces as a guarantee the app can override.

## Regression checks for the reliability patch

- [ ] App update with an icon-only change: the package broadcast invalidates that package's cached
      bitmaps and visible icons reload. Rapid repeated package changes do not strand icon preloads.
- [ ] Revoke Draw over other apps while the floating wheel is enabled, then return to Orbit Home:
      the switch turns off and the foreground service/notification/windows are removed. Grant the
      permission and enable it again: both windows return. Also check a normal settings preview while
      the feature is off; the preview still closes correctly.
- [ ] Force the wheel window to fail to attach, then force the handle window to fail to attach:
      both cases leave no half-overlay behind and the service can start cleanly after the failure is gone.
- [ ] Replace a centre image and the hidden wallpaper. If a write/compression failure is induced,
      save reports failure and the previous image remains readable.
- [ ] Back up/restore preferences along with `centres/` and `wallpapers/`; custom centre images,
      centre videos (including their poster fallback), and the hidden wallpaper remain present.
      Confirm the total backup size stays within the target device's backup quota.
- [ ] TalkBack can focus the clock pill and activate “Change clock background colour”; the chooser opens,
      a colour can be selected, and the current time/date are announced.


## Haptic feedback regression

Run this on a physical device with a working vibration motor (emulator results are not authoritative).
Haptics go through `View.performHapticFeedback` (the platform's touch-feedback path), gated by Orbit's own
switch at Settings > Haptic feedback, and by the system's Touch feedback setting.

1. System Touch feedback ON, Orbit's Haptic feedback ON. Open the floating wheel and rotate it slowly across
   several slots; feel one light tick per slot. Spin it fast: ticks stay crisp (capped at roughly 30 per second),
   never a continuous buzz.
2. Repeat step 1 with the floating wheel in *shortcut* mode (it used to be silent) and on a home-screen wheel.
3. Edit the wheel's apps (add/remove one). The wheel must not tick when its list changes.
4. Tap the edge handle while the wheel is closed, then pull it closed/open; one firm confirmation per state change,
   not a vibration on every move sample. Slide the handle up/down: one light tap when the slide starts.
5. Long-press the wheel centre until reposition mode activates: one firm press at the threshold.
6. Grid mode and the all-apps drawer: long-press an app (firm press). Drop it on the grid (confirmation).
   Swipe between grid pages and between wheel pages: one light detent per page turn, none on first open.
7. Pull the drawer up from the dock: one confirmation when it opens.
8. Triple-tap bare wallpaper: confirmation as the wallpaper swaps.
9. Settings > Haptic feedback: toggling OFF plays one last haptic then everything is silent (repeat steps 1 to 8
   and confirm nothing vibrates, including in the floating wheel). Toggling ON plays one haptic immediately.
   Kill and reopen the app: the choice is remembered, and the floating wheel obeys it too.
10. Other switches (floating wheel enable, wheel-customiser shortcut switch) give a toggle haptic. Stepped sliders
    (app count) tick per step; continuous sliders give one tap at each end.
11. Disable the system Touch feedback setting with Orbit's switch ON: Orbit must stay silent.
12. Test with the phone's vibration intensity at low and normal. A missing or failing motor/service must never
    block or cancel a gesture.

## Grid folders

- [ ] Hold an app and drag it over the **middle** of another app and wait. After a moment the
      cell lights up more strongly and a light tick is felt. Let go: a folder appears in that cell
      holding both apps, and the dragged app's old cell is empty.
- [ ] Do the same but drop on the **edge** of the other app's cell. No folder: the apps reorder
      as before.
- [ ] Sweep an app across several others without stopping. No folder is offered along the way.
- [ ] Tap the folder. It opens as a panel with its apps. Tap an app: it opens, and the panel closes.
- [ ] Tap the name at the top of an open folder, type a new one, press Done, close the folder.
      The new name is under the folder's tile. Clear the name: the tile says "Folder" again.
- [ ] Drag an app onto an existing folder (middle of the cell, wait): it joins the folder.
- [ ] Open a folder, hold one of its apps and drag it out onto an empty cell. The panel hides while
      you carry it; on drop the app is on the grid and gone from the folder.
- [ ] Take apps out of a two-app folder until one is left: the folder turns back into that app, in
      the same cell.
- [ ] Open a folder, drag an app out and drop it back on the same folder. Nothing changes.
- [ ] Drag a folder to another cell, and to another page: it moves like an app does. Dropping a
      folder on an app does not make a folder inside a folder.
- [ ] Drag a folder to Remove: the folder goes, and all its apps are back in the drawer only.
- [ ] Drag an app from the **drawer** onto the middle of a home app: a folder is made with both.
- [ ] Kill and reopen the app: folders, their names and their apps are as you left them.
- [ ] Uninstall an app that is in a folder: it disappears from the folder. Uninstall every app in a folder:
      the folder's cell goes empty and nothing else moves.
- [ ] With the system animation scale at 0, everything above still works, without the slide.
