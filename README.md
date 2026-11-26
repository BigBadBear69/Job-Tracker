# Job Clock

A simple Android app for tracking work time: clock in, take breaks, clock out.

## Features
- **Clock In / Start Break / End Break / Clock Out** with one big button
- **Live timer** showing time worked (breaks excluded), total time, and break time
- **Optional break notes**: quick picks (Lunch, Coffee, Rest, Personal) or type your own
- **Ongoing notification** with a running timer while you're clocked in (restored after reboot)
- **History** grouped by day, with Today / This week / Last week totals
- **Edit shifts**: fix clock-in/out times, add, edit or delete breaks, delete shifts
- **Export to CSV** via the share button on the History tab
- All data is stored locally on your phone

## Install
1. Open the repository's **Releases** page on your phone and download the latest `JobClock-x.y.z.apk`.
2. Open the file. If Android asks, allow **Install unknown apps** for your browser or Files app.
3. Updates install over the old version and keep your data.

## Build
Requires JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Releases
`.github/workflows/release.yml` tests and builds the APK on every push and publishes it as a
GitHub release tagged `v<versionName>`. Bump `versionCode` and `versionName` in
`app/build.gradle.kts` to publish a new release.

> The signing keystore (`app/release.keystore`) is committed so every build uses the same key
> and updates install cleanly. That's fine for a personal app. If you ever publish to the Play
> Store, create a private key and keep it out of the repository.
