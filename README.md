# Gallery Swiper

A free and open-source Android app for cleaning up your photo gallery by swiping through photos month by month.

**Swipe right to keep, swipe left to delete.** Review your decisions before committing — deletions move photos to the system trash for safety.

## Features

- **Month-by-month browsing** — photos grouped by capture date
- **Swipe gestures** — drag right to keep, drag left to delete
- **Review step** — confirm or adjust your decisions before final deletion
- **Trash-safe** — uses Android's `createTrashRequest` (API 30+) to keep recoverable copies
- **Bookmarks** — save photos to revisit later
- **Statistics** — reviewed count, deleted count, space saved, streak tracking
- **No ads, no limits** — 100% free, no subscriptions, no time-gates
- **No AI, no network** — all processing happens on-device

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` (API 33+) | Load photos and videos from your gallery |
| `READ_EXTERNAL_STORAGE` (API 32 and below) | Legacy storage access for older devices |
| `WRITE_EXTERNAL_STORAGE` (API 28 and below) | Required for deletion on very old Android versions |

Gallery Swiper **never uploads, shares, or processes your photos anywhere**. Everything stays on your device.

## Build

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+
- Android SDK 34

### Steps

```bash
git clone https://github.com/jovanovskiot/gallery-swiper.git
cd gallery-swiper
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Room** — local persistence
- **Coil** — async image loading
- **Navigation Compose** — screen routing
- **Min SDK 26**, Target SDK 34

## License

[MIT](LICENSE)
