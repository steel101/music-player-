# 🎵 Music Player

A high-performance, feature-rich music player for Android designed for audiophiles and power users. This app combines a powerful offline playback engine with intelligent online metadata enrichment and YouTube integration—all packed into a highly optimized, lightweight APK.

## ✨ Key Features

### 🎧 Superior Audio Control
*   **10-Band Equalizer:** Fine-tune your sound with professional presets (Rock, Pop, Jazz, etc.) or manual adjustments.
*   **Audio Enhancements:** Built-in Bass Boost, Virtualizer, and Reverb effects.
*   **Pro Playback Engine:** Supports Gapless playback, Silence Trimming (skips dead air at ends), and a Loudness Limiter to prevent clipping.
*   **Speed & Pitch:** Adjust playback speed (0.5x to 2.0x) and pitch in real-time.

### 📚 Intelligent Library Management
*   **Smart Grouping:** Browse your collection by Artist, Album, Genre, Folder, or even **Decade**.
*   **Library Insights:** View your listening stats, including total time, top artists, and "Forgotten Gems" you haven't played in a while.
*   **Dynamic Playlists:** Create and manage custom playlists.
*   **Folder Blacklisting:** Hide specific folders (like ringtones or game audio) from your library.

### 🔍 Automated Metadata & Artwork
*   **Auto-Enrichment:** Automatically fetches missing high-quality album art and metadata from MusicBrainz and iTunes.
*   **Artist Bios:** Deep-dive into your music with artist biographies and discographies fetched from TheAudioDB.
*   **AcoustID Fingerprinting:** Identify unknown files by their unique audio signature, even if they have no tags.
*   **Tag Editor:** Manually edit titles, artists, and genres, with the option to write those changes directly to the physical file on your storage.

### 🎥 YouTube Integration
*   **Integrated Search:** Search the millions of tracks available on YouTube directly within the app.
*   **Streaming & Downloads:** Preview YouTube tracks instantly or download them to your local library for offline use.
*   **High-Quality Extraction:** Automatically finds the best audio streams and fetches high-res thumbnails.

### 🎨 Beautiful & Functional UI
*   **Dynamic Theming:** The entire app UI adapts its colors to match the artwork of the currently playing song.
*   **Synced Lyrics:** Full support for `.lrc` files. View scrolling, time-synced lyrics, or use the **built-in Lyrics Editor** to sync them yourself.
*   **Real-time Visualizer:** A responsive, rainbow-spectrum visualizer that reacts to your music.
*   **Shared Transitions:** Smooth, modern animations when moving between lists and the full player view.

### 🛠️ Extras
*   **Sleep Timer:** Fall asleep to music with a timer that can optionally wait for the current song to finish.
*   **Home Screen Widget:** Control your music and see what's playing without opening the app.
*   **File Renaming:** Automatically rename messy filenames into clean `Artist - Title.mp3` formats based on metadata.

## 🚀 Technical Highlights
*   **Size Optimized:** Aggressively optimized to stay under **10MB** by stripping redundant libraries and native binaries.
*   **Architecture:** Built using Modern Android Development (MAD) practices with Jetpack Compose, Kotlin Coroutines, and Room Database.
*   **Media3/ExoPlayer:** Powered by Google's latest media engine for rock-solid stability and format support.

## 📋 Requirements
*   **Android Version:** Android 14 (API 34) or higher.
*   **Permissions:** Requires Storage access (to scan music) and Record Audio (for the visualizer logic).

---

### 📥 How to build
To generate the smallest possible version of this app for your device:
1. Open the project in Android Studio.
2. Select the `release` build variant.
3. Run `./gradlew assembleRelease`.
4. Your optimized APK will be in `app/build/outputs/apk/release/`.
