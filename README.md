# Lyra

An offline music player for Android in the spirit of Musicolet, with two extra features.

## Features

**The basics (like Musicolet)**
- Plays local files, no internet and no ads
- Tabs: Songs, Folders, Albums, Artists, Playlists, Queues
- **Multiple queues at once**: each queue remembers its song and position, and you switch between them from the "Queues" tab
- Play next / Add to queue / New queue from here (long press or ⋮ on a song)
- Playlists with reordering
- Notification controls, lock screen, Bluetooth headphones (Media3)
- Pauses when headphones are unplugged
- Tags in any language: locale-aware sorting, and accent-insensitive search ("beyonce" finds "Beyoncé", "αλεξιου" finds "Αλεξίου")

**Extra 1: Artist aliases (aka)**
- On an artist page, tap ✏️ and link other names to the same artist. E.g. "Prince" = "The Artist Formerly Known as Prince", "Kanye West" = "Ye", "Λαυρέντης" = "Lavrentis".
- All songs appear under one name of your choice, with "Also known as: …"
- Automatic suggestions for similar names: "Beyonce" and "Beyoncé", "The Weeknd" and "Weeknd"
- Search finds the artist by any of their names
- Tags with multiple artists ("A feat. B", "A; B") are split correctly, and the separators are configurable in Settings. A name you've added to an artist, e.g. "Simon & Garfunkel", is never split.

**Extra 2: Back cover**
- In the player and on the album page: **tap the cover to flip it** to the back cover, with an animation
- **Long press** for full screen with pinch-zoom. Handy for reading tracklists and credits.
- Back cover sources, in this order:
  1. An image embedded in the file with type "Back cover" (MP3 ID3v2.2/2.3/2.4 and FLAC)
  2. An image file in the album folder or a subfolder (e.g. `Scans/`, `Artwork/`) whose name contains `back`, `rear`, `inlay`, `tray`, `πίσω` or `οπισθόφυλλο`

## Build

1. Open the `Lyra` folder in **Android Studio** (Ladybug or newer)
2. Wait for the Gradle sync. It downloads Gradle 8.11 and the libraries on its own.
3. Connect your phone with USB debugging and press ▶ Run

   or for an APK: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**

Requires Android 8.0+ (API 26). Gradle 8.11 needs JDK 17–23; it does not run on JDK 25.

## Structure

```
app/src/main/java/app/lyra/player/
├── data/
│   ├── MediaScanner.kt       MediaStore scanning
│   ├── Library.kt            albums/artists/folders, applying aliases, search
│   ├── AppStore.kt           storage (JSON): aliases, playlists, queues
│   ├── ArtworkLoader.kt      front & back covers (cached)
│   ├── EmbeddedPictures.kt   ID3v2/FLAC parser for typed pictures
│   └── Text.kt               multilingual sorting and search keys
├── playback/
│   ├── PlaybackService.kt    Media3 ExoPlayer + MediaSession
│   └── MusicController.kt    UI ↔ service bridge, multiple queues
└── ui/                       Jetpack Compose (Material 3, dynamic colors)
```

## Ideas for later
- Equalizer, sleep timer, gapless/crossfade
- Synced lyrics (.lrc)
- Back covers from M4A/OGG tags
- Drag & drop reordering in the queue
