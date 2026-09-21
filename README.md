# Sultan Digital Clock — Android Controller (ESP8266 Edition)

This source package is prepared specifically against the supplied `Sultan_Digital_Clock_v8_ColorChangeSec.ino` ESP8266 firmware.

## Real ESP8266 controls wired into the app

- IP / AP connection (`192.168.4.1` supported)
- Connection/authentication with ESP8266 `/checkauth`
- Live status from the actual root Web UI HTML (no ESP32-only `/api/status` dependency)
- Phone time sync
- NTP sync
- Display ON/OFF
- D7 light ON/OFF
- Display schedule
- English/Bangla date settings
- 12/24-hour mode
- Colon blink
- Show date
- Hourly tone enable
- Tone active time range
- Fixed / Random / Sequential tone mode
- Tone selection and real tone test
- Alarm 1 / Alarm 2 and ringtone selection
- Manual brightness
- Auto LDR brightness
- LDR low/high calibration
- Static / Smooth Fade / Rainbow / Custom RGB / Sweep Random
- 12-color palette + custom RGB controls
- Color interval and animation speed
- 8-step color playlist with duration, speed and color-change interval
- Wi-Fi scan
- Wi-Fi client configuration
- Static IP configuration
- Web password change
- OTA `.bin` upload through the firmware's `/update` endpoint
- Hardware Web UI compatibility view for complete firmware-native controls

## Source verification

See `ESP8266_API_CONTRACT.md` for the exact route/parameter contract extracted from the supplied firmware.

Run:

```text
python tools/verify_esp8266_api.py
```

The project intentionally does not claim ESP32-only features that are absent from the supplied ESP8266 firmware.

## Build

Open the project in Android Studio and build the Debug variant. The included GitHub Actions workflow is configured for the project's Gradle toolchain.
