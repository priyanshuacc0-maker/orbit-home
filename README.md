# Orbit Home

A small Android launcher with one idea: your apps on a **circular wheel** you spin
with your finger, over your own wallpaper. A tap opens the app in the middle.

That is the whole launcher. There are no widgets, no home pages and no folders.
Everything that can be changed is in **Settings**, in the three-dot menu.

- Application ID / namespace: `com.orbit.home`
- Version: `1.101.0` (`versionCode` 140)
- Toolchain: Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, JDK 17, Compose BOM 2024.12.01,
  Material 3, DataStore Preferences, Media3 1.4.1
- `minSdk 26`, `targetSdk 36`, `compileSdk 36`
- **Five permissions, and no more.** `SYSTEM_ALERT_WINDOW` and
  `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_SPECIAL_USE`/`POST_NOTIFICATIONS` for the
  floating shortcut wheel, and `SET_WALLPAPER` for the wallpaper page. Still **no
  internet** and no `QUERY_ALL_PACKAGES`, and no hardcoded app names or package names.

## What it does

**The wheel.** Every installed app sits on the ring. Drag it to spin, flick it to
coast, and it settles on an icon with a tick. Tap an icon to open it; tap the
centre to open the app shown there. A tap that merely stops a spinning wheel never
launches anything — the next deliberate tap does. The wheel keeps pointing at the
same app when the list changes, so installing or removing an app does not throw the
ring back to the start.

**Moving the wheel.** Hold the middle of the wheel and it frees up: it shrinks
slightly to say so, and while your finger is down you can drag it up or down to put
it where you want. No text appears — the shrink is the whole message. Let go and the
position is saved. A hold you do not drag changes nothing. Screen readers can move it
too, through **Move wheel up** and **Move wheel down** on the centre.

**The dock.** Up to five apps above the navigation bar, seeded once from the
system's own defaults (dialer, browser, messaging, gallery, contacts). Drag the dock
upwards to open the all-apps drawer.

**The all-apps drawer.** A searchable, alphabetical list of everything installed,
with grid and list layouts. Search is ranked — an exact name beats a prefix, which
beats a word match, an initialism (`gm` for Google Maps), a substring, and finally a
package name — and accents are ignored, so "emile" finds "Émile". The keyboard's
Search key opens the best match. Drag the sheet down or press Back to close.

**The floating shortcut wheel.** One wheel on the screen edge, holding app shortcuts
rather than whole apps. Drag it up and down along the edge; it lifts above the
keyboard when one opens, and comes back down when it closes. Tap its centre to open
a shortcut, double-tap to switch between shortcuts and apps. It is switched on and
shaped from **Settings → Floating apps wheel**.

**Icons.** Every icon — the wheel, the dock, the drawer, the shortcut wheel — is
drawn as a **circle of the same size**, whatever shape the phone would otherwise
force. The icon's own artwork is read unmasked, and the artwork itself is measured and
normalised, so an app that draws its logo small does not sit beside one that fills
the frame looking half the size.

## Settings

- **Apps** — which apps are on the home wheel, in order.
- **Dock apps** — which apps are in the dock, in order.
- **Apps wheel** — how many slots the ring draws, the app icon size, the wheel's own
  size and the centre's size, against a live preview, with an explicit **Save**. One
  wheel's shape can be **matched onto all of them**.
- **Floating apps wheel** — whether the floating wheel is on, which shortcuts it
  holds, and its shape.
- **Wallpaper** — a preview, and two ways in: **Choose from photos** and **Browse
  files**. Nothing changes until you press **Apply**; **Cancel** lets the choice go.
  This changes the wallpaper for the whole phone.
- **Customise centre** — what the middle of a wheel shows, for the home screen's wheel
  or the floating one, separately. From the **Library** (the app's own mark, and a way
  back to the wheel's ordinary middle) or **Import**, which takes a picture or, for
  the home wheel, a short video. An imported picture is framed first: pinch to zoom,
  drag to move, turn it a quarter at a time, with a grid and a circle showing what will
  actually be visible. The floating wheel's centre is half off the screen, so its guide
  is a half circle.

**The hidden wallpaper.** Wallpaper settings has a third box: a second wallpaper,
shown when you **triple-tap the home screen** away from the wheel and the dock. It is
set the same way as the ordinary one, and the triple tap switches between the two and
back. The switch is remembered, so it stays where you left it until you change it. The
page also says which one is showing and has a **Switch** for it, because a gesture
only some people can make should never be the only way to a feature.

## Data and privacy

Everything the app knows is stored on the device, in DataStore and in the app's own
private files: the wheels and their shapes, the dock, the shortcut wheel's settings,
the centre choices, and any picture or video brought in for a centre or for the hidden
wallpaper. Nothing is sent anywhere — there is no network code in the app at all, and
no internet permission to use if there were.

A picture or video brought in is **copied into the app's own storage** rather than
remembered as the address it came from. A picked file is readable only while the
permission lasts and the file behind it can move or be deleted; a launcher that showed
a blank centre a week later because you had tidied your gallery would have lost your
setting. Videos are refused above 25 MB rather than copied and discovered later.

## Accessibility

Every icon carries a label and a button role, and exposes an explicit activate action,
so a screen reader can open an app even though the wheel's gesture is handled by hand.
The drawer opens from a visible button as well as the dock's swipe, so it is never
reachable only by dragging. Every gesture has a way that is not a gesture: the wheel's
position has its own actions on the centre, and the hidden wallpaper has its box and
its Switch. Reduced motion — the system animator duration scale at 0 — is honoured
through one policy in `Motion`, and the centre's video holds a still frame rather than
playing.

## Testing

`TESTING.md` is the on-device checklist, written as manual steps with the failure each
one would catch.

Unit tests cover the pure logic: wheel geometry and motion maths, the crop and framing
arithmetic, the codecs that store wheel shapes and centre choices, icon-cache
bucketing, search ranking, locale-aware label ordering, selection rules and the triple
tap counter.

> **Honest status:** every build has been verified by compilation and JVM unit tests
> only. Nothing has been run on a physical device or an emulator, and `TESTING.md`
> has not been executed. The Activities, Services and Composables are not covered by
> unit tests, so the gesture and surface layers rest on that checklist.

## Building

```
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # debug APK
```

Signing is optional: with no `keystore.properties` the debug key is used. CI
(`.github/workflows/android.yml`) runs the tests before it builds anything, so a red
test produces no APK, and writes the keystore only when every signing secret is
present.
