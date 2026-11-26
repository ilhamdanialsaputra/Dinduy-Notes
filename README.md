<div align="center">

<img src="https://github.com/user-attachments/assets/f9b8e90c-7d38-4257-a2ea-aeacb7f34480" width="96" alt="Dinduy icon">

# Dinduy

**Notes that stay on your phone.** Text, handwriting, PDFs, photos and voice notes in one Android app.

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202025.09-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Material 3](https://img.shields.io/badge/Material%203-Expressive-6750A4?style=for-the-badge&logo=materialdesign&logoColor=white)

[![Release](https://img.shields.io/github/v/release/ilhamdanialsaputra/Dinduy-Notes?style=flat-square)](https://github.com/ilhamdanialsaputra/Dinduy-Notes/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/ilhamdanialsaputra/Dinduy-Notes/build.yml?style=flat-square&label=build)](https://github.com/ilhamdanialsaputra/Dinduy-Notes/actions/workflows/build.yml)
![Min SDK](https://img.shields.io/badge/minSdk-26-informational?style=flat-square)
![Target SDK](https://img.shields.io/badge/targetSdk-36-informational?style=flat-square)
![ABI](https://img.shields.io/badge/ABI-arm64--v8a-informational?style=flat-square)
![Data](https://img.shields.io/badge/data-on--device-success?style=flat-square)
![No tracking](https://img.shields.io/badge/trackers-none-success?style=flat-square)
[![License](https://img.shields.io/badge/license-GPL%20v3-blue?style=flat-square)](LICENSE)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-support-FF5E5B?style=flat-square&logo=kofi&logoColor=white)](https://ko-fi.com/ilhamdanials)

[Description](#description) · [Features](#features) · [Architecture](#architecture) · [Techniques](#interesting-techniques) · [Libraries](#libraries-and-tools) · [Fonts](#fonts) · [Permissions](#permissions) · [Build and release](#build-and-release) · [Structure](#project-structure) · [License](#license)

</div>

## Screenshot
<img width="1920" height="1080" alt="Dinduy Notes_20261108_031652_0000" src="https://github.com/user-attachments/assets/67040d7b-627d-4929-b9cc-d03fbc9adc86" />

<img width="6144" height="3374" alt="IMG_20261126_091144" src="https://github.com/user-attachments/assets/d91a6bb1-c9c3-4ce9-b49d-5de909791a17" />



## Description

Dinduy combines text editing, handwriting, PDF reading, voice notes and reminders. A note is a list of blocks: paragraphs, headings, lists, tables, quotes, code and photos. Rich text is stored as plain text plus formatting ranges. Pen and highlighter strokes attach to the text and move when it reflows.

PDFs open as notes. You can search them, select text, and draw on them. The page-turn mode has two 3D page curls. Voice notes are transcribed on the device with whisper.cpp, or through Groq with your own API key. Text in photos is read with Tesseract.

The network is used only on request: downloading transcription models or OCR language data, Groq transcription, and link previews. The Groq key is stored encrypted ([Secure.kt](app/src/main/java/app/dinduy/Secure.kt)) and is left out of backups.

All app code is in [app/src/main/java/app/dinduy/](app/src/main/java/app/dinduy/), in one flat package. The OpenGL page curl engine is in [silkcurl/](app/src/main/java/app/dinduy/silkcurl/). Build settings are in [app/build.gradle.kts](app/build.gradle.kts).

## Features

| Area | What it does |
|:--|:--|
| **Writing** | Bold, italic, underline, strikethrough, highlight, inline code. Bulleted, numbered, lettered and checklist lists. Tables, code blocks, dividers and seven quote types. Markdown shortcuts (`- `, `# `). Search inside a note, undo and redo. |
| **Photos** | Gallery or camera. Resize, crop, several photos per row, text flowing beside a photo. Note colors follow the cover photo. On-device text scan (OCR). |
| **Drawing** | Pen, highlighter, eraser and selection. Finger or stylus. Hold the end of a stroke to snap it to a shape. |
| **PDF** | Open from other apps through Open with or Share. Scroll or page mode, two 3D page curls, dark pages, search, text selection, drawing on pages, export of notes to A4 PDF. |
| **Voice** | Record inside a note, in the background if the app is minimized. Quick record from the New button. Transcription on-device or through Groq, in Auto, Indonesian or English. |
| **Reminders** | One-time or repeating (daily, weekly, monthly, yearly). Notification actions: Done, Snooze 1 hour, Tomorrow morning. |
| **Organizing** | Pins, labels, archive, trash, 1 or 2 column layout, drag to reorder, swipe gestures. |
| **Sharing** | `.dinduy` files with photos, drawings, audio and PDFs. Full backup, with Merge or Replace on restore. Markdown and text import. |
| **Appearance** | Light, dark (pure black for AMOLED) or system. Material You or custom color, monochrome mode, per-note themes and backdrops, four note fonts. Home-screen widgets for quick notes and a note list. |

## Architecture

- **UI.** One activity ([MainActivity.kt](app/src/main/java/app/dinduy/MainActivity.kt)) with Compose screens. App state lives in one `AppVM` view model ([Store.kt](app/src/main/java/app/dinduy/Store.kt)). Each open note has an `Ed` object ([Editor.kt](app/src/main/java/app/dinduy/Editor.kt)) that holds editor state and undo history.
- **Data.** `Db` holds `Prefs`, a list of `Note` objects (each with `Blk` blocks) and labels ([Model.kt](app/src/main/java/app/dinduy/Model.kt)). Everything is one `dinduy.json` file, encoded with kotlinx.serialization. The decoder ignores unknown keys and coerces bad values, so files from other versions still load.
- **Writes.** Each note's JSON is cached, so unchanged notes aren't encoded again. The file is written to a `.tmp` file and renamed. Widgets refresh after each write.
- **Background work.** A foreground service ([Bg.kt](app/src/main/java/app/dinduy/Bg.kt)) covers downloads, recording and transcription with a single notification. It stops when the work ends or the app is removed from recents.
- **Widgets and alarms.** `RemoteViews` widgets ([NotesWidget.kt](app/src/main/java/app/dinduy/NotesWidget.kt), [QuickNoteWidget.kt](app/src/main/java/app/dinduy/QuickNoteWidget.kt)). Reminder alarms and the boot receiver are in [Reminder.kt](app/src/main/java/app/dinduy/Reminder.kt).

## Interesting techniques

- **[Spans over plain text](https://developer.android.com/develop/ui/compose/text/style-text#multiple-styles).** Rich text is a `String` plus `(start, end, kind)` ranges. After each edit, a common prefix/suffix diff finds the changed region, shifts the ranges and merges overlaps. Each text field starts with a [zero-width space](https://en.wikipedia.org/wiki/Zero-width_space), so Backspace at the start of a block shows up as a text change. See [Model.kt](app/src/main/java/app/dinduy/Model.kt).
- **Text wrapping around images.** Works like [CSS `float`](https://developer.mozilla.org/en-US/docs/Web/CSS/float), built by hand. Where a paragraph passes the bottom of a floating image, the editor adds display-only line breaks and em-space shims. The stored text is unchanged. See [Editor.kt](app/src/main/java/app/dinduy/Editor.kt).
- **highlighter anchored to text.** A highlighter attaches to a letter and keeps its shape in font-size units, so it moves and scales with the text. Holding the end of a pen stroke snaps it to a line, circle, square or triangle, using iterative [Ramer–Douglas–Peucker](https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm) simplification and a haptic tick. See [Ink.kt](app/src/main/java/app/dinduy/Ink.kt).
- **[Pointer event passes](https://developer.android.com/reference/kotlin/androidx/compose/ui/input/pointer/PointerEventPass).** In the PDF reader, parent handlers read touches in the `Initial` pass. Selection handles and two-finger zoom take priority over the pager, list and page curl below them. See [Pdf.kt](app/src/main/java/app/dinduy/Pdf.kt).
- **[Spring animation](https://developer.android.com/develop/ui/compose/animation/customize) from one progress value.** Cards, PDF books and photos morph to their open state from a single float, so closing is the exact reverse of opening. The app uses springs instead of fades. See [Book.kt](app/src/main/java/app/dinduy/Book.kt), [Cover.kt](app/src/main/java/app/dinduy/Cover.kt) and [Photo.kt](app/src/main/java/app/dinduy/Photo.kt).
- **Animated variable fonts.** Titles and headings animate per word along the `wght`, `wdth`, `ROND`, `opsz` and `slnt` axes of Google Sans Flex. This is the Compose counterpart of CSS [`font-variation-settings`](https://developer.mozilla.org/en-US/docs/Web/CSS/font-variation-settings); see also [variable fonts in Compose](https://developer.android.com/develop/ui/compose/text/fonts#variable-fonts). Animation steps are quantized to limit the number of font families in memory, and word widths are locked so lines don't reflow. See [ExpressiveType.kt](app/src/main/java/app/dinduy/ExpressiveType.kt).
- **[GraphicsLayer](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer) caching and windowed composition.** Home cards draw their animated backdrop once into an offscreen layer and reuse it. In long notes, only blocks near the viewport are composed. A cost score with separate on and off thresholds avoids flicker. Text state is held per field, so typing in one block doesn't recompose the others. See [Ornament.kt](app/src/main/java/app/dinduy/Ornament.kt) and [Editor.kt](app/src/main/java/app/dinduy/Editor.kt).
- **3D book from a 2D canvas.** Each face of the PDF book is a plane projected with perspective, using [`Matrix.setPolyToPoly`](https://developer.android.com/reference/android/graphics/Matrix) and [`BitmapShader`](https://developer.android.com/reference/android/graphics/BitmapShader). Back faces are culled, and an off-axis camera moves instead of tilting the book. See [Book3D.kt](app/src/main/java/app/dinduy/Book3D.kt).
- **OpenGL ES page curl.** "Silk curl" embeds the [PlayLikeCurl](https://github.com/karankalsi/PlayLikeCurl) engine as source. A [`GLSurfaceView`](https://developer.android.com/reference/android/opengl/GLSurfaceView) renderer takes a deck of three page bitmaps, sizes textures before allocating them, and checks GLES 2.0 and EGL support first. Pages with drawings or dark mode are rendered into bitmap copies before upload. See [SilkCurl.kt](app/src/main/java/app/dinduy/silkcurl/SilkCurl.kt).
- **PDF text layer.** A `PDFTextStripper` subclass records each character's box as a fraction of the page size, which powers search and selection handles. Android's [`PdfRenderer`](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer) opens one page at a time, so access goes through a coroutine [`Mutex`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/). See [Pdf.kt](app/src/main/java/app/dinduy/Pdf.kt).
- **Vector PDF export.** Notes are written with [`PdfDocument`](https://developer.android.com/reference/android/graphics/pdf/PdfDocument) at A4 size (595 × 842 pt). Text is laid out with [`StaticLayout`](https://developer.android.com/reference/android/text/StaticLayout) and split at block and line boundaries. See [PdfExport.kt](app/src/main/java/app/dinduy/PdfExport.kt).
- **Keystore-backed secret storage.** The Groq key is encrypted with AES-256-GCM using a non-exportable [Android Keystore](https://developer.android.com/privacy-and-security/keystore) key. It uses additional authenticated data (see [`AesGcmParams`](https://developer.mozilla.org/en-US/docs/Web/API/AesGcmParams) for the concept), with the IV stored before the ciphertext. A file that can't be decrypted is deleted. The `secure/` folder is excluded from Auto Backup in [backup_rules.xml](app/src/main/res/xml/backup_rules.xml) and [data_extraction_rules.xml](app/src/main/res/xml/data_extraction_rules.xml).
- **Network handling.** Whisper model downloads resume with a [`Range`](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Range_requests) header from the bytes already on disk ([Audio.kt](app/src/main/java/app/dinduy/Audio.kt)). OCR data goes to a temporary file and is renamed after a size check ([Ocr.kt](app/src/main/java/app/dinduy/Ocr.kt)). The Groq client uses a fixed base URL, sends the key only in the `Authorization` header, doesn't follow [redirects](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Redirections), and drops segments with high `no_speech_prob` and low `avg_logprob` to filter text Whisper invents over silence ([Groq.kt](app/src/main/java/app/dinduy/Groq.kt)).
- **Reminders computed from the first trigger.** The next time is derived from the stored first time plus a repeat rule, so alarms never modify note data. [`AlarmManager`](https://developer.android.com/reference/android/app/AlarmManager) uses exact alarms when permitted and inexact ones otherwise. A receiver reschedules after boot, app update, and time or time zone changes. See [Reminder.kt](app/src/main/java/app/dinduy/Reminder.kt) and [AndroidManifest.xml](app/src/main/AndroidManifest.xml).
- **ZIP-based backup and share files.** A `.dinduy` file is a ZIP (`data.json`, `img/`, `audio/`, `pdf/`, `lp/`) used for both full backups and single-note sharing. The older `DINDUY01` format is still read. File names are built from validated IDs. The app registers the type with [intent filters](https://developer.android.com/guide/components/intents-filters) for `application/x-dinduy` and file-name patterns. See [Store.kt](app/src/main/java/app/dinduy/Store.kt).

## Libraries and tools

- [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android) with [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast): on-device OCR. The `eng` and `ind` data is downloaded once, after consent. Results are parsed as hOCR with jsoup. The library comes from JitPack, which is restricted to that one group in [settings.gradle.kts](settings.gradle.kts).
- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) through the prebuilt [whisper-android](https://github.com/ffmpegkit-maintained/whisper) AAR: offline transcription. The ggml `base` (about 142 MB) and `small` (about 466 MB) models are downloaded from [Hugging Face](https://huggingface.co/ggerganov/whisper.cpp). The AAR only supports arm64-v8a, so the app is limited to that ABI.
- [Groq speech-to-text API](https://console.groq.com/docs/speech-to-text) (`whisper-large-v3`): optional online transcription with the user's own key.
- [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android): text and character positions for search, selection and conversion. Keep rules are in [app/proguard-rules.pro](app/proguard-rules.pro).
- [jsoup](https://jsoup.org/): Markdown handling, hOCR parsing and link preview metadata.
- [pagecurl](https://github.com/oleksandrbalan/pagecurl): the Classic 3D page mode.
- [PlayLikeCurl](https://github.com/karankalsi/PlayLikeCurl) ([maintained fork](https://github.com/Arnab11/PlayLikeCurl)): MIT-licensed GLES2 engine copied into `silkcurl`. The one local change is a wider field of view (45° to 55°).
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) and [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines).
- [Compose BOM 2025.09.00](https://developer.android.com/develop/ui/compose/bom) with Material 3. Material 3 Expressive styling is done in app code ([Expressive.kt](app/src/main/java/app/dinduy/Expressive.kt), [Theme.kt](app/src/main/java/app/dinduy/Theme.kt)).
- [R8](https://developer.android.com/build/shrink-code) in full mode with resource shrinking. Dependency metadata is left out of the APK for F-Droid.
- F-Droid: the [build recipe](metadata/app.dinduy.yml) declares the `NonFreeNet` anti-feature for Groq. Store text and changelogs follow the [fastlane layout](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/).

## Fonts

Font files are bundled in [app/src/main/assets/fonts/](app/src/main/assets/fonts/). The [Prepare F-Droid workflow](.github/workflows/prepare-fdroid.yml) copies them from the [google/fonts](https://github.com/google/fonts) repository. If a file is missing, the app falls back to system fonts.

- [Google Sans Flex](https://fonts.google.com/specimen/Google+Sans+Flex): animated titles and headings.
- [Roboto Flex](https://fonts.google.com/specimen/Roboto+Flex): note font option.
- [Tinos](https://fonts.google.com/specimen/Tinos): note font option, metric-compatible with Times New Roman.
- [Reenie Beanie](https://fonts.google.com/specimen/Reenie+Beanie): handwriting-style note font option.

License texts for Google Sans Flex, Roboto Flex and Reenie Beanie are in [fonts-licenses/](fonts-licenses/).

## Permissions

Declared in [AndroidManifest.xml](app/src/main/AndroidManifest.xml).

| Permission | Used for |
|:--|:--|
| `INTERNET` | Model and OCR data downloads, Groq transcription, link previews |
| `RECORD_AUDIO` | Voice notes |
| `CAMERA` | Photos from the camera (hardware not required) |
| `POST_NOTIFICATIONS` | Reminders and background task progress |
| `SCHEDULE_EXACT_ALARM` (up to API 32), `USE_EXACT_ALARM` | Reminders at the exact time |
| `RECEIVE_BOOT_COMPLETED` | Rescheduling reminders after a restart |
| `FOREGROUND_SERVICE`, `_DATA_SYNC`, `_MICROPHONE` | Recording, downloads and transcription in the background |

## Build and release

- Toolchain: JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.10.1, Kotlin 2.1.21 (Compose compiler plugin), `compileSdk` and `targetSdk` 36.
- Fonts are committed, so the build needs no font download.
- Signing: if `KS_FILE` is set, the build reads `KS_PASS`, `KS_ALIAS` and `KEY_PASS` and signs with that keystore. Without it, no signing config is applied, which is how F-Droid builds.
- [build.yml](.github/workflows/build.yml) builds on pushes to `main` and `master` and on `v*` tags. A tag must match `versionName`. Tag builds also require the fonts, a LICENSE file and a changelog named after `versionCode`. They publish `Dinduy-<tag>.apk` to a GitHub Release, with notes from the commit log.
- [prepare-fdroid.yml](.github/workflows/prepare-fdroid.yml) runs once by hand. It fetches the fonts and their licenses and commits them.

## Project structure

```
.
├── .github/
│   └── workflows/
├── app/
│   └── src/
│       └── main/
│           ├── assets/
│           │   └── fonts/
│           ├── java/
│           │   └── app/
│           │       └── dinduy/
│           │           └── silkcurl/
│           └── res/
│               ├── drawable/
│               ├── drawable-nodpi/
│               ├── drawable-v31/
│               ├── layout/
│               ├── mipmap-anydpi-v26/
│               ├── values/
│               ├── values-night/
│               ├── values-night-v31/
│               ├── values-v31/
│               └── xml/
├── fastlane/
│   └── metadata/
│       └── android/
│           ├── en-US/
│           │   ├── changelogs/
│           │   └── images/
│           │       └── phoneScreenshots/
│           └── id-ID/
│               └── changelogs/
├── fonts-licenses/
├── metadata/
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── LICENSE
├── README.md
└── settings.gradle.kts
```

- [app/src/main/java/app/dinduy/](app/src/main/java/app/dinduy/): all Kotlin code in one package, with files named by feature (`Editor*`, `Ink`, `Pdf*`, `Audio*`, `Reminder`, `Store`).
- [app/src/main/java/app/dinduy/silkcurl/](app/src/main/java/app/dinduy/silkcurl/): the embedded PlayLikeCurl engine (Java, MIT header in each file) and the Kotlin glue in `SilkCurl.kt`.
- [app/src/main/res/xml/](app/src/main/res/xml/): backup and data extraction rules, FileProvider paths and widget definitions.
- `res/*-v31` and `res/*-night*`: widget colors for Android 12+ dynamic color and dark mode.
- [fastlane/metadata/android/](fastlane/metadata/android/): store listing in English and Indonesian. Changelogs are named by `versionCode`.
- [metadata/](metadata/): F-Droid build recipe.
- [.github/workflows/](.github/workflows/): the APK and release build, and the one-time font preparation.

The app also creates these folders at runtime:

```
filesDir/
├── audio/        # voice recordings
├── img/          # photos and covers
├── models/       # Whisper models
├── ocr/
│   └── tessdata/ # OCR language data
├── pdf/          # imported PDFs
└── secure/       # encrypted Groq key
cacheDir/
├── lp/           # link preview thumbnails
└── pdfx/         # PDF export temp files
```

## License

[GNU GPL v3](LICENSE). Code in `app/src/main/java/app/dinduy/silkcurl/` is MIT. Fonts are under their own licenses.

## Support

If Dinduy is useful to you, you can support development on [Ko-fi](https://ko-fi.com/ilhamdanials).
