# Nolee Canvas Starter

An interactive Nolee DevKit Ultra app showing how one focused experience can turn the device's
physical controls and live sensor streams into product behavior. It intentionally uses ordinary
Android APIs plus the public Nolee Launcher contract—never root and never a mandatory client SDK.

## What the hardware does

- Camera-lid scroll moves between the four animated scenes through Launcher's public callback.
- Camera-lid tap (`F2`) pulses the persona; double touch (`F5`) opens the sensor readings; long
  touch (`F6`) returns to the personal-AI listening scene.
- Camera facing transitions (`F3`, `F4`, `F7`, `F8`) rotate or reset the living form. Cross-axis
  surface swipes are distinguished from facing transitions by input-device name or scan code.
- Side-button short shifts the composition, double moves to the next scene, triple opens live
  sensors, and long reveals owner controls plus the tested rounded-display safe zone.
- Heart rate controls breathing speed, accelerometer tilt rolls the form across the glass the way a
  ball would, and steps pulse it and advance the scene. SpO2 and blood-pressure estimates appear in
  the matching sensor HUD.

Touch dragging and long-pressing the display provide desktop/emulator fallbacks for the lid scroll
and owner controls.

## Build and install

Requires Android SDK 35 and JDK 17 or newer.

```powershell
.\build.bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell pm grant ai.nolee.canvas android.permission.BODY_SENSORS
```

Select `Nolee Canvas` as the primary app in Nolee Launcher before starting kiosk. The app declares
`CATEGORY_HOME`, so the power button retains the platform's expected kiosk sleep/home behavior.

The source is deliberately compact: `MainActivity.kt` is the physical-control map,
`LidScrollBridge.kt` is the copyable no-root scroll registration, and `NoleeSensors.kt` shows the
correct sensor names, indices, and Android units.

## Laying out on the lens

The form has no solid body: it is a bed of translucent colour puffs with short tufts packed through
the middle, so filaments stay visible on both sides of it and it reads as suspended gas rather than
a shaded ball. Nothing draws a hard edge or a specular rim, and there is no full-screen ring.

`DisplaySafeZone.kt` carries the same 408 x 502 calibration the Launcher ships (2 px straight-edge
inset, 112 px corner radius, 4 px content margin). Backgrounds and the living form still run to the
edge; `safeContentInsetAt` is for text and controls that must stay readable.

Shipped watches run at `font_scale 1.5`, so any dense panel sized in `dp` will overflow if its text
scales freely. The sensor readings pin their own type through `instrumentSp`, which caps the
accessibility scale at 1.0 for that column only - scene words, captions and owner controls still
scale for the owner. Pin `lineHeight` alongside `fontSize` when doing this: `Text` otherwise keeps
the theme's 24 sp line box, which is what inflated the readings to 335 dp and pushed them off the
top of the lens.

`tools/placement_jig.html` positions the readings against this geometry and emits the constants to
paste back into `CanvasScreen.kt`. Its height model mirrors the composable, so keep the two in step
if the metric type sizes change.
