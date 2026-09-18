<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/3d51afe5-8ca0-4d4e-9b1e-238975e60828

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.


## Sultan ESP32 firmware compatibility (v1.1.0)

This Android project is matched to the supplied `sultan_clock_ESP32_apk_21_08_26.ino`.
The firmware provides the Android API endpoints `/api/status` and `/saveweeklyplaylist`,
persists two weekly MP3 schedule slots, and supports per-prayer Azan enable flags (`aze0`..`aze4`).
The Android app limits weekly slots to the two slots supported by the firmware and clamps DFPlayer
track numbers to the ESP32/DFPlayer range.

### GitHub build

Push the complete project to the `main` branch. The included GitHub Actions workflow installs
Gradle 9.3.1, creates a standard debug keystore, and builds `app-debug.apk` automatically.
