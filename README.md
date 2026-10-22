# JpedroToolkit

Android app (Kotlin, Jetpack Compose) that uses Shizuku and/or root to manage phones, styled after jpedrothejo.github.io.

## Pages

- **Home**: Shizuku and root status with request buttons, connected phones, links to every tool
- **Device**: requests root, looks for Magisk/KernelSU/APatch managers, reads Knox warranty bit and device info
- **Flash**: odin4 with BL/AP/CP/CSC/USERDATA slots, Download mode phone scan, reboot/validate/erase options
- **Install**: one or many APKs, split sets, downgrade and grant-all options, on this phone or a connected one
- **Power**: reboot, recovery, bootloader, Download mode, fastbootd, EDL, soft reboot, shutdown
- **Terminal**: phone shell or raw adb, with history, stop and clear
- **Settings**: theme, roundness, card opacity, bold text, page bar style and top/bottom position, app wallpaper with blur and dimming, access mode, flashing options, terminal font and history, binary import, reset

## Build

Open the folder in Android Studio (Ladybug or newer, JDK 17) and let it sync, then run the `app` configuration. Android Studio creates the Gradle wrapper jar for you.

## Binaries

The app does not bundle odin4, adb or fastboot. In Settings under Binaries, import arm64 Android builds of the ones you need. They are copied to `/data/local/tmp/jpedrotoolkit` and added to PATH for every command.

## Notes

- Flashing and adb over USB need raw access to `/dev/bus/usb`, which normally requires root. With Shizuku only, adb over Wi-Fi (`adb connect ip:port` in the Terminal) is the reliable path.
- Without root, `Soft reboot` is disabled and power actions use `svc power`.
- The Terminal runs one command at a time and is not an interactive PTY.


## App wallpaper

The app starts with a built-in Windows 8 Metro-inspired blue wallpaper. In Settings → App wallpaper, choose an image from the phone to replace it; blur and dimming are available for a selected image. The wallpaper gallery from the website has been removed.

## Local device features without ADB

- The Device page displays basic information directly through Android's `Build` APIs. Root and Knox checks are displayed together with the basic device information and run when the Device page opens; use Refresh all device checks to rerun them.
- For installing an APK on the same phone, the app opens Android's built-in package installer. Android asks you to confirm; this is not a silent install. The simple local flow handles one APK at a time. ADB/Shizuku/root is still needed for advanced installs on another device or split APK sets.
