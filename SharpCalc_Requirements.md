# SharpCalc Requirements: EL-W516XG Emulator

Nov 11, 2026

## Brief for the implementing agent

Build the Android app this document specifies. Read the whole document before writing code.

- **Deliverable:** a zip of a complete Android Studio project (Kotlin, Jetpack Compose) with a README of build and install steps. If the Android SDK is missing, say the build was not verified; never claim it compiled.
- **Source of truth, in order:** this document, then the Sharp operation manual if attached, then the product photo. Do not invent behaviour, legends, constants or conversion factors. Where something is unspecified, record it in `ASSUMPTIONS.md` and ask the person if it blocks the work.
- **Traceability:** every requirement ID (P-, L-, K-, W-, E-, M-, D-, N-, V-) gets at least one test or a manual check. Name tests after the ID.
- **Build order:** engine and tests, key table, WriteView editor and drawing, menus, modes, Simulation and Solver, state saving. Finish and test each before the next.
- **Resumability:** keep `PROGRESS.md` current (decisions, done, next). The person is token-conscious and may hit a usage limit; after each stage, leave the project in a state that builds and note where to resume.
- **Keep logic out of the UI:** engine and key logic stay free of Android and Compose imports so they run in plain JVM tests.
- **Do not use Sharp logos or wordmarks.**
- **Before starting:** ask clarifying questions only for things that change what you build; then proceed without waiting.
- **If provided:** read the manual PDF first and correct section 3 (legends), section 7 (modes) and section 8 (constants, conversions) against it, listing every change.

## 1. Purpose and scope

SharpCalc is an Android app that behaves like the Sharp EL-W516XG WriteView scientific calculator: same keys, same display, same modes, same answers for the same key presses.

**"Exact" means:** for any key sequence the manual describes, the app produces the same display, result, menu and error as the real calculator. The reference is Sharp's EL-W506T / W516T / W516XG operation manual (manual PDF on sharp.ca). Where the manual is silent, behaviour is recorded as an assumption in section 11 and tested against a real unit when one is available.

**In scope:** every key, every shifted function (2ndF, orange), every ALPHA function (teal), WriteView entry and display, all modes listed in section 7, memory, constants and conversions, Simulation and Solver.

**Out of scope:** solar charging, battery, contrast wheel, the key-click sound of the hardware, and the Sharp logo and model wordmarks. The app is unofficial and must not use Sharp trademarks, so it carries a neutral name and an "unaffiliated" line on its About screen.

## 2. Platform and deliverable

The deliverable is an Android Studio project that builds to an APK. A finished APK cannot be produced in Claude's cloud workspace, because it has no Android SDK and cannot reach Google's Maven, the SDK download site or the Gradle plugin portal. The person building it runs the build in Android Studio.

| ID | Requirement |
| --- | --- |
| P-1 | Kotlin, Jetpack Compose (Material 3 for plumbing only; all calculator visuals are custom). |
| P-2 | minSdk 24, targetSdk 34, portrait only, phones first (tested on Pixel 8, API 34). |
| P-3 | No network permission, no ads, no analytics, no account. Works offline. |
| P-4 | `engine/` is pure Kotlin with no Android imports, so it compiles and tests on a plain JVM. |
| P-5 | Key logic (key table, menus, press handling) is Compose-free and unit-tested on a JVM. |
| P-6 | Project opens in Android Studio Koala or newer and syncs with no manual edits. Gradle wrapper files are included. |
| P-7 | Delivery: a zip of the full project, plus a README with build and install steps. `./gradlew assembleDebug` produces `app-debug.apk`. |
| P-8 | Debug APK is acceptable for v1. A signed release APK needs the person's own keystore and is a later step. |

## 3. Physical layout

The main screen reproduces the front of the calculator from the product photo: dark case with silver edge, a 4-line dot-matrix LCD above the keys, and 47 keys in the order below. The key table is read from the photo, and a few small legends are uncertain (marked in section 11).

| ID | Requirement |
| --- | --- |
| L-1 | LCD: 4 lines of 16 characters (5x7 dot font) plus a 2-D area for WriteView, green-grey background, dark blue ink. |
| L-2 | Annunciators on the LCD: 2ndF, ALPHA, HYP, STO, RCL, M, DEG/RAD/GRAD, FIX/SCI/ENG/N1/N2, W-VIEW, CPLX, BUSY, and scroll markers when content is hidden. |
| L-3 | Key rows, top to bottom: HOME / arrow cross / ON/C; 2ndF / MODE; ALPHA to BS (6 keys); hyp to D°M′S (6); yˣ to (x,y) (6); Exp to CHANGE (6); then four rows of 5, 5, 5 and 4 large keys (the = key is double width). |
| L-4 | Each key shows its primary legend on the key, its 2ndF legend above-left in orange, its ALPHA legend above-right in teal. Boxed orange legends mark the base-conversion keys. |
| L-5 | 2ndF and ALPHA keys light up while active and the matching annunciator shows. |
| L-6 | Layout scales to any phone width without clipping a legend; the keys keep the real calculator's proportions. |
| L-7 | Every key gives press feedback (pressed state, optional haptic). |

**Key legends** (primary / 2ndF orange / ALPHA teal):

| Key | 2ndF | ALPHA |
| --- | --- | --- |
| HOME | | |
| ON/C | OFF | |
| MODE | CA (clear all) | |
| ALPHA | M-CLR | |
| MATH | SET UP | |
| D1 / D2 / D3 | ALGB (Simulation) / SOLVER / | formula memories |
| BS | DEL | |
| hyp | arc hyp | NOT |
| sin / cos / tan | sin⁻¹ / cos⁻¹ / tan⁻¹ | AND / OR / XOR |
| π | logₐx | XNOR |
| D°M′S | ←→DEG | |
| yˣ | ˣ√ | A |
| √ | ³√ | B |
| x² | x⁻¹ | C |
| log | 10ˣ | D |
| ln | eˣ | E |
| (x,y) | ∠ and i | F |
| Exp | P.FACT | e |
| a/b | ab/c | |
| RCL | x³ | X |
| STO | int÷ | Y |
| M+ | M− | M |
| CHANGE | DATA | INS-D |
| 7 | RANDOM | |
| 8 | →rθ | STAT |
| 9 | →xy | MEMORY |
| ( | x′ | ∫dx |
| ) | y′ | d/dx |
| 4 | n! | CONST |
| 5 | nCr | CONV |
| 6 | nPr | |
| × | →HEX | Σ |
| ÷ | →BIN | Π |
| 1 | % | ◂ENG |
| 2 | GCD | ENG▸ |
| 3 | LCM | |
| + | →PEN | |
| − | →OCT | |
| 0 | MDF | |
| • | DRG▸ | |
| (−) | abs | NEG (below key) |
| = | →DEC | ANS (ENTER below key) |

## 4. Key behaviour

Every key must do its primary, 2ndF and ALPHA function exactly as the legend says. A legend that does nothing is a defect, not a gap.

| ID | Requirement |
| --- | --- |
| K-1 | 2ndF is one-shot: press it, then a key, and the orange function runs and 2ndF clears. Pressing 2ndF twice cancels it. |
| K-2 | ALPHA is one-shot in the same way. Pressing it after 2ndF is M-CLR (clear all memories). |
| K-3 | hyp is a prefix. hyp then sin/cos/tan gives sinh/cosh/tanh. 2ndF then hyp then sin gives the inverse hyperbolic function. |
| K-4 | STO then a variable key (A to F, X, Y, M) stores the current value or last answer. STO then D1, D2 or D3 stores the entry line as a formula. |
| K-5 | RCL then a variable key inserts it. RCL then D1 to D3 inserts the stored formula. |
| K-6 | M+ adds the value to M and M− (2ndF) subtracts it. M shows on the LCD while M holds a non-zero value. |
| K-7 | BS deletes left of the cursor. 2ndF BS (DEL) deletes at the cursor. Both are template-aware (section 5). |
| K-8 | ON/C clears the entry, or closes a menu. 2ndF ON/C is OFF. 2ndF MODE is CA (clears everything, keeps settings). |
| K-9 | Arrow cross: left and right move the cursor; up and down move between WriteView slots, then through the answer history. |
| K-10 | ENTER is the = key. It evaluates and keeps the answer in ANS. An operator pressed right after a result continues from ANS. |
| K-11 | Multi-level menus (MODE, SET UP, MATH, RANDOM, DRG▸, MEMORY, CONST, CONV) open on the LCD. A digit picks an item; up/down turn pages; BS goes back a level; ON/C leaves. |
| K-12 | HOME returns to Normal mode. |
| K-13 | Base conversion keys (2ndF + ×, ÷, +, −, =) convert the shown value to HEX, BIN, PEN, OCT and DEC. |
| K-14 | The answer history keeps the last 20 entries and can be recalled with the up arrow. |

## 5. WriteView editor

In WriteView, expressions are entered and shown as written on paper: stacked fractions, roots with a bar, raised exponents. Entry and results both use it. A Line editor, chosen in SET UP, shows the same expression as one line.

| ID | Requirement |
| --- | --- |
| W-1 | Templates: fraction (a/b), mixed fraction (ab/c), square root, nth root, power (yˣ, x², x³, x⁻¹, 10ˣ, eˣ), logₐx, absolute value, Σ, Π, ∫dx, d/dx. |
| W-2 | Templates nest to any depth the screen allows, for example a fraction inside a root inside an exponent. |
| W-3 | Empty slots show as boxes. The cursor sits in a slot; left and right walk through slots in reading order. |
| W-4 | Up and down move between numerator and denominator, and between the limits and body of Σ, Π and ∫. |
| W-5 | Pressing a/b after a number moves that number into the numerator. Pressing the nth-root key after a number makes it the index. |
| W-6 | BS on an empty template removes it. BS on a filled template steps the cursor in without destroying content. Some items cannot be deleted until their contents are removed, and the app shows "Cannot delete!" as the real unit does. |
| W-7 | Results display in WriteView when exact: fractions, mixed fractions, and roots where the real calculator shows them. |
| W-8 | Long expressions scroll sideways, with hidden-content markers at the edges; the cursor stays visible. |
| W-9 | The expression the user sees is the expression evaluated: every template must evaluate to the same value as its Line-editor form. |
| W-10 | Line editor: Σ and Π as `Σ(f, start, end[, step])`, ∫ as `∫(f, a, b[, n])`, d/dx as `d/dx(f, x[, dx])`, as given in the manual. |
| W-11 | Entries and results can be recalled from history and edited. |

## 6. Calculation engine

The engine evaluates with the manual's order of priority and shows up to 10 digits of a 15-digit internal value.

**Order of evaluation (first to last):**

1. Fractions
2. ∠ and engineering prefixes
3. Functions that precede their argument: x⁻¹, x², n!, %
4. yˣ and ˣ√
5. Implied multiplication of memory values
6. Functions followed by an argument: sin, cos, (−)
7. Implied multiplication of functions
8. nCr, nPr, GCD, LCM
9. ×, ÷, int÷
10. +, −
11. AND
12. OR, XOR, XNOR
13. Ending instructions: =, M+, M−, →DEG, →RAD, →GRAD, →r∠θ, →xy

| ID | Requirement |
| --- | --- |
| E-1 | Functions: sin, cos, tan and inverses; hyperbolic and inverse hyperbolic; log, ln, 10ˣ, eˣ, logₐx; square, cube, reciprocal, square root, cube root, nth root; n!, nPr, nCr; GCD, LCM; int, frac, abs, round-style MDF; %; π, e; RANDOM (random number, dice, coin, R.Int). |
| E-2 | Angle units DEG, RAD, GRAD, switched in SET UP or by DRG▸ (converts the value). Exact results at multiples of 90 degrees (sin 180 = 0, not 1.2E-16). |
| E-3 | D°M′S: degree-minute-second entry, and ←→DEG converts between decimal and sexagesimal display. |
| E-4 | Polar and rectangular: Pol/Rec, →rθ and →xy store results in X and Y. |
| E-5 | Prime factorization (P.FACT) of an integer greater than 2 and at most 10 digits. |
| E-6 | int÷ returns quotient and remainder (shown as Q and R). |
| E-7 | Display formats: NORM1, NORM2, FIX, SCI, ENG with the real calculator's thresholds; ◂ENG and ENG▸ shift the exponent by 3; fraction results as a/b or ab/c. |
| E-8 | Errors use the manual's messages and positions (syntax, math/domain, overflow, dimension, stat, "Cannot delete!"). After an error, left or right puts the cursor at the offending place. |
| E-9 | Answers keep full internal precision: ANS and stored values are not rounded to the display. MDF rounds the stored value to what is displayed. |
| E-10 | Numeric Σ, Π, ∫ and d/dx follow the manual's defaults: Σ/Π step 1, ∫ with n = 100 subintervals, d/dx with dx = 10⁻⁵ at x = 0, otherwise abs(x)×10⁻⁵. |
| E-11 | Normal probability P(t), Q(t), R(t) in STAT and in the MATH menu. |

## 7. Modes

Sharp lists seven modes for this calculator: normal, stat, drill, complex, matrix, list and equation. The MODE key opens a menu on the LCD (page 1 of 2 shows NORMAL, STAT, TABLE, COMPLEX, EQUATION). Simulation and Solver are not modes but are only available in Normal.

| ID | Mode | Requirement |
| --- | --- | --- |
| M-1 | Normal | Everything in sections 4 to 6. Base conversion (→HEX, →BIN, →PEN, →OCT, →DEC) works here, with AND, OR, XOR, XNOR, NOT, NEG on 32-bit integers shown as two's complement. |
| M-2 | Stat | Sub-modes: SD (single variable), a+bx, a+bx+cx², a·e^(bx), a+b·ln x, a·x^b, a+b/x, a·b^x. Data table with x, y and FRQ columns, up to 100 items, editable in place, insert a line with INS-D, delete a line with 2ndF BS. |
| M-3 | Stat results | STAT menu (ALPHA 8): statistical values (n, x̄, sx, σx, Σx, Σx², min, max, y equivalents, Σxy, Q1, Med, Q3), regression coefficients (r, a, b, c, R²), variable assignment, and estimates x′ and y′. |
| M-4 | Table | Enter Function 1 and optional Function 2, X Start (default 0), X Step (default 1; 0 or negative gives an error). Read-only table of X and ANS columns, up and down scroll, values to 7 digits with the full value at the bottom. |
| M-5 | Complex | i, ∠, conj, arg, real, imag, abs, powers and roots of complex numbers. Results in rectangular or polar form (SET UP). |
| M-6 | Equation | Simultaneous linear equations (2 and 3 unknowns), quadratic and cubic, with complex roots shown as a+bi. |
| M-7 | Matrix | Matrices with det, transpose, identity, dim, fill, random matrix, ref, rref, add, subtract, multiply, inverse. Vectors with dot product, cross product, angle, unit and abs (MATH menu). |
| M-8 | List | List data entry and operations as in the manual. Details to be taken from the manual before build. |
| M-9 | Drill | Practice mode as in the manual. Details to be taken from the manual before build. |
| M-10 | Simulation (2ndF D1) | Normal mode only. Expression with at least one variable (A to F, M, X, Y); the app asks for each value and shows the result; 2ndF D1 again re-runs with new values. Only = ends a calculation. |
| M-11 | Solver (2ndF D2) | Normal mode only. Expression containing x; Start value (default 0) and dx; Newton's method; result stored in X; the manual's ERROR 02 behaviour when it fails to converge. |
| M-12 | Mode switching | Changing mode clears the entry; settings, memories and formulas are kept; 2ndF MODE (CA) clears memories. Statistical data is erased on a sub-mode change. |

## 8. Memory, constants and conversions

| ID | Requirement |
| --- | --- |
| D-1 | Temporary memories A to F, X and Y; independent memory M; formula memories D1 to D3 (D4 if the unit has it). Capacities as listed on Sharp's product page: 8 temporary, 4 formula, 4 definable. |
| D-2 | The MEMORY list (ALPHA 9) shows every stored value over several LCD pages. |
| D-3 | ANS holds the last result and is recalled with ALPHA =. |
| D-4 | CONST (ALPHA 4) asks for a two-digit number, 01 to 52, and inserts the constant: the 52 constants of the manual, from speed of light (01) to standard atmosphere (52). Values must match the manual's table. |
| D-5 | CONV (ALPHA 5): enter a value, press CONV, then a two-digit number, 01 to 44, for the 22 metric/imperial pairs of the manual (length, area, mass, temperature, volume, energy, power, pressure, force). |
| D-6 | Memories and formulas survive leaving the app. ON/C does not clear them; M-CLR and CA do. |

Open point: the manual text available to the author lists constant and conversion names but not their values. Values must be checked against the manual's tables before the app is called exact (section 11).

## 9. Non-functional requirements

| ID | Requirement |
| --- | --- |
| N-1 | Responsiveness: a key press shows its effect within 50 ms; a typical calculation within 100 ms; Σ, ∫ and Solver show BUSY and stay cancelable with ON/C. |
| N-2 | State survives rotation, backgrounding and process death: entry line, cursor, mode, settings, memories, formulas, statistics data, history. |
| N-3 | Accessibility: every key has a content description that reads its primary function, and its 2ndF and ALPHA functions when active. Minimum touch target 44 dp where the layout allows. |
| N-4 | Works at font and display scales up to 130% without clipping legends. |
| N-5 | Size: APK under 10 MB, no third-party runtime libraries beyond AndroidX and Compose. |
| N-6 | Privacy: no permissions requested, no data leaves the device. |
| N-7 | Dark system theme does not change the calculator's own appearance; the app looks like the device in light and dark mode. |
| N-8 | Code is split so that adding a function means adding an entry in the key table and the engine, with a test. |

## 10. Verification and acceptance

The app is "done" when every requirement above has a passing test or a signed-off manual check. No requirement is accepted on the author's say-so.

| ID | Check |
| --- | --- |
| V-1 | Engine unit tests on a plain JVM, run on every change, covering arithmetic, trig, formatting, equations, statistics, matrices, base-n, table, drill, templates, logic and editor. |
| V-2 | Key-sequence tests on a JVM: every key's primary, 2ndF and ALPHA action is pressed and its result checked (section 3 table, one row per test). |
| V-3 | Manual examples: every worked example in the Sharp operation manual (key sequence and displayed result) becomes an automated test. |
| V-4 | WriteView checks: for each template, entry, navigation, delete and evaluation, plus a screenshot test of the LCD at three phone sizes. |
| V-5 | Side-by-side check against a real EL-W516XG on a fixed list of about 100 key sequences covering every mode. Differences are logged and fixed or recorded as accepted deviations. |
| V-6 | Layout check: screenshot of the app next to the product photo; every legend present, readable and in the right place. |
| V-7 | Install check: debug APK installs and runs on a Pixel 8 emulator (API 34) and on one physical phone. |

**Definition of done:** V-1 to V-7 pass, section 11 has no open item marked "blocks release", and the README's build steps work from a fresh unzip.

## 11. Open items and questions

These items need a source or a decision before the app can be called exact. Each is a gap in this specification, not in any implementation.

| Item | Gap | Blocks release |
| --- | --- | --- |
| Constants and conversion factors | Values must be taken from the manual's tables | Yes |
| Stat, Table, Equation, Matrix, Drill layouts | Real data-entry and table screens need the manual or more photos | Yes |
| List mode | Manual detail needed | Yes |
| Drill mode | Manual detail needed | Yes |
| Matrix and vector functions, MATH-menu items | Full item list and key sequences needed from the manual | Yes |
| Base-n | The real unit converts in Normal mode; whether to also offer a separate mode is open (decide before building Base-n) | Yes |
| Error messages, error cursor and "Cannot delete!" | Full message list and positions needed from the manual | Yes |
| MODE menu page 2 | The photo shows page 1 of 2 only | Yes |
| Small legends (for example ∠ and i on (x,y), CHANGE, D1 to D3 shifts) | Read from a small photo; some may be wrong | Yes |
| Line editor setting | Optional for v1 | No |
| Key count | Sharp's own site says 47 keys plus the 4 arrow keys; the table in section 3 was counted from the photo | No |
