# Sultan Digital Clock — ESP8266 API Contract

Target firmware: `Sultan_Digital_Clock_v8_ColorChangeSec.ino`

The Android app is wired to the exact HTTP routes and parameter names exposed by the supplied ESP8266 firmware.

## Implemented routes

- `/` — live HTML status + hardware web UI
- `/about`
- `/sync` — `y`, `mo`, `d`, `h`, `m`, `s`
- `/ntpsync`
- `/savesettings` — `f`, `sd`, `cb`, `hb`
- `/savealarm` — `i`, `h`, `m`, `e`, `t`
- `/savebright` — `b`, `a`, `lc`, `hc`
- `/savecolor` — `m`, `sc`, `ci`, `r`, `g`, `b`, `spd`
- `/savewifi` — `en`, `ssid`, `haspass`, `pass`, `staticen`, `sip`, `gip`, `snip`
- `/togglelight`
- `/savedatesettings` — `eng`, `bangla`
- `/toggledisplay`
- `/savedisplayschedule` — `auto`, `offh`, `offm`, `onh`, `onm`
- `/saveplaylist` — `en`, `cnt`, `mN`, `ciN`, `rN`, `gN`, `bN`, `dN`, `spdN`, `ccN`
- `/toggleplaylist`
- `/scanwifi`
- `/savetone` — `mode`, `idx`
- `/testtone` — `idx`
- `/savetonerange` — `en`, `sh`, `eh`
- `/settings`
- `/changepassword` — `old`, `new`, `en`
- `/checkauth`
- `/ota` / `/update` — OTA firmware upload

## Deliberately disabled in the Android API layer

The supplied ESP8266 firmware does not expose these routes, so the app does not pretend that they work:

- `/api/status`
- `/toggleprayeralarm`
- `/toggletempsensor`
- `/savedfvolume`
- `/testdftrack`
- `/savehourlymode` (ESP32-style track-pool API)
- `/savetracks`
- `/savewaqtazan`
- `/saveweeklyplaylist`
- `/changeappass`
- `/resetdefaultpass`
- `/showwifipass`

The app uses the real ESP8266 HTML status page as the live status source and keeps the hardware Web UI available as a compatibility fallback.
