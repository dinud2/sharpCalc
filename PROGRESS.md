# SharpCalc progress

Updated 2026-11-11. A debug build is ready for a friend to try on Android 7.0+.

## Verification

- 104 JVM tests pass; debug APK builds; Android lint: 0 errors, 6 warnings.
- API 34 emulator installs and runs at three resolutions. Arithmetic, fractions, statistics
  entry, errors, integration, and process-death checks pass at each resolution.
- Compact profile also checked with 130% font size and dark mode.
- 49 constants checked against NIST CODATA 2014, plus 13 conversion pairs and temperature anchors.
- See VERIFICATION.md for detailed coverage and limitations.

## Fixes in this pass

- Repaired emulator XML parsing; missing keys and failed display assertions now fail the run.
- Prevented saved results from being re-evaluated on restart (ANS expressions could change answers).
- Preserved exact memories, selected result forms and history answers across restart.
- Reduced crowded legend sizes and restored the complex-number i legend.

## Build and check

```
docker/run.sh ./gradlew :engine:test assembleDebug lintDebug
docker/emulator.sh docker/device-checks.sh
```

APK: app/build/outputs/apk/debug/app-debug.apk. Shareable copy: dist/Quadline-Calc-debug.apk.
Project archive: dist/SharpCalc-project.zip. These generated artifacts are ignored by Git.

## Still open

- Physical phone installation and TalkBack; opening in Android Studio.
- 130% display scaling (130% font scaling has passed).
- Full manual examples sheet, product-photo comparison, and comparison with a physical Sharp unit.
- Exact hardware constants/conversions and guessed menu layouts; see ASSUMPTIONS.md.
- Signed release build is deferred; the requirements explicitly permit a debug APK for v1.

Core app and engine are implemented, but it must not be described as an exact, fully verified replica.
