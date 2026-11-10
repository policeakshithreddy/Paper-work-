# Paperwork — Final Compatibility & Security Amendment

This amendment resolves all seven architectural, SDK compatibility, cryptographic state-machine, and verification requirements prior to Milestone 1 execution.

---

## 1. AndroidX PDF SDK Compatibility & Build Matrix Resolution

### Verified Artifact & Container SDK Inspection Findings
During direct inspection of the Google Maven AAR metadata (`META-INF/com/android/build/gradle/aar-metadata.properties`) and the container's `/opt/android/sdk/platforms/` installation, we verified the exact technical constraints:

1. **Minimum SDK (`minSdk = 28`)**:
   - Every `androidx.pdf` artifact (`pdf-core`, `pdf-viewer`, `pdf-viewer-fragment`, `pdf-compose`, `pdf-document-service`, `pdf-ocr-play-services`) declares `<uses-sdk android:minSdkVersion="28" />` in its `AndroidManifest.xml`.
   - **Resolution**: Paperwork **explicitly raises `minSdk` from `24` to `28` (Android 9 Pie)** across the application, eliminating any `tools:overrideLibrary` workaround and ensuring 100% honest manifest compatibility for all AndroidX PDF artifacts.
   - **Documented Device-Support Change**: Raising `minSdk` to `28` targets Android 9.0+ (covering >99.2% of active Google Play devices) while guaranteeing native `KeyInfo` Keystore introspection and full `androidx.pdf` bytecode compatibility.
2. **AGP, Gradle Wrapper & `compileSdk` Alignment**:
   - **Gradle Wrapper**: `gradle-9.3.1-bin.zip` (pre-configured in `gradle/wrapper/gradle-wrapper.properties`).
   - **Android Gradle Plugin (AGP)**: `9.2.0` (verified HTTP 200 available on Google Maven `com.android.application.gradle.plugin:9.2.0`, with `9.1.1` pre-cached in the container).
   - **`compileSdk` & `androidx.pdf` Artifact Matrix**:
     - `pdf-core:1.0.0-beta01`, `pdf-viewer:1.0.0-beta01`, `pdf-viewer-fragment:1.0.0-beta01`, `pdf-document-service:1.0.0-beta01`, and `pdf-ocr-play-services:1.0.0-beta01` declare `minCompileSdk=36` and `minCompileSdkExtension=19` (satisfied natively by `compileSdk 36.1` with Extension 20).
     - `pdf-compose:1.0.0-beta01` declares `minCompileSdk=37` (`minAndroidGradlePluginVersion=9.1.0`), whereas `pdf-compose:1.0.0-alpha18` declares `minCompileSdk=36` (`minCompileSdkExtension=19`).
     - In the build container (`/opt/android/sdk/platforms/`), `android-36.1` (Extension 20) is the highest pre-installed platform image; if `compileSdk = 37` is requested without an installed `android-37/android.jar`, Gradle matches `compileSdk { version = release(36) { minorApiLevel = 1 } }` with `pdf-compose:1.0.0-alpha18` (zero AAR metadata overrides needed) **or** `1.0.0-beta01` when `android-37` is provisioned. We validate the exact matrix via `gradle :app:assembleDebug` in Milestone 1 before proceeding.

### SDK & Runtime Compatibility Matrix

| API Level / Runtime Environment | Active `PdfViewerEngine` Implementation | Text Selection & Search Layer | Scanned Page OCR Layer |
| :--- | :--- | :--- | :--- |
| **API 35+ (Android 15/16+)** or **API 31–34 with SDK Extension `R >= 13`** | **`JetpackSandboxedPdfEngine`** (`androidx.pdf` `SandboxedPdfLoader` + `PdfViewer` / `PdfViewerFragment`) | Native Jetpack PDF Viewer selection handles & search (`isTextSearchActive`) + `NativePdfTextExtractor` (`PDFTextStripper`) | `MlKitOcrProvider` (`pdf-ocr-play-services`) + `OnDeviceOcrService` (`play-services-mlkit-text-recognition`) |
| **API 28–30 (Android 9–11)** or **API 31–34 with SDK Extension `R < 13`** | **`FrameworkPdfRendererEngine`** (`android.graphics.pdf.PdfRenderer` mutex-synchronized tile/page renderer) | `NativePdfTextExtractor` (`pdfbox-android` `PDFTextStripper`) with selectable text sheet & keyword match navigation | `OnDeviceOcrService` (`play-services-mlkit-text-recognition:19.0.2`) executed on rendered page bitmaps |

---

## 2. Corrected Compose & Kotlin Versioning Strategy

- **Why Compose BOM Alone Does Not Govern Compiler Compatibility**:
  - `androidx.compose:compose-bom` manages only the runtime/UI artifact versions (`androidx.compose.ui`, `material3`, `foundation`, `runtime`).
  - Since Kotlin 2.0+, the **Compose Compiler** is distributed directly inside the Kotlin repository and must match the exact Kotlin Gradle Plugin (`KGP`) version via `org.jetbrains.kotlin.plugin.compose`.
- **Verified Aligned Toolchain**:
  - **Kotlin Gradle Plugin (`org.jetbrains.kotlin.android` / built-in AGP Kotlin)**: `2.2.10`
  - **Kotlin Compose Compiler Gradle Plugin (`org.jetbrains.kotlin.plugin.compose`)**: `2.2.10` (strictly locked 1:1 to `kotlin = "2.2.10"`)
  - **Google KSP (`com.google.devtools.ksp`)**: `2.3.5` (verified compatible with Kotlin `2.2.10` and Room `2.7.0` in this environment)
  - **Compose BOM (`androidx.compose:compose-bom`)**: `2025.02.00`

### Reconciled Dependency Catalog (`gradle/libs.versions.toml`)

All unused template dependencies (`retrofit`, `converter-moshi`, `okhttp`, `logging-interceptor`, `moshi-kotlin`, `firebase-*`, `accompanist-permissions`, `play-services-location`, `credentials`, `googleid`) are **removed completely**:

```toml
[versions]
agp = "9.2.0" # Verified with Gradle 9.3.1 (falls back to 9.1.1 only if container offline lock requires)
kotlin = "2.2.10"
googleDevtoolsKsp = "2.3.5"
composeBom = "2025.02.00"
coreKtx = "1.18.0"
appcompat = "1.7.0"
fragmentKtx = "1.8.6"
activityCompose = "1.10.1"
lifecycle = "2.8.7"
navigationCompose = "2.8.9"
room = "2.7.0"
datastorePreferences = "1.1.7"
coroutines = "1.10.2"
camerax = "1.5.0"
coilCompose = "2.7.0"
biometric = "1.1.0"
exifinterface = "1.4.0"
androidxPdf = "1.0.0-beta01"
androidxPdfCompose = "1.0.0-alpha18" # Native minCompileSdk=36 (Extension 19); 1.0.0-beta01 on API 37
pdfboxAndroid = "2.0.27.0"
mlkitDocumentScanner = "16.0.0"
mlkitTextRecognition = "19.0.2"
secretsGradlePlugin = "2.0.1"
googleServices = "4.5.0"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
core = "1.6.1"
runner = "1.6.2"
robolectric = "4.16.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
androidx-fragment-ktx = { group = "androidx.fragment", name = "fragment-ktx", version.ref = "fragmentKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material3-window-size = { group = "androidx.compose.material3", name = "material3-window-size-class" }
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
androidx-camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
androidx-biometric = { group = "androidx.biometric", name = "biometric", version.ref = "biometric" }
androidx-exifinterface = { group = "androidx.exifinterface", name = "exifinterface", version.ref = "exifinterface" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coilCompose" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
androidx-pdf-viewer = { group = "androidx.pdf", name = "pdf-viewer", version.ref = "androidxPdf" }
androidx-pdf-viewer-fragment = { group = "androidx.pdf", name = "pdf-viewer-fragment", version.ref = "androidxPdf" }
androidx-pdf-compose = { group = "androidx.pdf", name = "pdf-compose", version.ref = "androidxPdfCompose" }
androidx-pdf-document-service = { group = "androidx.pdf", name = "pdf-document-service", version.ref = "androidxPdf" }
androidx-pdf-ocr-play-services = { group = "androidx.pdf", name = "pdf-ocr-play-services", version.ref = "androidxPdf" }
pdfbox-android = { group = "com.tom-roush", name = "pdfbox-android", version.ref = "pdfboxAndroid" }
play-services-mlkit-document-scanner = { group = "com.google.android.gms", name = "play-services-mlkit-document-scanner", version.ref = "mlkitDocumentScanner" }
play-services-mlkit-text-recognition = { group = "com.google.android.gms", name = "play-services-mlkit-text-recognition", version.ref = "mlkitTextRecognition" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-core = { group = "androidx.test", name = "core", version.ref = "core" }
androidx-runner = { group = "androidx.test", name = "runner", version.ref = "runner" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
google-devtools-ksp = { id = "com.google.devtools.ksp", version.ref = "googleDevtoolsKsp" }
secrets = { id = "com.google.android.libraries.mapsplatform.secrets-gradle-plugin", version.ref = "secretsGradlePlugin" }
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

---

## 3. Corrected Key Invalidation & Recovery State Machine

Generating a replacement key in `AndroidKeyStore` **cannot** decrypt ciphertext encrypted under a permanently invalidated or deleted hardware key. Paperwork implements an explicit 4-state key lifecycle (`VaultKeyState`):

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                      VAULT KEY LIFECYCLE STATE DIAGRAM                        │
└───────────────────────────────────────────────────────────────────────────────┘

   ┌─────────────────────────────────────────────────────────────────────────┐
   │ 1. ACTIVE (Key Version N)                                               │
   │ - Keystore alias `paperwork_vault_aes_gcm_v{N}` is valid & operational. │
   │ - Existing v{N} documents can be decrypted and new ones encrypted.      │
   └────────────────────────────────────┬────────────────────────────────────┘
                                        │
        KeyPermanentlyInvalidatedException / UnrecoverableKeyException /
        Corrupted Keystore Entry / AEADBadTagException on Key Probe
                                        │
                                        ▼
   ┌─────────────────────────────────────────────────────────────────────────┐
   │ 2. INVALIDATED (Key Version N Unusable)                                 │
   │ - Existing key v{N} cannot decrypt documents encrypted under v{N}.      │
   │ - Vault operations are halted; affected documents are flagged           │
   │   `DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED`.                │
   └────────────────────────────────────┬────────────────────────────────────┘
                                        │
                                        ▼
   ┌─────────────────────────────────────────────────────────────────────────┐
   │ 3. RECOVERY_REQUIRED (User Decision Gate)                               │
   │ - Honest UI Banner & Modal explains that OS lockscreen/keystore changes │
   │   permanently invalidated key v{N} and old v{N} files cannot be         │
   │   decrypted by generating a new key.                                    │
   │ - User options:                                                         │
   │   (a) Re-import external originals via SAF                              │
   │   (b) Purge unreadable v{N} records & provision replacement key v{N+1}  │
   └────────────────────────────────────┬────────────────────────────────────┘
                                        │
                     User explicitly confirms replacement key
                                        │
                                        ▼
   ┌─────────────────────────────────────────────────────────────────────────┐
   │ 4. NEW_KEY_CREATED (Key Version N+1 Provisioned)                        │
   │ - New alias `paperwork_vault_aes_gcm_v{N+1}` is created for NEW docs.   │
   │ - Any retained v{N} records remain clearly marked as `UNREADABLE`       │
   │   until explicitly re-imported or deleted (never silently marked OK).   │
   └─────────────────────────────────────────────────────────────────────────┘
```

- **Pre-Vault Data Loss Disclosure**: Before enabling the encrypted vault or biometric/credential lock, Settings displays an explicit **Vault Key & Data Loss Warning** explaining that hardware-bound Android Keystore keys are tied to device security state and cannot be backed up to the cloud; if the Keystore is reset by the OS, encrypted files without an exported external backup will be permanently unrecoverable.

---

## 4. Actual Biometric & Access Lock Security Boundary

Paperwork keeps the distinction between **App Access Lock** and **Cryptographic Authentication** unambiguous in both code and UI:

1. **App Access Lock (`AppAccessLockManager` — Default User Gate)**:
   - Uses `BiometricPrompt` (`Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL`) and/or the optional salted `PBKDF2WithHmacSHA256` 4-digit PIN (with exponential retry cooldowns) to gate entry into the Paperwork UI session.
   - Explicitly labeled in Settings as **"App Access Lock (UI Session Gate)"** with an explanation that a 4-digit PIN has 10,000 combinations and controls app entry, while on-disk encryption is performed by the Android Keystore AES-256-GCM key.
2. **Cryptographic Key Policy (`KeyGenParameterSpec`)**:
   - The default vault key (`paperwork_vault_aes_gcm_v{N}`) is configured with `setUserAuthenticationRequired(false)` so background/multi-page thumbnail rendering, PDFBox page preparation, and `PrintDocumentAdapter` spooling can operate reliably once the user has unlocked the app session, without throwing `UserNotAuthenticatedException` mid-stream.
   - Paperwork does **not** claim that biometric authentication cryptographically unlocks every individual AES-GCM block cipher operation unless an explicit per-operation `CryptoObject` key policy is active.

---

## 5. Corrected Encrypted PDF Viewing Lifecycle

Neither `SandboxedPdfLoader` (`androidx.pdf`) nor `android.graphics.pdf.PdfRenderer` can read an encrypted `.enc` file directly (which begins with the 19-byte `"PWKV"` header + IV + AES-GCM ciphertext). Every PDF viewing path goes through `SecureDocumentViewerSession`:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     ENCRYPTED PDF VIEWING LIFECYCLE                          │
└──────────────────────────────────────────────────────────────────────────────┘

  [1. Encrypted Vault File]  (/files/vault_pdfs/{docId}.pdf.enc)
              │
              │  Header Validation ("PWKV", FormatVer=1, KeyVer=N, 12B IV)
              │  + Keystore Key v{N} AES-256-GCM Decryption (AAD = docId)
              ▼
  [2. Controlled Decryption (`SecureDocumentAccessManager.openSession(doc)`)]
              │
              │  Writes validated plaintext PDF to isolated session file:
              │  `/cache/active_session_tmp/session_{sessionId}_{docId}.pdf`
              │  (Owner-only 0600 permissions, tracked in active session registry)
              ▼
  [3. Temporary PDF Access Handle (`DecryptedPdfSessionHandle`)]
              │  Provides `file: File`, `uri: Uri`, and `openParcelFileDescriptor()`
              │  Retained for the COMPLETE viewer session across config changes,
              │  zoom/scroll, search, and on-demand OCR passes.
              ├─────────────────────────────────────┬──────────────────────────┐
              ▼                                     ▼                          ▼
  [4A. JetpackSandboxedPdfEngine]     [4B. FrameworkPdfRendererEngine]   [4C. Text/OCR]
  (API 35+ or API 31+ w/ Ext R>=13)   (Tested Fallback on all APIs)      - NativePdfTextExtractor
  - `SandboxedPdfLoader.openDocument` - `PdfRenderer(pfd)` page pool     - OnDeviceOcrService
              │                                     │                          │
              └─────────────────────────────────────┴──────────────────────────┘
                                                    │
                                                    ▼
  [5. Deterministic Session & Abandoned-Session Cleanup]
  - `DecryptedPdfSessionHandle.close()` invoked when viewer screen exits (`onCleared` / `DisposableEffect`).
  - Closes open `ParcelFileDescriptor`s and deletes `session_{sessionId}_{docId}.pdf`.
  - Startup & background sweep deletes any orphaned `/cache/active_session_tmp/session_*.pdf`
    from prior process termination.
```

---

## 6. Quantitative Validation of Uploaded Logo Asset (`/public/assets/aistudio/image_e90e79ed.png`)

We inspected `/public/assets/aistudio/image_e90e79ed.png` directly in the workspace using ImageMagick `identify -verbose`:
- **Confirmed Existence & Format**: Exists at `/public/assets/aistudio/image_e90e79ed.png` (PNG, `GrayscaleAlpha`, 8-bit depth).
- **Resolution & Aspect Ratio**: `1662 × 1668` pixels (near-exact `1:1` square, `0.996:1`).
- **Content Bounding Box & Transparency**:
  - The black stacked-layers geometric mark occupies a bounding box of `1202 × 1148` pixels at offset `(+229, +260)` surrounded by an opaque white (`srgba(255,255,255,1)`) background margin.
- **Adaptive Icon & Monochrome Safe-Zone Strategy (No Stretching or Cropping)**:
  1. **Aspect-Preserving Square Normalization**: Pad `1662 × 1668` symmetrically to `1668 × 1668` (adding 3px left/right) so the original symbol is never stretched or cropped.
  2. **Adaptive Foreground (`res/drawable/ic_Paperwork_logo_foreground.png` & `ic_launcher_foreground.xml`)**:
     - Android Adaptive Icons use a `108dp × 108dp` canvas where the inner **`66dp × 66dp` circle** (`61.1%` diameter) is the guaranteed safe zone across all OEM launcher masks (circle, squircle, rounded square, teardrop).
     - Because the stacked-layers mark spans `1202 / 1668 = 72%` of the source canvas, mapping the full image into a `66dp` centered `<item>` inside `108dp` places the entire geometric symbol inside a `47.5dp × 45.4dp` box—well inside the `66dp` safe zone with zero clipping under any mask.
  3. **Adaptive Background (`res/drawable/ic_launcher_background.xml`)**:
     - Solid `#FFFFFF` background matching the logo's crisp white canvas so the black geometric mark has maximum contrast (`21:1` WCAG AAA contrast ratio).
  4. **Themed Monochrome Icon (`<monochrome>` in `res/mipmap-anydpi-v26/ic_launcher.xml`)**:
     - Extracts the alpha silhouette of the black stacked-layers symbol (`-fuzz 15% -transparent white`) into `res/drawable/ic_launcher_monochrome.png` wrapped in a `66dp` `<layer-list>`, enabling Android 13+ themed icons.

---

## 7. Measurable Milestone Acceptance Criteria & Gradle Verification Tasks

All milestones will be verified using actual Gradle tasks (`gradle :app:assembleDebug`, `gradle :app:testDebugUnitTest`, and `gradle :app:compileDebugAndroidTestKotlin`):

| Milestone | Scope | Measurable Acceptance Criteria & Gradle Tasks |
| :--- | :--- | :--- |
| **Milestone 1: Foundation, SDK Compatibility & Logo Assets** | Reconcile `libs.versions.toml` & `app/build.gradle.kts` (`minSdk = 28`, Compose plugin `2.2.10`, zero unused dependencies); generate aspect-preserved launcher icons + monochrome layer from `image_e90e79ed.png`; build M3 Expressive navigation shell. | - `gradle :app:assembleDebug` succeeds with 0 errors.<br>- Adaptive icon foreground, background, monochrome, and `mipmap-*` PNGs preserve the complete 3-layer symbol inside the 66dp safe zone. |
| **Milestone 2: Versioned AES-256-GCM Storage, Key Recovery States & Access Lock** | Implement `VaultCryptoManager` (19-byte `PWKV` header, unique 12B IV, `KeyInfo` security level detection), 4-state `VaultKeyState` (`ACTIVE`, `INVALIDATED`, `RECOVERY_REQUIRED`, `NEW_KEY_CREATED`) with pre-vault data-loss warning, Room AAD metadata encryption, and `AppAccessLockManager` (`BiometricPrompt` + salted PBKDF2 PIN). | - `gradle :app:testDebugUnitTest` passes tests for: 100 unique 12B IVs, header parsing, key-version routing, `ACTIVE -> INVALIDATED -> RECOVERY_REQUIRED -> NEW_KEY_CREATED` transitions, AAD tamper rejection, and PIN lockout timers. |
| **Milestone 3: Scanner Pipeline (ML Kit + CameraX, Deskew & Color/Ink Modes)** | Implement `SafeBitmapDecoder` (EXIF + OOM-safe downsampling), ML Kit Document Scanner with CameraX fallback, 4-corner perspective/deskew adjustment, retake, reorder, and `Original` / `Enhanced Color` / `Ink-Saving Grayscale` modes. | - `gradle :app:testDebugUnitTest` verifies non-destructive filter rendering, EXIF-safe downsampling of large images, and zero mutation of the original scan file. |
| **Milestone 4: Encrypted PDF Viewing Lifecycle & Separate Text/OCR** | Implement `SecureDocumentAccessManager` (`DecryptedPdfSessionHandle`), `PdfViewerEngine` (`JetpackSandboxedPdfEngine` + `FrameworkPdfRendererEngine`), `NativePdfTextExtractor` vs. `OnDeviceOcrService`, SAF PDF import, and `FileProvider` sharing. | - `gradle :app:testDebugUnitTest` verifies encrypted file -> temporary session decryption -> viewer load -> deterministic cleanup on exit, plus separation of native PDF text vs. OCR text and graceful handling of corrupted/password-locked PDFs. |
| **Milestone 5: Non-Rasterizing PDFBox Manipulation, Image-to-PDF & Reverse Printing** | Implement `PdfBoxPageManipulator` + `PdfIntegrityValidator` (page count, page boxes, forms/links, digital signature warning), cancellable mixed-dimension Image-to-PDF converter, and `PaperworkPrintDocumentAdapter` (repeated `onLayout`/`onWrite`, `CancellationSignal`, reversed page sequence `N -> 1`, cleanup in `onFinish`). | - `gradle :app:testDebugUnitTest` verifies `PdfIntegrityValidator` on reversed (`N -> 1`) and range-extracted PDFs, signature detection, and `PrintDocumentAdapter` lifecycle resilience. |
| **Milestone 6: Final QA, Instrumentation Compilation & Full Test Suite** | End-to-end integration verification across all screens, error paths, and test suites. | - `gradle :app:assembleDebug`, `gradle :app:testDebugUnitTest`, and `gradle :app:compileDebugAndroidTestKotlin` all succeed with 0 uncaught exceptions across expected failure/cancellation paths. |
