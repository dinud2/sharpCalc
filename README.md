# Quadline Calc

A four-line scientific calculator for Android, inspired by the Sharp EL-W516XG.
It works offline, has no ads, and doesn't ask for any permissions. This is an
unofficial project and isn't affiliated with Sharp.

## Get it on your phone

You'll need an **Android phone running Android 7.0 or newer**. This APK won't work on an iPhone.

1. Open this page on your phone and [download Quadline Calc](https://github.com/dinud2/sharpCalc/raw/refs/heads/main/downloads/Quadline-Calc.apk).
2. When the download finishes, tap it. If you can't find it, open your phone's **Files** app and look in **Downloads** for `Quadline-Calc.apk`.
3. Your phone may ask whether your browser or Files app can install apps. Tap **Settings**, turn on **Allow from this source**, then go back.
4. Tap **Install**, then **Open**. You'll also find **Quadline Calc** in your app drawer afterward.

You don't need Android Studio, a computer, or a USB cable. If you've downloaded the
APK on your computer instead, send it to your phone or copy it over USB, then open
it from Files. You can turn off **Allow from this source** again after installing.

This is a debug build for trying the app. It has been tested in an Android emulator,
but hasn't been checked on a physical phone yet. If installation fails, check that
the download finished and that your phone meets the Android version requirement.

## A few useful keys

- **2ndF** selects the orange labels above the keys.
- **ALPHA** selects the teal labels.
- **MODE** switches between the calculator's nine modes.
- **2ndF**, then **MATH** opens settings.
- **ON/C** clears the current entry; **HOME** returns to normal calculation mode.

The small **i** in the top-right corner opens the About box.

## Build it yourself

The project has two parts: `engine/` holds the calculator logic and JVM tests,
and `app/` holds the Android interface.

With JDK 17 and Android SDK 34 installed:

```sh
./gradlew :engine:test
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.
You can also open the project in Android Studio Koala or newer and run the `app`
configuration. Opening it in Android Studio hasn't been verified yet.

If you prefer Docker:

```sh
docker build -t sharpcalc-build docker
docker/run.sh ./gradlew :engine:test assembleDebug lintDebug
```

To run the emulator checks on a Linux host with `/dev/kvm` available:

```sh
docker/emulator.sh docker/device-checks.sh
```

If you already use ADB, install your local build with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The APK in `downloads/` is the tested build shared with this repository. It isn't
updated automatically when the source changes. A production release signing setup
hasn't been added yet.

## What's been checked

As of November 11, 2026:

- All **104 JVM tests** pass.
- The debug APK builds, and Android lint reports **0 errors and 6 warnings**.
- Interaction checks pass at three emulator screen sizes, including dark mode,
  130% font size, and reopening the app after its process is stopped.

It isn't a fully verified replica of the physical calculator. Some menu layouts
and behavior are approximations, and physical-phone testing, TalkBack, and a
side-by-side check with a real Sharp calculator are still outstanding.

[VERIFICATION.md](VERIFICATION.md) has the test details,
[ASSUMPTIONS.md](ASSUMPTIONS.md) records the known differences, and
[PROGRESS.md](PROGRESS.md) lists what's left to do.
