# Assumptions, corrections and accepted deviations

Reference used: Sharp's *EL-W506T / EL-W516T / EL-W516XG Operation Manual* (the PDF on sharp.ca) and the
*EL-W535TG / W516T Operation Guide*. The manual's separate "calculation examples" sheet and the product
photo were **not** available, and no real unit was at hand. Everything below is therefore either taken
from the manual text or is a stated guess. Items marked **(guess)** need a real unit to confirm (V-5).

## 1. Corrections to the requirements document, from the manual

### Section 3 (legends)
| Requirement text | Built as | Why |
| --- | --- | --- |
| D1 / D2 / D3 ALPHA: "formula memories" | No ALPHA function on D1-D3 | The manual has only definable memories D1-D3: STO + D key stores, the D key calls. |
| (x,y) 2ndF: "∠ and i" | 2ndF (x,y) is ∠; the (x,y) key itself types i. Both only in COMPLEX mode | The manual enters rectangular form as `x + y i` and polar as `r 2ndF-∠ θ`; `i` is a mode-specific (grey) legend. |
| D°M′S 2ndF: "←→DEG" | Drawn as `⇔DEG` | Same function, one glyph. |
| DRG▸ listed as a menu (K-11) | Not a menu: each press converts the value to the next unit and switches the unit | Manual, "Angular Unit Conversions": "Each time ... is pressed, the angular unit changes in sequence." |

All other legends are exactly the table in section 3. They could not be checked against the photo.

### Section 7 (modes)
| Requirement | Built as | Why |
| --- | --- | --- |
| "normal, stat, drill, complex, matrix, list and equation"; M-8 List mode | MODE 0-8: NORMAL, STAT, TABLE, COMPLEX, EQUATION, MATRIX, VECTOR, DISTRIBUTION, DRILL. **There is no List mode.** | That is the manual's mode list. "List" appears only on the product page. |
| M-7 vectors under the matrix MATH menu | VECTOR is its own mode (MODE 6) with its own MATH menu | Manual. |
| (not in the document) | DISTRIBUTION mode (MODE 7): Normal pdf/cdf/inverse, Binomial pdf/cdf, Poisson pdf/cdf | Manual. Its seven worked examples are tests. |
| M-1 "32-bit integers shown as two's complement" | 10-digit complement in the current base (BIN 10 bits, OCT 30 bits, HEX up to FFFFFFFFFF = -1, PEN, DEC ±9999999999) | Sharp's N-base ranges. From memory of the examples sheet, not re-read: **(guess)**. |
| M-12 "2ndF MODE (CA) clears memories" | CA clears the entry, ANS, statistics and matrices/vectors; **A-F, X, Y, M and D1-D3 are kept**. M-CLR (2ndF ALPHA) clears memories | The manual's "Clearing the Entry and Memories" table. |
| MODE menu page 2 unknown | Page 2 holds MATRIX, VECTOR, DISTRIBUTION, DRILL | Manual's mode numbers; the page split is a **(guess)**. |

### Section 8 (memory, constants, conversions)
| Requirement | Built as | Why |
| --- | --- | --- |
| D-1 "D4 if the unit has it", "4 formula" memories | D1-D3 only; no formula memories | Manual. |
| D-4 constant values "must match the manual's table" | The 52 names and order are the manual's. **Values are CODATA 2014**; 49 entries now checked against the NIST archive | The manual prints the names and says the values follow CODATA 2014; it does not print the values. Hardware comparison remains open, see section 3. |
| D-5 conversion factors | 44 conversions in the manual's order; factors are the exact definitions (NIST SI guide). acre uses the US survey foot, as the manual notes | Same: names in the manual, factors not. |
| D-3 ANS | As written | |

## 2. Behaviour the manual does not pin down **(guesses)**

- **LCD drawing.** The 5x7 font, the small 3x5 font for exponents and limits, the shapes of √, Σ, ∫ and the
  cursor (a blinking vertical bar) are my own drawing, not the unit's ROM font.
- **Menu and screen layouts.** Menus put as many `n:NAME` items on a line as fit; the STAT data table,
  TABLE view, matrix editor (2 columns of 7 digits visible), equation and distribution forms are laid out to
  fit 16x4 characters but are not copied from the real screens.
- **MATH menu contents and order** in each mode, the STAT menu grouping, SET UP item labels.
- **Result format.** Trailing decimal point (`5.`), exponent shown as `ᴇ` with two digits (`2.ᴇ18`), fractions
  shown mixed first when above 1, limits for fraction/√/π results as stated in the manual.
- **Error screen**: `ERROR nn` plus a short name on line 2.
- **Constants** are typed into the expression as their symbol (`g`), not as digits.
- **Conversions** are a postfix item (`125yd►m`) at the priority the manual gives them.
- **Constant calculation (K)** applies only when the next entry is a bare number; ON/C drops the constant.
- **D1-D3.** With something on the entry line, STO + D stores the line (requirement K-4). With an empty line it
  stores the next function key (the manual's behaviour). The key, or RCL + key, replays it.
- **DISTRIBUTION results are cut, not rounded, after 9 decimals.** Three of the manual's examples only come
  out that way (true 0.04698531257 is printed as 0.046985312).
- `2^3^2` is 2^(3^2); `0^0` is ERROR 02; quartiles use the median-of-halves method.
- Solver's default dx is 0.00001. Simulation asks for variables in the order they appear.
- **Not emulated:** the K indicator, the exact wording of Solver's result screen, key auto-repeat.
- The power-off name (SET UP 7) shows for 1.5 s; contrast (SET UP 3) changes the ink strength.
- Keys act on release (needed for screen readers), not on touch-down.
- Restart persistence now preserves exact fractions/roots/π, result forms, and history answers. Older saves that already discarded exact values cannot recover that lost precision.

## 3. Open items of section 11

| Item | Status |
| --- | --- |
| Constants and conversion factors | 49 constants checked against the NIST 2014 archive; 13 conversion pairs and temperature anchors checked against references. AU, parsec and Celsius offset are outside the CODATA fixture. **Exact hardware matching remains open**, including all conversion digits. |
| Stat, Table, Equation, Matrix, Drill layouts | **Open.** Functions follow the manual; screen layouts are guesses. |
| List mode | Closed: the manual has no List mode. |
| Drill mode | Built from the manual (question ranges, counts, scoring). Layout is a guess. |
| Matrix and vector functions, MATH-menu items | Functions are the manual's list. Menu order is a guess. |
| Base-n | Decided: conversion keys work inside NORMAL, no separate mode (manual). |
| Error messages, cursor, "Cannot delete!" | Codes 01, 02, 03, 04, 07, 08, 10 and both alert messages are the manual's. Line-2 wording is a guess. |
| MODE menu page 2 | See section 1. |
| Small legends | See section 1. Needs the photo. |
| Line editor | Built (SET UP 2 1). |
| Key count | 47 keys plus 4 arrows, as in section 3. |

## 4. Naming

The repository keeps the working name SharpCalc. The app the user sees is **Quadline Calc**
(`app.quadline.calc`): no Sharp name, logo or model number appears in the app, and the About box
(the small "i" at the top right) says it is unaffiliated.

## 5. Reference checks added on 2026-11-11

`ConstantsReferenceTest` uses a checked-in fixture from the
[NIST CODATA 2014 archive](https://physics.nist.gov/cuu/Constants/ArchiveASCII/allascii_2014.txt).
The tolerance is relative 1e-10 because some published exact values have trailing ellipses.
Conversion checks use rounded reference factors from
[NIST SP 811 Appendix B.8](https://www.nist.gov/pml/special-publication-811/nist-guide-si-appendix-b-conversion-factors/nist-guide-si-appendix-b8)
with a relative 5e-7 tolerance. These checks establish agreement with those references,
not digit-for-digit agreement with a Sharp unit. No constant values were changed.
