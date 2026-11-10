# Paperwork — Final Validated Architecture, Dependency Catalog & Milestone Specification

Paperwork is a privacy-first, Google Pixel-inspired Android application built with Jetpack Compose and Material 3 Expressive. This final specification incorporates all nine architectural validation requirements, integrates your uploaded stacked-layers **Paperwork logo** (`/public/assets/aistudio/image_e90e79ed.png`) as the official adaptive app launcher icon and in-app brand mark, and structures delivery into six independently testable, crash-free milestones.

---

## User Review & Final Validation Checklist

> [!IMPORTANT]
> **Please review and approve (`Proceed`) this validated specification to begin Milestone-by-Milestone implementation:**
> - **App Logo & Branding**: The uploaded geometric stacked-layers logo (`/public/assets/aistudio/image_e90e79ed.png`) will be configured as the adaptive launcher icon (`ic_launcher_foreground.xml`, `ic_launcher_background.xml`, and all `mipmap-*` densities) as well as the header brand mark inside the app.
> - **Zero-Crash Resilience**: Every hardware, file I/O, camera, PDF, OCR, and print boundary is wrapped in defensive state/error handlers (`Result<T>` / sealed UI states) so malformed files, missing Play Services, cancelled dialogs, or large images never crash the application.

---

## 1. Final Validated Architecture (9 Core Corrections)

### 1. PDF Compatibility & `PdfEngine` Abstraction (`androidx.pdf` vs. Tested Fallback)
- **Verified `androidx.pdf:1.0.0-beta01` Artifact Requirements**:
  - During build inspection of the Google Maven AAR metadata (`pdf-core:1.0.0-beta01`, `pdf-compose:1.0.0-beta01`, `pdf-viewer:1.0.0-beta01`, `pdf-document-service:1.0.0-beta01`, `pdf-ocr-play-services:1.0.0-beta01`), we verified two concrete platform constraints:
    1. **Manifest & Compile Metadata**: The `1.0.0-beta01` AARs declare `minSdkVersion = 28` in their manifest and `minCompileSdk = 37` in AAR metadata.
    2. **Runtime Sandboxed Service Requirement**: `SandboxedPdfLoader` (`pdf-document-service`) binds to an isolated `<service>` backed by framework `PdfRenderer` / `PdfRendererPreV` APIs that require **Android 15 (API 35+)** natively, or **Android 12–14 (API 31–34)** with **SDK Extension Level R (`Build.VERSION_CODES.R`) >= 13** (`SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 13`). On API 24–30 or devices where `SdkExtensions.getExtensionVersion(R) < 13`, `SandboxedPdfLoader.openDocument()` throws `UnsupportedOperationException` / `SecurityException` or fails service binding.
- **`PdfEngine` Abstraction**:
  - Paperwork defines a clean `PdfViewerEngine` interface with two concrete implementations:
    1. **`JetpackSandboxedPdfEngine`**: Activated only when `PdfRuntimeCapability.probe(context)` confirms API 35+ or `(API >= 31 && SdkExtensions.getExtensionVersion(R) >= 13)` **and** `SandboxedPdfLoader` successfully opens the document. Uses `@OptIn(ExperimentalPdfApi::class) androidx.pdf.compose.PdfViewer` + `PdfViewerState` + `MlKitOcrProvider`.
    2. **`FrameworkPdfRendererEngine` (Tested Fallback)**: Activated automatically on API 24–30, on devices without SDK Extension R >= 13, or if `SandboxedPdfLoader` reports an encrypted/corrupted stream error. Uses a single-threaded mutex-guarded `android.graphics.pdf.PdfRenderer` tile/page pool with Compose pinch-to-zoom, double-tap zoom, and page position persistence.
- **Strict Separation of PDF Text Extraction vs. OCR**:
  - **PDF Text Extraction (`NativePdfTextExtractor`)**: Reads embedded PDF character streams and glyph coordinates directly from the PDF's `/Contents` stream via `PDFTextStripper` (`pdfbox-android`). Instant, deterministic, and lossless for digital PDFs.
  - **Optical Character Recognition (`OnDeviceOcrService`)**: Runs `com.google.mlkit:text-recognition` (`play-services-mlkit-text-recognition:19.0.2`) on rendered page bitmaps **only** when a page lacks an embedded text layer or when the user explicitly triggers OCR. The UI and data model store `nativePdfText` and `ocrRecognizedText` in separate fields (`TextSource.NATIVE_PDF_LAYER` vs. `TextSource.MLKIT_OCR`) so OCR output is never conflated with embedded PDF text.

### 2. Non-Rasterizing PDF Page Manipulation (`pdfbox-android`) & Validation
- **Accurate Role Definition**:
  - `com.tom-roush:pdfbox-android:2.0.27.0` is used strictly for **non-rasterizing PDF page-level manipulation** (reordering pages for Reverse Print, extracting page subsets, rotating page `/Rotate` dictionaries, and inspecting embedded text/structure)—**not** claimed as universally lossless arbitrary content editing.
- **Original Document Preservation**:
  - Any page manipulation operation (rotation, page extraction, or reverse ordering) **never overwrites the original vault PDF in-place** unless explicitly requested; it writes to a new output file or temporary print stream while keeping the original document untouched.
- **Post-Write Structural Validation (`PdfIntegrityValidator`)**:
  - Every rewritten PDF is validated before committing to the vault or print spooler by reopening the output with `PDDocument.load()` and verifying:
    - Expected `numberOfPages` matches the target operation.
    - Page boxes (`MediaBox`, `CropBox`) and `/Rotate` angles are preserved.
    - Document catalog entries (`AcroForm` forms, page `Annotations` / links, embedded font resources) remain readable.
    - **Digital Signatures Detection**: Before modifying any PDF, `pdDocument.signatureDictionaries` is inspected. If cryptographic digital signatures are present, Paperwork warns the user that reordering or rotating pages will invalidate existing digital signatures and preserves the signed original untouched.

### 3. Versioned `AES-256-GCM` Encryption, Key Versioning & Actual Storage Semantics
- **Versioned Binary Envelope Format**:
  - Every encrypted file (`.enc`) and Room metadata blob uses a self-describing binary header so key rotation and format upgrades are deterministic:
    ```
    ┌──────────────┬───────────────┬──────────────┬────────────────┬──────────────────────────┐
    │ Magic (4 B)  │ FormatVer(1B) │ KeyVer (2 B) │ Random IV(12B) │ Ciphertext + GCM Tag(16B)│
    │  "PWKV"      │    0x01       │   0x0001     │  SecureRandom  │  AES/GCM/NoPadding       │
    └──────────────┴───────────────┴──────────────┴────────────────┴──────────────────────────┘
    ```
- **Unique 12-Byte Random IV Per Encryption**:
  - Every call to `encryptBytes()` / `encryptStream()` generates a fresh 12-byte (96-bit) IV using `Keystore` randomized encryption (`setRandomizedEncryptionRequired(true)`) and verifies `iv.size == 12` (never reusing an IV across files or metadata updates).
- **Key-Version Handling & Recovery Strategy**:
  - Keys in `AndroidKeyStore` are aliased by version (`"paperwork_vault_aes_gcm_v1"`, `"paperwork_vault_aes_gcm_v2"`). When decrypting, the engine reads `KeyVer` from the 19-byte header and selects the matching Keystore alias.
  - If `KeyPermanentlyInvalidatedException` or `UnrecoverableKeyException` occurs, Paperwork increments the active key version for new documents, marks older unreadable entries with a `VaultStatus.KEY_INVALIDATED` state (never crashing), and offers a guided **Vault Recovery & Re-Import** workflow.
- **Actual Keystore Security Level Detection**:
  - Rather than promising hardware backing on every device, Paperwork inspects `SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java)` (`KeyInfo.securityLevel` on API 31+ / `isInsideSecureHardware` on API 24–30) and reports the true runtime backing in Settings:
    - `StrongBox Hardware Security Module`
    - `Trusted Execution Environment (TEE)`
    - `Software-Enforced Keystore`
- **Honest Flash Storage Deletion Semantics**:
  - Because modern Android flash storage (UFS/eMMC with FTL wear-leveling and F2FS/ext4 journaling) does not guarantee physical block erasure via userspace file overwriting, Paperwork does **not** claim that zero-overwriting a file physically erases flash cells.
  - Instead, security relies on **never writing sensitive vault files to disk in plaintext** (encrypting before persistence) and minimizing temporary decrypted file lifetime in `/cache/active_session_tmp/` with immediate `File.delete()` + OS FBE (File-Based Encryption) protection.

### 4. App Access Lock: Biometric / Device Credential & Salted PIN
- **Accurate Security Boundary**:
  - The optional 4-digit PIN is explicitly documented and architected as an **in-app UI access lock**, **not** the cryptographic encryption key (since a 4-digit PIN has only $10^4 = 10,000$ combinations).
- **Biometric & Device Credential Support (`androidx.biometric:biometric`)**:
  - Alongside the salted PBKDF2-HMAC-SHA256 4-digit PIN (with exponential retry cooldowns: 30s at 5 failures, 2m at 6–7, 10m at 8+), Paperwork integrates Android's `BiometricPrompt` (`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`) so users can unlock the app using fingerprint, face unlock, or their OS screen lock credential.

### 5. Resilient Print Lifecycle & Reversed Page Sequence Spooling
- **Repeated `onLayout()` / `onWrite()` & Cancellation Handling**:
  - Android's system print dialog invokes `onLayout()` and `onWrite()` multiple times as the user changes paper size, orientation, or page range in the system preview.
  - `PaperworkPrintDocumentAdapter` supports arbitrary repeated `onLayout()` and `onWrite()` calls, respects `CancellationSignal` immediately (`onLayoutCancelled()` / `onWriteCancelled()`), honors the `pages: Array<out PageRange>` requested by the system spooler, and closes every `ParcelFileDescriptor` and `FileInputStream` / `FileOutputStream` in `use {}` blocks.
- **Temporary Print File Lifecycle**:
  - The prepared temporary PDF file is kept alive and readable across all `onLayout()` and `onWrite()` passes and is **only** deleted inside `PrintDocumentAdapter.onFinish()` (which Android invokes only after the print job is queued to the system spooler or the user dismisses/cancels the system print UI), plus a startup/session sweep for abandoned crash artifacts.
- **Reverse Ordering Definition**:
  - Reverse page ordering (`N → 1`) uses `pdfbox-android` to construct a temporary print PDF containing the **reversed page sequence** (`Page N, Page N-1, ..., Page 1`) before handing the stream to Android's `PrintManager`, ensuring face-up output trays stack multi-page documents in natural reading order.

### 6. Complete Scanner Pipeline & Color-Preserving Filters
- **Capabilities**:
  - **Primary**: Google ML Kit Document Scanner (`SCANNER_MODE_FULL`) providing automatic edge detection, perspective correction, manual corner adjustment, rotation, multi-page capture, and retake.
  - **Fallback & Studio**: In-app CameraX capture + interactive **4-Corner Perspective & Deskew Adjuster**, 90° rotation, page reordering, single-page retake, and multi-page addition.
- **Color Preservation & Optional Ink-Saving Filters**:
  - The untouched **Original** capture is always preserved in full color.
  - In **Scanner Preview Studio**, each page supports selectable, non-destructive filter modes:
    1. `Original (Untouched Color)`
    2. `Enhanced Color (Default)` — Preserves full color fidelity (stamps, signatures, highlights) while normalizing paper background and contrast.
    3. `Ink-Saving Grayscale (Optional)` — Optional monochrome/high-contrast filter for black-and-white printing.

### 7. Resilient Document Workflows (SAF Import, Secure Share, Large/Mixed Images)
- **External PDF Import via SAF**: Uses `ActivityResultContracts.OpenDocument()` (`application/pdf`), copies and validates the stream on `Dispatchers.IO` with bounded buffer reads, inspects for password protection or corruption before importing, and displays a friendly error state if a PDF is password-locked or malformed.
- **Secure Sharing**: Uses `FileProvider` (`Intent.ACTION_SEND`) with `FLAG_GRANT_READ_URI_PERMISSION` and `ClipData` so receiving apps get temporary read-only access without exposing internal file paths.
- **Large Images, Mixed Dimensions & Interrupted Conversion**:
  - Decodes images using two-pass `BitmapFactory.Options` (`inJustDecodeBounds` + power-of-two `inSampleSize`) and EXIF orientation normalization so 48MP+ camera photos never trigger `OutOfMemoryError`.
  - Normalizes mixed portrait/landscape/square images onto the target page canvas (`A4`, `Letter`, `Legal`) with aspect-ratio-preserving centering.
  - Runs conversion inside a cancellable coroutine/job that cleans up partial output files if cancelled or interrupted.

---

## 2. Reconciled Dependency Catalog (`gradle/libs.versions.toml`)

All unused template dependencies (`retrofit`, `okhttp`, `moshi`, `firebase-*`, `accompanist`, `play-services-location`) are **completely removed** (not commented out), and the Compose BOM is reconciled to `2025.02.00` for Kotlin `2.2.10` and AGP `9.1.1`:

```toml
[versions]
agp = "9.1.1"
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
pdfboxAndroid = "2.0.27.0"
mlkitDocumentScanner = "16.0.0"
mlkitTextRecognition = "19.0.2"
secretsGradlePlugin = "2.0.1"
googleServices = "4.5.0"
junit = "4.13.2"
androidxJunit = "1.3.0"
espressoCore = "3.7.0"
androidxTestCore = "1.6.1"
androidxTestRunner = "1.6.2"
robolectric = "4.16.1"
```

---

## 3. Clean Modular Package Structure

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         com.example (Paperwork)                              │
├──────────────────────────────────────────────────────────────────────────────┤
│  ├── core/                                                                   │
│  │    ├── security/     VaultCryptoManager (Header v1, 12B IV, KeyInfo)      │
│  │    │                 AppLockManager (BiometricPrompt + Salted PBKDF2 PIN) │
│  │    ├── pdf/          PdfViewerEngine (JetpackSandboxed + RendererFallback)│
│  │    │                 PdfBoxPageManipulator (Non-rasterizing + Validator)  │
│  │    │                 NativePdfTextExtractor (PDFBox Text Stripper)        │
│  │    ├── ocr/          OnDeviceOcrService (ML Kit Text Recognition)         │
│  │    └── image/        SafeBitmapDecoder (EXIF + OOM-safe Downsampling)     │
│  ├── data/                                                                   │
│  │    ├── local/        PaperworkDatabase, DocumentDao, DocumentEntity       │
│  │    ├── preferences/  PaperworkPreferencesRepository (DataStore)           │
│  │    └── repository/   DocumentRepository (Encrypted Vault & Temp Lifecycle)│
│  ├── domain/                                                                 │
│  │    └── model/        PaperworkDocument, ScanPageRecord, PrintConfig, etc. │
│  ├── features/                                                               │
│  │    ├── home/         HomeScreen (Brand Logo Header, Hero Scan, Recents)   │
│  │    ├── documents/    DocumentsScreen (Search, Filter, Sort, Grid/List)    │
│  │    ├── scanner/      ScannerStudioScreen (4-Corner Deskew, Color/Ink Mode)│
│  │    ├── pdfviewer/    NativePdfViewerScreen (PdfEngine + Search + OCR)     │
│  │    ├── tools/        ToolsScreen (Image-to-PDF, Page Manipulator, Print)  │
│  │    ├── printing/     PaperworkPrintHelper, PaperworkPrintDocumentAdapter  │
│  │    └── settings/     SettingsScreen (KeyInfo Badge, Biometric/PIN, Theme) │
│  └── ui/                                                                     │
│       ├── components/   PaperworkLogoMark, DocumentCards, Dialogs            │
│       └── theme/        Color.kt, Type.kt, Theme.kt (Material 3 Expressive)  │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Six Independently Testable Implementation Milestones & Acceptance Criteria

Once you approve this specification, implementation will proceed sequentially through these six verified milestones:

### Milestone 1 — Foundation, Reconciled Dependencies & Uploaded Logo Integration
- **Scope**: Remove all unused dependencies from `libs.versions.toml` and `app/build.gradle.kts`, reconcile Compose BOM, convert `/public/assets/aistudio/image_e90e79ed.png` into the adaptive launcher icon (`ic_launcher_foreground.xml`, `ic_launcher_background.xml`, and `mipmap-*` PNGs) + in-app `PaperworkLogoMark`, and establish the Material 3 Expressive theme and adaptive navigation shell.
- **Acceptance Criteria**: Clean build passes with zero unused dependencies; launcher icon and header display the uploaded stacked-layers logo.

### Milestone 2 — Versioned `AES-256-GCM` Storage, KeyInfo Detection & Biometric/PIN Access Lock
- **Scope**: Implement `VaultCryptoManager` with the 19-byte versioned header (`PWKV` + `FormatVer` + `KeyVer` + unique 12-byte random IV), `KeyInfo` hardware/TEE/software security-level introspection, Room metadata encryption with AAD, and `AppLockManager` supporting both `BiometricPrompt` / device credential and salted PBKDF2 4-digit PIN with retry cooldowns.
- **Acceptance Criteria**: Unit tests verify unique 12-byte IV generation across 100 consecutive encryptions, version header parsing, key-version dispatch, AAD tamper rejection, and PIN lockout timers.

### Milestone 3 — Scanner Pipeline (ML Kit + CameraX Fallback, 4-Corner Deskew & Color/Ink Filters)
- **Scope**: Implement `SafeBitmapDecoder` (EXIF + OOM-safe downsampling), ML Kit Document Scanner launcher with automatic CameraX fallback, interactive 4-corner perspective/deskew cropper, single-page retake, page reordering, and non-destructive `Original`, `Enhanced Color (Default)`, and `Ink-Saving Grayscale (Optional)` modes.
- **Acceptance Criteria**: Capturing, deskewing, rotating, reordering, and toggling filter modes never mutates the original image and never crashes on large bitmaps.

### Milestone 4 — `PdfEngine` Viewer Abstraction, Separate Native Text vs. OCR & SAF Import/Share
- **Scope**: Implement `PdfViewerEngine` (`JetpackSandboxedPdfEngine` + `FrameworkPdfRendererEngine` fallback), `NativePdfTextExtractor` (separate from `OnDeviceOcrService`), SAF PDF import with password/corruption handling, and `FileProvider` share sheet integration.
- **Acceptance Criteria**: Both digital vector PDFs and scanned PDFs open smoothly, preserve page position, distinguish embedded PDF text from ML Kit OCR text, and handle malformed/password-protected files gracefully without crashes.

### Milestone 5 — Non-Rasterizing `PDFBox` Manipulation, Image-to-PDF & Resilient Reverse Printing
- **Scope**: Implement `PdfBoxPageManipulator` + `PdfIntegrityValidator` (checking page counts, page boxes, forms/links, and digital signature warnings), cancellable multi-image-to-PDF conversion for mixed dimensions, and `PaperworkPrintDocumentAdapter` handling repeated `onLayout()`/`onWrite()`, `CancellationSignal`, FD cleanup, and reversed page sequence (`N → 1`) output.
- **Acceptance Criteria**: Reversed print output preserves vector text and page boxes, passes `PdfIntegrityValidator`, and keeps temporary print files alive until `onFinish()`.

### Milestone 6 — Final QA & Comprehensive Robolectric Verification Suite
- **Scope**: Execute the full JVM/Robolectric test suite covering encryption headers, IV uniqueness, PDFBox structural validation, reverse print sequence generation, mixed-dimension image conversion, and UI state resilience.
- **Acceptance Criteria**: `compile_applet` and `gradle :app:testDebugUnitTest` pass with 0 errors and 0 crashes across all features.
