# Apex Performance – Android

Android version of the Apex Performance app (Kotlin + Jetpack Compose), with the same
screens, roles and API calls as `apex-performance-ios`.

- **Package name:** `software.rdd.apexperformance` (can't be changed after the first Play upload)
- **Min Android:** 8.0 (API 26), **target:** API 36
- **API:** debug builds use `https://test.apex-performance.fit/api`, release builds `https://apex-performance.fit/api`
- **Languages:** English, Croatian, Italian (generated from the iOS `Localizable.xcstrings`)

## Requirements

- JDK 17 (`brew install openjdk@17`)
- Android SDK with platform 36 – easiest through [Android Studio](https://developer.android.com/studio),
  or `brew install --cask android-commandlinetools`
- `local.properties` with `sdk.dir=...` (Android Studio creates it)

## Build and run

```bash
./gradlew assembleDebug          # debug APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
./gradlew bundleRelease          # Play Store bundle → app/build/outputs/bundle/release/
```

Debug against a locally running API (the emulator sees this Mac as `10.0.2.2`):

```bash
./gradlew installDebug -PapiUrl=http://10.0.2.2:5000/api
```

## Push notifications (Firebase)

The app uses the same Firebase project as iOS (`apex-performance-e9a76`).

1. Firebase console → Project settings → **Add app → Android**, package `software.rdd.apexperformance`.
2. Download `google-services.json` into `app/`.

Without that file the app builds and works, just without push. The backend needs no changes:
the app registers its token on `POST fcm-tokens` with `platform: "android"`, and a tapped
notification opens the tab from its `type` (`appointment_request`, `appointment_updated`,
`body_measurement`), as on iOS.

## Release signing

Create an upload key once and keep it (and its passwords) safe – it isn't in git:

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Then add `keystore.properties` in the project root:

```properties
storeFile=upload-keystore.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

`./gradlew bundleRelease` then produces a signed `app-release.aab`.

## Distribution outside Google Play (Firebase App Distribution)

For the few Android users, the signed release APK (production API) goes out through
[Firebase App Distribution](https://console.firebase.google.com/project/apex-performance-e9a76/appdistribution)
instead of the Play Store. Testers get an email invite, install the app from their phone and
are emailed again for every new version.

One-time setup:

1. Firebase console → **App Distribution** → Get started (Android app `software.rdd.apexperformance`).
2. **Testers & Groups** → create a group with the alias `android-testers` and add the users' emails.
3. Install the Firebase CLI (`brew install firebase-cli` or `npm install -g firebase-tools`) and run
   `firebase login` – the Gradle plugin uploads with those credentials.
4. `google-services.json` and `keystore.properties` must be in place (see above).

Every release:

1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts` – Android won't install an
   update with the same or a lower `versionCode`.
2. Write what changed in `release-notes.txt`.
3. Build and upload:

   ```bash
   ./gradlew assembleRelease appDistributionUploadRelease
   ```

Always sign with the same upload key: an APK signed with a different key can't update the
installed app, and users would have to uninstall it first.

## Publishing on Google Play

1. [Google Play Console](https://play.google.com/console) developer account (one-time fee).
2. Create the app, enable **Play App Signing** and upload `app-release.aab` to an internal testing track first.
3. Store listing: 512×512 icon, 1024×500 feature graphic, phone screenshots, descriptions (hr/en/it).
4. App content: privacy policy URL, **Data safety** (same data as the iOS privacy manifest: name,
   email, phone, user ID – for app functionality, not shared), content rating, target audience.
5. New personal developer accounts must run a closed test with testers before production access.
6. Bump `versionCode` in `app/build.gradle.kts` for every upload.

## Structure

```
core/        API client, auth (JWT + Keystore-encrypted token), push, navigation, utilities
model/       API models (same as the iOS Models folder)
ui/          screens per feature: appointments, clients, body measurements, FMS, workouts, profile
ui/components  shared views (cards, rows, calendar, weight chart, toast, YouTube player...)
```

Each tab keeps its own navigation stack, like a `NavigationStack` per tab on iOS.
