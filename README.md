# Pixel Status Suite (Next-Gen Google Pixel & Android 17 Iconography)

[![Android](https://img.shields.io/badge/Android-12%20--%2014-brightgreen.svg?logo=android)](https://android.com)
[![Magisk](https://img.shields.io/badge/Magisk-v24%2B-blue.svg?logo=magisk)](https://github.com/topjohnwu/Magisk)
[![LineageOS](https://img.shields.io/badge/LineageOS-20%20--%2021-orange.svg)](https://lineageos.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Pixel Status Suite** is a unified, standalone status bar icon engine and management application for AOSP and LineageOS devices (tested on Android 14 / LineageOS 21).

All status bar vectors, dimensions, and iconography in this suite are **100% authentic Google Pixel system assets**, extracted directly from official Google Pixel factory images and decompiled `SystemUIGoogle.apk` / `framework-res.apk`.

---

## Live Status Bar Proof (Android 17 / Next-Gen Pixel)

<p align="center">
  <img src="screenshots/android17_statusbar_zoom.png" width="700" alt="Pixel Android 17 Status Bar Zoom" />
</p>

---

## Iconography Highlights

1. **4-Segment Cellular Signal (`ııll`)**:
   * Exact rounded vertical pill bars straight out of Google's `com.android.systemui.statusbar.pipeline.mobile.ui.compose.MobileIconKt` specification.
   * Heights: `5.0sp`, `7.5sp`, `10.0sp`, `12.0sp`. Width: `2.5sp`, Gap: `2.0sp`.
   * Unlit bars rendered with Google's authentic `fillAlpha="0.45"`.

2. **Segmented Wi-Fi Arcs**:
   * Authentic curved arcs with solid bottom dot extracted directly from `SystemUIGoogle.apk` (`ic_wifi_0.xml` through `ic_wifi_3.xml`).

3. **Android 17 Pill Battery Meter (`[ 94 ] ⚡`)**:
   * Full capsule pill container with authentic Google Android Green (`#3DDC84`) during charging.
   * Bold percentage text inside the pill.
   * High-contrast companion lightning bolt attached on the outside right of the pill.
   * Automatic theme adaptation (Google Red `<= 15%`, neutral white/dark mode tinting when discharging).

4. **Complete Google Pixel SystemUI Series**:
   * 33 authentic status bar vector XMLs pulled directly from Pixel `SystemUIGoogle.apk` (Airplane Mode, Dual-Bell Alarm, Roaming, DND, Bluetooth, Hotspot, VPN, Silent, Vibrate, etc.).

---

## Downloads & Releases

- **Flashable Magisk Module**: [`releases/Pixel-Status-Suite-Magisk.zip`](releases/Pixel-Status-Suite-Magisk.zip)
- **Standalone Manager APK**: [`releases/PixelStatusSuite.apk`](releases/PixelStatusSuite.apk)

---

## Technical Architecture

* **Overlay Runtime (RRO)**: `PixelSignalOverlay.apk`, `PixelWifiOverlay.apk`, `PixelSystemUIOverlay.apk` target `android` and `com.android.systemui` with priority `9999`.
* **SystemUI Hook (LSPosed / Zygisk)**: Injects `CapsuleBatteryDrawable` into `BatteryMeterView` without breaking LineageOS battery callbacks or crashing SystemUI.

---

## License

MIT License. Authentic Google Pixel vector assets and iconography copyright Google LLC.

