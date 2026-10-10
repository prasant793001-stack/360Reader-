# 360Reader

A fast, read-only viewer for Android and Windows 11. It opens Word, Excel,
PowerPoint, PDF, images, text and CSV files, and shows each one in its real
layout.

One Kotlin codebase (Compose Multiplatform) builds both apps.

---

## Why files look right

| File type | How it's drawn |
|---|---|
| **Word, Excel, PowerPoint, OpenDocument, RTF, Visio, Publisher** | Rendered by **LibreOffice**, the same engine as the LibreOffice desktop suite. It does real pagination, real slide layout, and uses fonts that match Office fonts metric-for-metric (Carlito for Calibri, Liberation for Arial/Times…). The result is cached, so the second open is instant. |
| **PDF** | Android's built-in PDF renderer (PDFium) on the phone; Apache PDFBox on Windows. |
| **Images** (JPG, PNG, WebP, GIF, BMP, HEIC*, AVIF*) | Native decoders, with photo rotation (EXIF) applied. |
| **Text and code** (TXT, MD, JSON, XML, logs, source code…) | Native text view with line numbers, wrap toggle and find. |
| **CSV / TSV** | Spreadsheet-style grid with a sticky header row. |

\* HEIC/AVIF open on Android 9+ phones. Windows can't decode HEIC yet.

Spreadsheets are exported one page per sheet, so a sheet appears whole
instead of being chopped into printer-sized pieces.

### Features

- Continuous page view with pinch zoom, double-tap zoom and Ctrl + mouse-wheel zoom (Windows)
- Zoom buttons and a page indicator ("Slide 4 of 18")
- **Present** mode for slide decks: full screen; swipe, tap or use the arrow keys / Space; Esc or Back exits
- Find in text files, with match highlighting and next/previous
- Recent files list
- "Open with 360Reader" from Files, WhatsApp, Gmail, Drive… (Android); drag and drop or "Open with" (Windows)
- Light and dark theme follow the system

---

## Project layout

```
360Reader/
├── composeApp/
│   ├── src/commonMain/      Shared app: screens, viewers, file detection, recent files
│   ├── src/androidMain/     Android: activity, PDF renderer, LibreOfficeKit engine
│   ├── src/desktopMain/     Windows: window, PDFBox, LibreOffice engine, drag and drop
│   ├── icons/               Windows app icon
│   └── resources/windows/   Files shipped next to the Windows app (optional bundled LibreOffice)
├── libreoffice-kit/         LibreOfficeKit Java bindings + the Android engine once built
└── tools/
    ├── build-android-engine.sh        One-time build of the Android office engine
    └── bundle-libreoffice-windows.ps1 Optional: ship LibreOffice inside the Windows installer
```

---

## Publish on GitHub (cloud builds)

You don't need to build anything on your PC to get the apps.
`.github/workflows/build.yml` builds them on GitHub's servers:

1. Push the project to a GitHub repository.
2. On GitHub, go to **Releases → Draft a new release**. Create a tag such as `v1.0.0` and press **Publish release**.
3. Wait about 10–15 minutes. The release then shows **360Reader-1.0.0.apk** and **360Reader-1.0.0.msi**.
4. On your phone, open the release page, tap the APK and choose **Install**. On your PC, download the MSI and run it.

Every push to `main` also builds both apps, as a quick check that everything compiles. You can see the results under the **Actions** tab.

**Keep updates installable:** add your own signing key as repository secrets:
`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`.
Without them, each build uses a throwaway key, and you'd have to uninstall before installing a newer version.

---

## 1. Set up your PC (once)

1. Install **Android Studio** (latest stable): https://developer.android.com/studio
2. Install a **JDK 21** for building the Windows app. Eclipse Temurin 21 is a good choice: https://adoptium.net
   (Android Studio's built-in JDK runs the app fine, but it can't make the Windows installer.)
3. Install **LibreOffice** (free): https://www.libreoffice.org/download/
   The Windows app uses it to draw Office files.

Unzip this project somewhere simple, e.g. `C:\Projects\360Reader`.

---

## 2. Windows 11 app

Open **PowerShell** in the project folder.

**Run it:**

```powershell
.\gradlew.bat :composeApp:run
```

The first build downloads its dependencies and takes several minutes. After
that it starts in seconds.

**Make an installer** (`.msi`):

```powershell
.\gradlew.bat :composeApp:packageReleaseMsi
```

The installer is created in
`composeApp\build\compose\binaries\main-release\msi\`.
Double-click it to install 360Reader with a Start-menu entry.

Other options:

- `packageReleaseExe` builds an `.exe` installer instead.
- `createReleaseDistributable` builds a ready-to-run folder with `360Reader.exe`, and no installer.

**Open files with it:** right-click a file → **Open with** → **Choose another app** → 360Reader.
You can also drag files onto the window.

> If you see *"Install the free office engine"*, LibreOffice isn't installed in the
> usual place. Install it, or set the environment variable `READER360_SOFFICE`
> to the full path of `soffice.exe`.

---

## 3. Android app

1. In Android Studio: **File → Open** → choose the `360Reader` folder. Let Gradle sync.
   If Studio offers to upgrade the Android Gradle Plugin, you can accept.
2. On your phone, turn on **Developer options → USB debugging**, then plug the phone in.
3. Pick the **composeApp** run configuration and your phone, then press **Run ▶**.

**Make an APK to install directly:**

```powershell
.\gradlew.bat :composeApp:assembleRelease
```

The APK is created at `composeApp\build\outputs\apk\release\composeApp-release.apk`.
Copy it to the phone and open it to install. You may need to allow
"Install unknown apps" for your file manager.

Without the office engine, the Android app already opens PDF, images, text and
CSV files. For Word, Excel and PowerPoint on the phone, do step 4 once.

---

## 4. Android office engine (one time)

Phones don't have LibreOffice installed, so the app carries its own copy.
LibreOffice doesn't publish a ready-made Android library, so you build it once
from source. The script does everything for you.

**On Windows, use WSL2 (Ubuntu):**

1. Open PowerShell **as Administrator** and run `wsl --install -d Ubuntu-24.04`. Restart if asked, then create your Ubuntu user.
2. In the Ubuntu window, run:

   ```bash
   cd /mnt/c/Projects/360Reader        # wherever you unzipped the project
   bash tools/build-android-engine.sh
   ```

3. Wait. The first build takes **1–3 hours** and needs about **40 GB** of free disk space and ideally 16 GB of RAM.
   The heavy work happens inside Ubuntu's own disk (`~/lo-android`). The script copies only the finished engine into the project.
4. Back in Android Studio, **Build → Rebuild Project**, then run the app again.

The app detects the engine automatically. Office files now open on the phone,
and the build switches to 64-bit ARM only, which covers all modern phones.

**What it adds:** roughly 60–90 MB to the APK. That's the price of exact
rendering with no internet needed and no files leaving the phone.

**If the build stops** with a missing-tool error, install the tool it names
with `sudo apt install <name>` and run the script again. It resumes where it
left off. To build a specific LibreOffice version, set it first, e.g.
`LO_BRANCH=libreoffice-25-8 bash tools/build-android-engine.sh`.

---

## 5. Supported formats

| Category | Extensions |
|---|---|
| Documents | doc, docx, docm, dot, dotx, rtf, odt, ott, wpd, wps, pages, hwp |
| Spreadsheets | xls, xlsx, xlsm, xlsb, xlt, xltx, ods, numbers |
| Presentations | ppt, pptx, pptm, pps, ppsx, pot, potx, odp, key |
| Drawings | vsd, vsdx, odg, pub, cdr |
| PDF | pdf |
| Images | jpg, jpeg, png, webp, gif, bmp, heic, heif, avif, ico |
| Text & code | txt, md, log, json, xml, html, css, js, ts, kt, java, py, c, cpp, cs, go, rs, sql, yaml, ini and more |
| Tables | csv, tsv |

Files with an unknown extension are identified by their contents.

---

## 6. Before you publish (later)

- **Signing:** release builds currently use the debug key so they install
  easily. Create your own upload key in Android Studio (**Build → Generate Signed
  App Bundle**) and update `signingConfig` in `composeApp/build.gradle.kts`.
- **Play Store:** upload an `.aab` (`bundleRelease`). The engine fits within
  Play's size limits for arm64.
- **Windows:** run `tools\bundle-libreoffice-windows.ps1` so the installer
  carries LibreOffice and users don't need to install it separately.
  For the Microsoft Store, package as MSIX.
- **Licences:** LibreOffice is MPL 2.0. Add an "Open-source licences" screen
  that credits LibreOffice, PDFBox (Apache 2.0) and Compose (Apache 2.0).
  See `libreoffice-kit/LICENSE-MPL-2.0.txt`.
- **App name:** check "360Reader" is free in both stores, or change
  `applicationId`, `app_name` and `packageName`.

### Good next features

Text search inside PDFs and Office files, a page thumbnail sidebar,
sheet tabs with sheet names for spreadsheets, a single-instance window on
Windows, and print/share buttons.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `jpackage` / packaging fails on Windows | Use JDK 21 (Temurin): set `JAVA_HOME` to it, then run the command again. |
| Gradle sync fails with "Unsupported class file" | In Android Studio: **Settings → Build Tools → Gradle → Gradle JDK** → choose JDK 17 or 21. |
| Office file shows "LibreOffice couldn't convert" | Try opening the file in LibreOffice directly. If it's password protected, remove the password first. |
| First Office file on Windows is slow | LibreOffice prepares its private profile on first use (5–10 s). Later files take 1–3 s, and repeat opens are instant from cache. |
| Phone says the engine "couldn't start" | The engine was built for a different CPU. It must be 64-bit ARM (`arm64-v8a`), which is what the script builds. |
| Cached rendering looks outdated | Delete `%LOCALAPPDATA%\360Reader\Cache` (Windows) or clear the app's cache (Android). |
