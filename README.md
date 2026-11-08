# Poweramp-inspired (original free Android music player)

An original Java/Android Studio starter project designed for Android 6.0+ (minSdk 23), including offline MediaStore scanning, basic playback, queue-next/previous, shuffle, favourites, simple playlists, and Android audio effects (Equalizer, BassBoost, Virtualizer).

## Build
1. Install Android Studio with Android SDK Platform 35 and JDK 17.
2. Open this folder in Android Studio.
3. Let Gradle sync, then Build > Build APK(s).
4. Install the resulting `app-debug.apk` on the Vivo V5.

## Compatibility notes
- `minSdk 23` supports Android 6.0 Marshmallow.
- `targetSdk 28` is intentionally conservative for legacy storage compatibility on Android 6.0. Android 13+ permission handling is included as a best-effort path.
- Audio effects depend on the device's Android audio-effect implementation and may be unavailable or behave differently on some devices.

## Scope
This is a first working source-code foundation, not a feature-complete Poweramp replacement. Advanced artwork extraction, LRC lyric synchronisation, crossfade/gapless playback, persistent background playback + media notification/lock-screen controls, widget, sleep timer, and robust folder/genre navigation are not yet implemented. The project does not contain Poweramp's code or bypass its licence.
