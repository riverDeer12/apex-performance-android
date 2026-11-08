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

### From GitHub Actions

The **Distribute to Firebase** workflow (`.github/workflows/distribute.yml`, started by hand
from the Actions tab) builds the signed release APK and uploads it the same way. It needs
these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -i upload-keystore.jks` |
| `KEYSTORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `KEY_ALIAS` | `keyAlias` from `keystore.properties` |
| `KEY_PASSWORD` | `keyPassword` from `keystore.properties` |
| `FIREBASE_SERVICE_ACCOUNT` | JSON key of a service account with the *Firebase App Distribution Admin* role |

Bump `versionCode` and update `release-notes.txt` on `production` before running it.

Always sign with the same upload key: an APK signed with a different key can't update the
installed app, and users would have to uninstall it first.

## Publishing on Google Play

Releases go to a Google Play testing track with the **Publish to Google Play** workflow
(Actions → Publish to Google Play → Run workflow): it builds the signed release bundle (AAB)
and uploads it with the release notes from `release-notes.txt`. Testers of the track get every
update from the Play Store.

One-time setup:

1. [Google Play Console](https://play.google.com/console) developer account (one-time fee).
2. Create the app (`software.rdd.apexperformance`), keep **Play App Signing** on.
3. Run the workflow with **upload** off, download the `app-release-aab` artifact (kept for a
   day) and upload it by hand to **Testing → Internal testing** – Google Play takes uploads
   through the API only after the first one.
4. **Internal testing → Testers**: create an email list with the testers and share the opt-in
   link with them.
5. Google Cloud: service account `github-play-upload` and a Workload Identity Federation
   pool that lets only this repository's workflows act as it (no key is stored anywhere; the
   organization policy blocks service account keys anyway). In Cloud Shell:

   ```bash
   PROJECT_ID=apex-performance-514123
   SA=github-play-upload@$PROJECT_ID.iam.gserviceaccount.com
   PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')
   gcloud services enable androidpublisher.googleapis.com iamcredentials.googleapis.com sts.googleapis.com --project=$PROJECT_ID
   gcloud iam workload-identity-pools create github --project=$PROJECT_ID --location=global --display-name=GitHub
   gcloud iam workload-identity-pools providers create-oidc apex-android --project=$PROJECT_ID \
     --location=global --workload-identity-pool=github --display-name="apex-performance-android" \
     --issuer-uri=https://token.actions.githubusercontent.com \
     --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
     --attribute-condition="assertion.repository=='riverDeer12/apex-performance-android'"
   gcloud iam service-accounts add-iam-policy-binding $SA --project=$PROJECT_ID \
     --role=roles/iam.workloadIdentityUser \
     --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/riverDeer12/apex-performance-android"
   echo "projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/apex-android"
   ```

6. Play Console → **Users and permissions** → invite the service account's email with the
   app's *Release to testing tracks* permission.
7. Repository **variables** (Settings → Secrets and variables → Actions → Variables):
   `GCP_WORKLOAD_IDENTITY_PROVIDER` = the line the last command printed,
   `GCP_PLAY_SERVICE_ACCOUNT` = `github-play-upload@apex-performance-514123.iam.gserviceaccount.com`.
   Neither is secret.

Before production: store listing (512×512 icon, 1024×500 feature graphic, phone screenshots,
descriptions in `play-store/listing`), **App content** (privacy policy URL, **Data safety**:
name, email, phone, user ID, health and fitness, crash logs – for app functionality, not
shared), content rating and target audience. New personal developer accounts must run a
closed test with at least 12 testers for 14 days before production access.

Bump `versionCode` in `app/build.gradle.kts` for every upload: Google Play refuses a bundle
with a `versionCode` it already has.

## Structure

```
core/        API client, auth (JWT + Keystore-encrypted token), push, navigation, utilities
model/       API models (same as the iOS Models folder)
ui/          screens per feature: appointments, clients, body measurements, FMS, workouts, profile
ui/components  shared views (cards, rows, calendar, weight chart, toast, YouTube player...)
```

Each tab keeps its own navigation stack, like a `NavigationStack` per tab on iOS.
