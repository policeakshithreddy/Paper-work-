# Paperwork

> **Layers. Structure. Clarity.**  
> A calm, minimal, local-first document scanner and encrypted PDF vault for Android.

Paperwork is designed to make handling documents feel effortless and private. Everything runs strictly on your device—no accounts, no cloud servers, no subscriptions, and zero telemetry.

---

## Features

- **Document Scanner**: Fast, high-accuracy document scanning powered by Google ML Kit with auto-framing, perspective correction, and color enhancement.
- **On-Device Encrypted Vault**: Documents are encrypted at rest on your device using Android Keystore AES-256-GCM.
- **Automatic Reverse Printing**: Built-in spooling that reverses document page sequences ($N \to 1$) so face-up printer trays produce correctly ordered document stacks automatically.
- **Images to PDF**: Convert photos and scanned pages into clean, standard-dimension PDFs (A4, Letter, Legal) with custom margin and quality options.
- **PDF Viewer & Tools**: Fast, native PDF viewer with text selection, sharing, and non-rasterizing vector page manipulation.
- **Precision Mono Design**: Scandinavian/Swiss-inspired monochromatic interface crafted with Jetpack Compose and Material 3 Expressive styling.

---

## Tech Stack & Architecture

- **Language**: Kotlin 2.1
- **UI Toolkit**: Jetpack Compose with Material 3 (Adaptive layouts)
- **Architecture**: Clean Architecture with MVI / Unidirectional Data Flow
- **Local Persistence**: Android Room Database & DataStore Preferences
- **Security**: Android Keystore AES-256-GCM authenticated encryption
- **Document Processing**: Google ML Kit Document Scanner & Apache PDFBox-Android
- **Build System**: Gradle 9.5 with Version Catalogs (`libs.versions.toml`) and Dependency Locking

---

## Building from Source

### Prerequisites
- Android Studio Ladybug / Meerkat or newer
- JDK 17 or JDK 21
- Android SDK 36 (minSdk 28)

### Build Commands

```bash
# Clone the repository
git clone https://github.com/policeakshithreddy/Paper-work-.git
cd Paper-work-

# Run unit tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Assemble release APK
./gradlew assembleRelease
```

---

## License

This project is licensed under the Apache License 2.0.
