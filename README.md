# Xiaomi Hotspot Doctor

Fixes the Xiaomi HyperOS bug where the **5 GHz mobile hotspot will not turn on**, and lets you switch
between **5 GHz and 2.4 GHz** from Quick Settings tiles. The app also sets the **hotspot name**, lists the
**connected devices**, and turns on **USB tethering**, automatically if you want, when a computer is plugged in.

Tested on a Xiaomi 17, HyperOS 3 (OS3.0.335), Android 17 (API 37).

## The problem

When you pick 5 GHz in HyperOS's hotspot settings, it saves "5 GHz, automatic channel". The firmware can't
start the hotspot in that mode, so the hotspot shows **Error** and stays off. Saving *any* change on the
"Set up hotspot" screen (name, password, band) resets it to that broken mode.

A hotspot pinned to a **fixed 5 GHz channel** (149 by default) starts fine. This app pins the channel for you.

## Status-bar band indicator

While the hotspot is on, the app shows an ongoing notification whose small **status-bar icon** (up by the
clock/battery) is a hotspot broadcast symbol with the band number — **5** or **2.4** — so you can see the
active band at a glance. It updates automatically and disappears when the hotspot turns off.

On HyperOS, for the status bar to show the band icon (instead of the app's colored icon or a "…"), set
**Settings → Notifications & Control Center → Status bar → Notification icons** to show individual icons
(equivalently `settings put system status_bar_notification_style 1`). The app self-grants its notification
permission via Shizuku.

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

### Hotspot name

Change the name in the app's **Hotspot name** field and tap **Save name**. Don't use the HyperOS "Set up
hotspot" screen for this, because saving there resets the band to the broken 5 GHz auto-channel mode. The app
keeps the band, channel and password. If the hotspot is on, it restarts with the new name, and devices
have to reconnect to it. Names can be 1-32 bytes long, so non-Latin letters count as 2-3 bytes each.

The last 10 names you used appear as chips under the field. Tap one to switch the hotspot back to that name,
or long-press it to remove it from the list.

### Password

**Show** and **Copy** the hotspot password, or type a new one and tap **Change**. Changing it here keeps the
band, channel and security type; the HyperOS screen resets the band to the broken 5 GHz auto-channel mode.

### Live status, speed and data used

The status card at the top is colored by state: violet for 5 GHz, teal for 2.4 GHz, grey when off and red when
something is wrong. It shows the name, channel, frequency, number of devices, live download and upload speed,
and the data used since sharing started.

### Appearance

Choose **Auto**, which follows the phone's dark mode, **Light** or **Dark**.

### Connected devices

The app lists the devices using the phone's internet, over Wi-Fi or USB, with each device's name (from
DHCP), IP address and MAC address. While the app is open, the list refreshes every 5 seconds.

### Share internet over USB

This is the same as **Settings → Connection & sharing → USB tethering**:

- **USB tethering ON/OFF** shares the phone's internet with the computer over the cable.
- **Turn on automatically when a computer is plugged in** keeps a small, silent notification and turns USB
  tethering on each time you plug the phone into a computer. A plain charger doesn't trigger it. If you turn
  USB tethering off while the cable stays in, it stays off until the next time you plug in.

## How it works

`Helper.java` runs as the shell user, through Shizuku, via `app_process` with the APK as its class path. It:

1. stops Wi-Fi tethering if it's running (`ITetheringConnector.stopTethering`),
2. writes the SoftAp configuration with `setChannel(channel, BAND_5GHZ)` or `setBand(BAND_2GHZ)`
   (`IWifiManager.setSoftApConfiguration`),
3. starts tethering again, so internet sharing works normally.

The other helper commands use the same calls:

- `name` sets the name with `SoftApConfiguration.Builder.setWifiSsid`. The name is passed as UTF-8 hex, so no
  shell quoting is involved.
- `usb on|off` starts or stops `TETHERING_USB`.

The connected-devices list comes from the tethered interfaces in `dumpsys tethering`, the ARP table
(`/proc/net/arp`) and the DHCP server log in `dumpsys network_stack`, which provides the host names.

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
| `src/com/hotspotband/MainActivity.java` | App screen: status, name, band, connected devices, USB |
| `src/com/hotspotband/UsbAutoService.java` | Turns on USB tethering when a computer is plugged in |
| `src/com/hotspotband/BootReceiver.java` | Restarts the USB watcher after a reboot or app update |
| `libs/shizuku-*.jar` | Shizuku client library 13.1.5 (Apache-2.0) |
