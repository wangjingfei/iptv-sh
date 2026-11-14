# Android TV Player Plan

This repository does not contain an APK yet. The current services provide the backend and stable interfaces that a native TV client can consume.

## Product Scope

- Install and open as an Android TV / Google TV app with D-pad-first navigation.
- Import a playlist URL, initially the home EPG playlist.
- Browse channel groups, search, favorites, recent channels and a compact Now/Next guide.
- Full-screen playback, channel up/down, audio/subtitle selection and reconnect feedback.
- Support a configurable XMLTV URL; parse `tvg-id` to `channel id` exactly.
- Keep playback local: the client fetches HLS from NAS, while router and NAS do multicast acquisition.
- Do not store IPTV credentials or attempt to emulate the ZTE authentication process in the app.

## Backend Contract

- Playlist: `GET /playlist.m3u` from the EPG service. It declares `x-tvg-url` and contains a `tvg-id` for each stream.
- Guide: `GET /epg.xml`, XMLTV channel IDs match the playlist `tvg-id`.
- Stream: each M3U URL points to `iptv-hls` `/{channel-id}/index.m3u8` and its HLS segments.
- Health/diagnostics: `/healthz`, `/status.json`, and HLS `/api/status`.

## Suggested Implementation

- Native Android app in Kotlin with Compose for TV and AndroidX Media3 ExoPlayer. Android's current TV guidance recommends Compose for TV for modern TV interfaces, and Media3 documents HLS live playback with MPEG-TS segments.
- A repository layer handles M3U parsing, XMLTV parsing, local cache and refresh policy.
- Player is a dedicated full-screen route; guide and channel list use a two-pane TV layout and focus-safe D-pad transitions.
- Use a local database/cache for guide entries and preferences. Do not download or persist the MPEG-TS media beyond normal playback buffers.
- Add network timeout/retry, HLS error categorization, accessibility labels, remote-control tests and a low-memory device test.

Before implementation, verify current official Android TV and Media3 documentation and test the actual FnOS/udpxy HLS stream on target TV hardware. Do not hard-code local IPs; provide editable playlist URL and optional fallback EPG URL.

Official references:

- https://developer.android.com/training/tv/get-started
- https://developer.android.com/training/tv/get-started/create
- https://developer.android.com/media/media3/exoplayer/hls

## Milestones

1. Architecture spike: prove the NAS HLS URL plays on target Android TV hardware.
2. Playlist import and channel list with D-pad focus behavior.
3. XMLTV parse/cache and Now/Next guide.
4. Favorites, groups, reconnect and diagnostics.
5. Signed APK/AAB build, install/update path and living-room usability pass.

## Acceptance Checks

- Cold launch to channel list without touch input.
- D-pad can navigate list, guide and player controls without focus loss.
- A channel plays and changes channel without recreating backend containers.
- Current and next programme match XMLTV timestamps in local timezone.
- Missing EPG still permits playback; stale XMLTV is visibly treated as cached metadata.
