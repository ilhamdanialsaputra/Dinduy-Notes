<div align="center">

<!-- Put your main image here, for example: -->
<!-- <img src="docs/screenshots/banner.png" alt="Dinduy" width="100%"> -->

# Dinduy

**Notes that stay neat, quick, and easy on your thumb.**
Everything is stored on your own phone. No account, no server.

<br>

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Material 3](https://img.shields.io/badge/Material%203-6750A4?style=for-the-badge&logo=materialdesign&logoColor=white)

![Min SDK](https://img.shields.io/badge/Min%20SDK-26-informational?style=flat-square)
![Version](https://img.shields.io/badge/Version-1.3-blue?style=flat-square)
![Privacy](https://img.shields.io/badge/Data-Stored%20Locally-success?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

[Bahasa Indonesia](README.md) | **English**

</div>

<br>

---

## Overview

Dinduy is a notes app that brings a lot together in one place: writing, doodling, reading PDFs, recording voice, and setting reminders. It follows the Material You look, and almost everything can be done with one thumb.

---

## Features

### Writing

- Rich text: **bold**, *italic*, underline, strikethrough, highlight, and code
- Headings, bullet lists, numbered lists, lettered lists, and checklists
- Quotes in several styles: Note, Tip, Important, Warning, Caution, Idea, Definition
- Code blocks, tables, and dividers
- Text alignment and paragraph indents
- Markdown-style typing, for example type `- ` for a bullet or `# ` for a heading
- Search inside a note, plus undo and redo

### Photos and covers

- Insert photos from the gallery or straight from the camera
- Resize, crop, and arrange several photos in one row
- Text flows automatically beside a photo
- Add a cover image, and the note's colors follow the photo
- Scan text from a photo (OCR) without internet, then copy the result

### Drawing

- Pen, highlighter, eraser, and a Select tool
- Works with a finger or a stylus
- Shapes are tidied up automatically (line, circle, square, triangle) when you hold the end of a stroke for a moment
- Drawings stick to the text, so they move along when the text changes

### PDF reader

- Open PDFs from other apps through Open with or Share
- Scroll mode or page mode, including a 3D page-curl effect
- Search words, select and copy text, and a dark page mode
- Draw directly on top of a PDF

### Voice notes

- Record audio inside a note, and it keeps going while the app is minimized
- Quick record: swipe the New button to the left to start recording right away
- Turn speech into text in two ways:

| Method | Strengths | Notes |
|---|---|---|
| Online (Groq) | Fast and most accurate | Needs your own Groq API key |
| Offline (Whisper) | Runs on your phone without internet | Model is downloaded once |

- Language: auto, Indonesian, or English
- Optional auto format (punctuation, bold, italic) without changing your words

### Reminders

- Add a reminder to any note, with a date and time
- Can repeat: daily, weekly, monthly, or yearly
- From the notification, pick Done, Snooze 1 hour, or Tomorrow morning
- Alarms are set up again automatically after the phone restarts

### Organizing notes

- Pin important notes to the top
- Labels to group notes
- Archive and Trash (Trash is emptied automatically after 30 days)
- 1 or 2 column layout
- Press, hold, and drag a card to set your own order
- Swipe a card right to archive, left to delete

### Sharing and export

- Share a note as a `.dinduy` file, with photos, drawings, audio, and PDFs included
- Export to PDF (A4) with a preview and adjustable text size
- Save or copy as Markdown text
- Import `.md`, `.txt`, and PDF files

### Appearance

- Light, dark (pure black for AMOLED screens), or follow the phone setting
- Material You dynamic color (Android 12 and up) or pick your own color
- Monochrome mode
- Font choices: Default, Roboto Flex, Times New Roman, Reenie Beanie
- Home screen widgets for quick notes and a notes list

---

## Privacy

| | |
|---|---|
| **Storage** | All notes live on your phone. There is no account and no Dinduy server. |
| **Internet** | Only used when you ask for it: downloading the offline model, Groq transcription, link previews, and checking for updates. |
| **API key** | Stored encrypted on the device (Android Keystore). It never goes into backups, shared files, or the code. |
| **Backup** | A single `.dinduy` file that you save wherever you choose. |

---

## Quick start

1. Tap **New** to create a note.
2. Hold **New** for the add menu (including voice note), or pull it up to start writing right away.
3. Hold a note card to select, delete, or duplicate it.
4. Swipe the bar above the keyboard for formatting tools.
5. Tap the down arrow to enter reading mode.
6. Tap the three dots inside a note for labels, cover, PDF export, and sharing.

---

## Backup and restore

Open **Settings**, then the **Backup** section. Save or share one `.dinduy` file containing all your notes, photos, audio, and PDFs. When restoring, choose **Merge** (add to your current notes) or **Replace all** (replace everything).

---

## Requirements

- Android 8.0 (API 26) or newer
- An arm64-v8a device, because the offline transcription engine only supports this architecture

---

## Building from source

Dinduy is written in Kotlin with Jetpack Compose and built with Gradle like a regular Android project.

- Fonts are downloaded automatically during the build. If that fails, the app falls back to system fonts.
- Without a keystore, the APK is signed with the debug key. To update without uninstalling first, provide a keystore through these environment variables:

```
KS_FILE   KS_PASS   KS_ALIAS   KEY_PASS
```

---

## Updates

Check for the latest version from the **Info** menu. Updates can be downloaded and installed right from inside the app.

---

## License

Released under the [MIT License](LICENSE).

<div align="center">

<br>

Made so that taking notes feels good.

</div>
