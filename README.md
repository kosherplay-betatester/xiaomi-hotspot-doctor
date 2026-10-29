# Xiaomi Hotspot Doctor

Fixes the Xiaomi HyperOS bug where the **5 GHz mobile hotspot will not turn on**, and lets you switch
between **5 GHz and 2.4 GHz** from Quick Settings tiles.

Tested on a Xiaomi 17, HyperOS 3 (OS3.0.335), Android 17 (API 37).

## The problem

When you pick 5 GHz in HyperOS's hotspot settings, it saves "5 GHz, automatic channel". The firmware can't
start the hotspot in that mode, so the hotspot shows **Error** and stays off. Saving *any* change on the
"Set up hotspot" screen (name, password, band) resets it to that broken mode.

A hotspot pinned to a **fixed 5 GHz channel** (149 by default) starts fine. This app pins the channel for you.

## Download

[`release/HotspotBand.apk`](release/HotspotBand.apk)

## Requirements

- [Shizuku](https://shizuku.rikka.app/) installed and running. The app asks for Shizuku permission once.
- After a phone reboot, start Shizuku again (Shizuku app → *Start via Wireless debugging*).

## Usage

Two Quick Settings tiles (add them from the app, or edit the control center):

| Tile | Tap when off / on the other band | Tap when lit |
|------|----------------------------------|--------------|
| hotspot + **5**   | Hotspot on at 5 GHz (fixed channel)  | Hotspot off |
| hotspot + **2.4** | Hotspot on at 2.4 GHz                | Hotspot off |

Only one band runs at a time; tapping the other tile switches over.
The **Hotspot Band** app does the same, shows live status, and lets you pick the 5 GHz channel
(149, 153, 157, 161, 36, 40, 44, 48).

If you used the HyperOS hotspot settings by mistake, just tap the 5 or 2.4 tile. It overwrites
the broken setting and keeps your hotspot name and password.

## How it works

`Helper.java` runs as the shell user, through Shizuku, via `app_process` with the APK as its class path. It:

1. stops Wi-Fi tethering if it's running (`ITetheringConnector.stopTethering`),
2. writes the SoftAp configuration with `setChannel(channel, BAND_5GHZ)` or `setBand(BAND_2GHZ)`
   (`IWifiManager.setSoftApConfiguration`),
3. starts tethering again, so internet sharing works normally.

## Building

Builds with the plain Android SDK tools, no Gradle needed.

- JDK 17+
- Android SDK: `platforms;android-37.2`, `build-tools;37.0.0`

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1            # builds HotspotBand.apk
powershell -ExecutionPolicy Bypass -File build.ps1 -Install   # and installs it via adb
```

`build.ps1` creates a signing keystore on first build. Keep it private and reuse it, because updates must be
signed with the same key.

## Source

| File | Purpose |
|------|---------|
| `src/com/hotspotband/Helper.java` | Privileged part: sets band/channel, stops/starts tethering |
| `src/com/hotspotband/Runner.java` | Launches the helper through Shizuku and parses its output |
| `src/com/hotspotband/BandTile.java` | Quick Settings tiles (`Tile5GHz`, `Tile24GHz`) |
| `src/com/hotspotband/MainActivity.java` | App screen |
| `libs/shizuku-*.jar` | Shizuku client library 13.1.5 (Apache-2.0) |
