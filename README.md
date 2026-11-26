# Job Tracker

Two separate Android apps, built from this repository and published under **Releases**:

| App | Module | Release tag |
|---|---|---|
| **Job Clock**: clock in/out and breaks | `app/` | `v1.x.x` |
| **Timeline**: private location history, similar to Google Maps Timeline | `timeline/` | `timeline-v1.x.x` |

## Job Clock
- **Clock In / Start Break / End Break / Clock Out** with one big button
- **Live timer** showing time worked (breaks excluded), total time, and break time
- **Optional break notes**: quick picks (Lunch, Coffee, Rest, Personal) or type your own
- **Ongoing notification** with a running timer while clocked in (restored after reboot)
- **History** grouped by day with Today / This week / Last week totals
- **Edit shifts**: fix times, add, edit or delete breaks, delete shifts
- **Export to CSV**

## Timeline
- **Background recording** that saves battery: fast GPS while moving, low-power mode when you stay put
- **Visits and trips** detected automatically, e.g. "Home 8:02–9:15" and "Driving · 12 km · 18 min"
- **Travel mode** (walking, running, cycling, driving, flying) from on-device activity recognition plus speed;
  bus, train, subway, tram, ferry and motorcycle can be set by hand
- **Day view**: map with your route colored by travel mode, visit pins, and a scrolling timeline;
  prev/next day and a calendar that marks days with data
- **Places**: name places (Home, Work, School, custom) and they're recognized automatically;
  search every place you've been; map of all places; visit history per place
- **Optional online lookups**: addresses from the phone's geocoder and nearby businesses from OpenStreetMap.
  Turn it off in Settings for a fully offline app.
- **Editing**: change a visit's place or times, change a trip's mode, delete visits, trips, days, date ranges, or everything
- **Insights** (month/year): distance and time by travel mode, top places, cities and countries visited,
  and multi-day **trips** away from home
- **Optional photos on the timeline**: photos you took during each visit or trip
- **Import** from Google (on-device `Timeline.json` export, older phone export, or Takeout `Records.json`)
- **Export** GPX (tracks), KML (Google Earth), or a full JSON backup you can restore
- **Auto-delete** history older than 3/18/36 months
- **Pause** from the app or the notification
- **Home-screen widget**: today's places and distance
- Everything is stored only on your phone

Map tiles: © OpenStreetMap contributors, served by OpenFreeMap (no API key needed).

## Install
Open **Releases** on your phone, download the APK for the app you want, and open it.
If Android asks, allow **Install unknown apps**. New versions install over old ones and keep your data.

## Build
Requires JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew :app:assembleRelease :timeline:assembleRelease
```

## Releases
`.github/workflows/release.yml` tests and builds both APKs on every push and publishes them as separate
GitHub releases. Bump `versionCode`/`versionName` in `app/build.gradle.kts` or `timeline/build.gradle.kts`
to publish a new version.

> The signing keystore (`app/release.keystore`) is committed so every build uses the same key and updates
> install cleanly. That's fine for personal apps. If you ever publish to the Play Store, create a private key and
> keep it out of the repository.
