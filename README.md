# android-read-assets

An Android sample project demonstrating how to list files bundled in `app/src/main/assets/` (PDFs in this example) and open them in an external viewer app on **modern Android (Android 10 through Android 16 / API 36)**.

## Version Information

- **App Version:** `2.0` (`versionCode 2`) — modernized from `1.0` (`versionCode 1`, Feb 2020)
- **Android SDK:** `compileSdk 36` / `targetSdk 36` (Android 16) / `minSdk 23` (Android 6.0)
- **Build Toolchain:** Gradle `8.14.5`, Android Gradle Plugin `8.13.1`, Java `17`
- **Key Dependencies:** `androidx.appcompat:1.7.1`, `androidx.activity:1.10.1`, `androidx.constraintlayout:2.2.1`

---

## Is This Issue Still Applicable to Modern Android?

**Yes — the core Android limitation still exists, but the old 2020 workaround is no longer valid.**

1. **Why you still cannot open `assets/` directly in an external app:**
   - Files in `app/src/main/assets/` are packaged inside the APK archive rather than existing as standalone files on the device filesystem.
   - External apps (such as Google Drive, Adobe Acrobat, or the system PDF viewer) **cannot** read `file:///android_asset/...` URIs (those only work inside your own app's `WebView`), and sharing raw `file://` URIs across apps has thrown `FileUriExposedException` since Android 7.0 (API 24).
   - Therefore, to open a bundled asset with an external `Intent.ACTION_VIEW` viewer, you **still** need to extract/copy the asset and share it via a `content://` URI with `Intent.FLAG_GRANT_READ_URI_PERMISSION`.

2. **Why the original 2020 approach broke on modern Android:**
   - The original version of this repository requested `WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE` and copied assets to `Environment.getExternalStorageDirectory() + "/pdfFiles"`, assuming the file had to live in a globally accessible external folder.
   - Starting in **Android 10 (API 29)** and enforced in **Android 11+ (API 30+)**, **Scoped Storage** blocks apps from creating arbitrary directories in shared external storage. `Environment.getExternalStorageDirectory()` is deprecated, `android:requestLegacyExternalStorage` is ignored when targeting API 30+, and `WRITE_EXTERNAL_STORAGE` has no effect (on Android 13+ / API 33+, requesting it is silently denied without showing a dialog).

3. **The modern Android solution (implemented in this repo):**
   - Copy the asset into your app's **private internal cache directory** (`getCacheDir()/pdfs/`).
   - Expose **only** that subdirectory through `androidx.core.content.FileProvider` using `<cache-path>`.
   - Launch `Intent.ACTION_VIEW` with the `content://` URI and `Intent.FLAG_GRANT_READ_URI_PERMISSION`.
   - **Zero runtime or manifest storage permissions are required.**

---

## What Changed from the 2020 Version

| Area | Original (Feb 2020, API 29) | Updated (Modern Android, API 36) |
| :--- | :--- | :--- |
| **Target location** | `Environment.getExternalStorageDirectory() + "/pdfFiles"` (blocked by Scoped Storage) | `new File(getCacheDir(), "pdfs")` (app-private cache) |
| **Storage permissions** | `WRITE_EXTERNAL_STORAGE` & `READ_EXTERNAL_STORAGE` + runtime request dialog | **None required** |
| **`FileProvider` paths** | `<external-path name="external_files" path="." />` (exposed all external storage) | `<cache-path name="pdf_cache" path="pdfs/" />` (least-privilege scope) |
| **Manifest** | Missing `android:exported`, deprecated `package` & `requestLegacyExternalStorage` | `android:exported="true"` on `MainActivity` (required on API 31+), `${applicationId}.fileprovider` |
| **File copying** | Ran synchronously on main UI thread; directory only created in permission callback | Runs on a background `ExecutorService` with atomic temp-file rename and automatic `mkdirs()` |
| **Intent launching** | `setFlags()` overwrote `FLAG_ACTIVITY_CLEAR_TOP`; silent failure if no viewer installed | Uses `addFlags(FLAG_GRANT_READ_URI_PERMISSION)` + `ClipData`, with user-facing `Toast` fallback |
| **Edge-to-Edge (API 35+)** | Not handled | Applies `WindowInsetsCompat` system bar & display cutout insets |
| **Build toolchain** | Gradle 5.4.1, AGP 3.5.3, defunct `jcenter()`, `compileSdk`/`targetSdk` 29 | Gradle 8.14.5, AGP 8.13.1, `mavenCentral()`, `compileSdk`/`targetSdk` 36, Java 17 |

---

## Step-by-Step Guide

### 1. Place PDF files in `app/src/main/assets/`

Add any `.pdf` files you want to bundle inside `app/src/main/assets/` (this repo includes `1_doc.pdf` through `6_doc.pdf`).

### 2. Declare `FileProvider` in `AndroidManifest.xml`

No `<uses-permission>` entries for storage are needed. Declare `androidx.core.content.FileProvider` inside `<application>` in [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml):

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths" />
</provider>
```

### 3. Scope shared paths in `res/xml/file_provider_paths.xml`

Limit `FileProvider` access strictly to the `pdfs/` subdirectory of your internal cache directory in [`app/src/main/res/xml/file_provider_paths.xml`](app/src/main/res/xml/file_provider_paths.xml):

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path
        name="pdf_cache"
        path="pdfs/" />
</paths>
```

### 4. Copy the asset to cache and launch `ACTION_VIEW`

In [`MainActivity.java`](app/src/main/java/com/mirislam/readasset/MainActivity.java):

1. List PDFs from `getAssets().list("")`.
2. When a row is tapped, copy the asset into `new File(getCacheDir(), "pdfs")` on a background thread.
3. Generate a temporary `content://` URI with `FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file)`.
4. Launch `Intent.ACTION_VIEW` with `application/pdf`, `Intent.FLAG_GRANT_READ_URI_PERMISSION`, and `ClipData`, catching `ActivityNotFoundException` if no PDF viewer is installed.

---

## Modern Alternatives

Depending on your app's needs, you may not need an external viewer at all:

- **In-app PDF viewing with Jetpack PDF (Recommended for in-app UX):** Google provides [`androidx.pdf:pdf-viewer-fragment`](https://developer.android.com/jetpack/androidx/releases/pdf) (and `androidx.pdf:pdf-compose`), which renders PDFs natively inside your app with search, zoom, and text selection without handing off to an external app.
- **Low-level in-app rendering:** Android's built-in `android.graphics.pdf.PdfRenderer` renders PDF pages to `Bitmap`s (note: it still requires a seekable `ParcelFileDescriptor`, so compressed assets must either be copied to cache or excluded from APK compression via `androidResources { noCompress += "pdf" }`).
- **Avoiding disk copies for external viewers:** You can write a custom `ContentProvider` overriding `openAssetFile()` paired with `noCompress += "pdf"` in `build.gradle`, though copying on demand to `getCacheDir()` with `FileProvider` is simpler and lets Android automatically reclaim cache space when storage is low.

---

## Building and Running

### Requirements

- **JDK 17** or newer (e.g., Android Studio's bundled JBR)
- **Android SDK** with Platform **API 36** (`compileSdk 36`, `minSdk 23`)
- An Android device or emulator with a PDF viewer app installed (e.g., Google Drive / Files)

### Build from the Command Line

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Or open the project directly in Android Studio and click **Run**.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).
