
# 🎵 Music Player

A high-performance, feature-rich music player for Android designed for audiophiles and power users. This app combines a powerful offline playback engine with intelligent online metadata enrichment and YouTube integration—all packed into a highly optimized, lightweight APK.

## ✨ Key Features

### 🎧 Superior Audio Control
*   **10-Band Equalizer:** Fine-tune your sound with professional presets (Rock, Pop, Jazz, etc.) or manual adjustments.
*   **Audio Enhancements:** Built-in Bass Boost, Virtualizer, and Reverb effects.
*   **Pro Playback Engine:** Supports Gapless playback, Silence Trimming (skips dead air at ends), and a Loudness Limiter to prevent clipping.
*   **Speed & Pitch:** Adjust playback speed (0.5x to 2.0x) and pitch in real-time.
*   **Per-Device Volume Memory:** Automatically remembers and restores your volume levels for specific Bluetooth devices, headphones, and speakers.

### 📚 Intelligent Library Management
*   **Smart Grouping:** Browse your collection by Artist, Album, Genre, Folder, or even **Decade**.
*   **Smart Playlists:** Dynamic auto-generating playlists including "On Repeat", "The Time Machine" (forgotten favorites), and "New Discoveries".
*   **Library Insights:** View your listening stats, including total time, top artists, and "Forgotten Gems" you haven't played in a while.
*   **Bulk Management:** Multi-select mode for batch editing genres or deleting multiple tracks at once.
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
*   **Adaptive Blur UI:** A dynamic "glass-morphism" player background that creates a custom immersive look based on the current album art.
*   **Auto UI Translation:** Automatically translates the entire app interface into your device's native language using Google Translate (can be toggled in settings).
*   **Synced Lyrics:** Full support for `.lrc` files and **Embedded Lyrics** inside audio tags. View scrolling, time-synced lyrics with optional real-time translation.
*   **Real-time Visualizer:** A responsive, rainbow-spectrum visualizer that reacts to your music.

### 🛠️ Extras
*   **Android Auto:** Full support for browsing and controlling your library from your car's head unit.
*   **Sleep Timer:** Fall asleep to music with a timer that features a smooth **Fade to Silence** ramp-down in the final 30 seconds.
*   **Home Screen Widget:** Control your music and see what's playing without opening the app.
*   **File Renaming:** Automatically rename messy filenames into clean `Artist - Title.mp3` formats based on metadata.

## 🛡️ Privacy & Transparency
This app respects your privacy. 
*   **Local-First:** All playback and library management happen on your device.
*   **External Services:** To provide a rich experience, the app communicates with the following third-party services for metadata, lyrics, and YouTube search:
    *   **MusicBrainz / iTunes / TheAudioDB:** For album art, artist bios, and track metadata.
    *   **LRCLib / Netease:** For synced lyrics.
    *   **AcoustID:** For audio fingerprinting/identification.
    *   **YouTube:** Search and extraction via the `NewPipeExtractor` library.
*   **No Tracking:** No analytics, trackers, or ads are included.

## 📜 License & Credits
This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for details.

### Libraries & Tools Used:
*   [Jetpack Compose](https://developer.android.com/compose) - Modern UI toolkit.
*   [Media3 / ExoPlayer](https://github.com/google/ExoPlayer) - High-performance audio engine.
*   [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) - Lightweight YouTube extraction.
*   [TagLib](https://github.com/kyant0/taglib-android) - Native metadata tagging.
*   [Room](https://developer.android.com/training/data-storage/room) - SQLite object mapping.
*   [Retrofit](https://github.com/square/retrofit) - Type-safe HTTP client.

## 🚀 Technical Highlights
*   **Size Optimized:** Aggressively optimized to stay under **15MB** by stripping redundant libraries and native binaries.
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
