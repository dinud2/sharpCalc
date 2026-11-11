# Verification: requirement to check

Automated checks are JVM tests in `engine/src/test/kotlin/sharpcalc/` (run `./gradlew :engine:test`;
104 tests, all passing on 2026-11-11). Test names start with the requirement ID.
"Manual" rows need a person; their state is given honestly.

| ID | Check | State |
| --- | --- | --- |
| P-1, P-2, P-3 | `TraceTest` "P-1 P-2 P-3 N-6 ..." (Compose, API 24-34, portrait, no permissions) | pass |
| P-4, P-5 | `TraceTest` "P-4 P-5 engine and key logic have no Android or Compose imports" | pass |
| P-6 | `TraceTest` "P-6 P-7 ..." (wrapper files, modules). Opening in Android Studio: manual | test pass; Android Studio not tried |
| P-7, P-8 | `docker/run.sh ./gradlew assembleDebug` produces `app-debug.apk` | pass (8.1 MB) |
| L-1 | `TraceTest` "L-1 LCD is 96 by 32 dots ..." | pass |
| L-2 | `KeyTest` "L-2 annunciators", plus K-1, K-2, K-6, K-13 tests | pass |
| L-3 | `TraceTest` "L-3 key rows ..." | pass |
| L-4 | `TraceTest` "L-4 legends of section 3" (data). Position and colour on screen: manual, see V-6 | data pass |
| L-5 | `TraceTest` "L-5 ..."; lit key border: manual on device | data pass |
| L-6, L-7 | Manual: screenshots at three sizes, press feedback on a device | see V-6 |
| K-1 ... K-14 | `KeyTest` tests named K-1 to K-14 (K-4/K-5 have two) | pass |
| W-1 ... W-11 | `WriteViewTest` tests named W-1 to W-11 | pass |
| E-1 ... E-11 | `EngineTest` and `KeyTest` tests named E-1 to E-11 | pass |
| M-1 | `EngineTest` "M-1 n-base", `KeyTest` "K-13 base conversion keys" | pass |
| M-2, M-3 | `KeyTest` "M-2 M-3 statistics ...", `EngineTest` "M-3 statistics" | pass |
| M-4 | `KeyTest` "M-4 table mode" | pass |
| M-5 | `KeyTest` "M-5 complex mode", `EngineTest` "M-5 complex" | pass |
| M-6 | `KeyTest` "M-6 equation mode", `EngineTest` "M-6 equations" | pass |
| M-7 | `KeyTest` "M-7 matrix mode", "M-7 vector mode", `EngineTest` "M-7 matrix" | pass |
| M-8 | Replaced by DISTRIBUTION mode (no List mode in the manual): `KeyTest` "M-8 distribution mode" | pass |
| M-9 | `KeyTest` "M-9 drill mode" | pass |
| M-10, M-11 | `KeyTest` "M-10 simulation ...", "M-11 solver ..." | pass |
| M-12 | `KeyTest` "M-12 mode change ...", "K-8 ..." | pass |
| D-1 | `TraceTest` "D-1 memories ..." | pass |
| D-2, D-3 | `KeyTest` "D-2 memory list", "D-3 ANS recall" | pass |
| D-4, D-5 | `KeyTest` "D-4 ...", "D-5 ..." (lists, entry, count). **Values checked against references as detailed below; not against a unit** | pass; values open |
| D-6 | `KeyTest` "D-6 N-2 state survives save and load", "K-2 M-CLR clears memories" | pass |
| N-1 | `TraceTest` "N-1 keys respond quickly and long calculations can be cancelled" | pass |
| N-2 | `TraceTest` "N-2 state round trip in every mode"; emulator process death: `docker/device-checks.sh` | pass |
| N-3 | `TraceTest` "N-3 ..." (data); TalkBack pass on a device: manual | data pass; TalkBack not tried |
| N-4 | Legend sizes ignore the system font scale by construction; 130 % check on a device: manual | pass on emulator; physical phone not tried |
| N-5 | `TraceTest` "N-5 no third-party runtime libraries"; APK 8.1 MB | pass |
| N-6 | `TraceTest` "P-1 P-2 P-3 N-6 ..." | pass |
| N-7 | Fixed colours, no theme lookup; dark-mode check: manual | pass on emulator; physical phone not tried |
| N-8 | `TraceTest` "N-8 ..." | pass |
| V-1 | 104 JVM tests | pass |
| V-2 | `TraceTest` "V-2 every legend does something", `KeyTest` "V-2 every key ..." | pass |
| V-3 | `TraceTest` "V-3 worked examples ...", `EngineTest` "E-11 ..." (7 distribution examples). **The manual's separate examples sheet was not available**, so most worked examples are not covered | partial |
| V-4 | `WriteViewTest`; LCD snapshots in `engine/build/snapshots/`. Three phone sizes: see V-6 | pass (JVM) |
| V-5 | Side-by-side with a real unit | **not done** (no unit) |
| V-6 | Screenshot next to the product photo | see below |
| V-7 | Install and run on a Pixel 8 emulator (API 34) and one physical phone | see below |

## Verification rerun, 2026-11-11

- `docker/run.sh ./gradlew :engine:test assembleDebug lintDebug`: **104 tests, zero failures;
  APK build passes; lint has zero errors and six warnings** (dependency updates, portrait lock,
  and backup rules). Reports are under the modules' ignored `build/` directories.
- `PersistenceTest`: ANS-dependent answers, exact memories, selected result format, history
  results, legacy state, and root/π/complex memories survive save/load. Three regression
  tests failed before the fix and pass after it.
- `ConstantsReferenceTest`: 49 constants compared to NIST CODATA 2014; 13 conversion pairs
  and Fahrenheit/Celsius anchors checked against NIST reference values. Details and sources
  are in `ASSUMPTIONS.md` section 5.
- `docker/emulator.sh docker/device-checks.sh`: **passes** at 720x1280 (320 dpi, font 130%,
  dark mode), 1080x2400 (420 dpi), and 1440x2560 (480 dpi), on API 34 / Pixel 7 AVD.
  This is not an actual Pixel 8 hardware profile.
- At each size, assertions check 2+3=5, 1/2+√8 decimal output, STAT entry mode, division-by-zero
  error, integral of X² from 0 to 3 = 9, unfinished entry after process death = 84,
  and completed ANS-dependent result remaining 10 after process death. No fatal exceptions.
- V-4: screenshots captured at all three sizes; exact-result screenshots visually inspected.
  These are smoke checks, not pixel-difference regression tests.
- V-6: legends adjusted to avoid overlap, missing complex `i` legend restored. Product-photo
  comparison remains open. V-7: install/launch on API 34 emulator passes; physical phone remains open.
- Artifacts: `dist/device-checks.log`, `dist/build-checks.log`, and `dist/shots/{compact,standard,large}/`.
  Earlier screenshots from the broken script were not valid interaction evidence.
- TalkBack, actual display-scale setting at 130%, Android Studio opening, all manual examples,
  and comparison with a real calculator remain unverified. Font scaling at 130% was tested.
