# Workbook and sample inventory

This inventory describes files currently available in the local `docs/` folder.
Those source documents contain customer and commercial data, so `docs/*.pdf` and
`docs/*.xlsx` are excluded from Git. Keep the workbooks and invoices on the local
PC; do not copy them into the public repository.

The sheet names, used ranges, formulas, merged cells, and print settings below were
read from the supplied workbooks. Generators copy local templates, preserve their
formatting, and replace only order-specific values. Customer documents remain local.

## `BON_DE_FABRICATION_ZEYNA.xlsx`

Candidate manufacturing-order workbook; four tabs, one for each ZEYNA color.

| Sheet | Used range | Merged cells | Formula cells |
|---|---|---:|---:|
| `zeyna blanco` | `A1:X60` | 57 | 158 |
| `zeyna negro` | `A1:Q60` | 57 | 158 |
| `zeyna madera (bois)` | `A1:Q60` | 57 | 158 |
| `zeyna marron` | `A1:Q60` | 57 | 158 |

All four sheets use the same 57 merged ranges:

`A51:C51`, `A58:F60`, `K58:Q58`, `K59:Q60`, `A46:C46`, `D46:E46`,
`H47:J47`, `A49:Q49`, `A50:C50`, `D50:H50`, `I50:L50`, `P28:P29`,
`Q28:Q29`, `B30:B45`, `C30:C36`, `D37:E37`, `C38:C39`, `D40:E40`,
`D42:E42`, `C43:C44`, `D45:E45`, `D12:F12`, `D25:F25`, `I26:K26`,
`M26:O26`, `A28:A29`, `B28:B29`, `C28:C29`, `D28:E28`, `F28:F29`,
`G28:G29`, `H28:J28`, `K28:K29`, `L28:N28`, `O28:O29`, `D10:G10`,
`H10:J10`, `K10:M10`, `N10:O10`, `P10:Q10`, `D9:G9`, `H9:J9`,
`K9:M9`, `N9:O9`, `P9:Q9`, `A6:B6`, `C6:M7`, `N6:Q6`, `A7:B7`,
`N7:Q7`, `A1:B4`, `C1:N2`, `O1:Q1`, `O2:Q2`, `C3:N4`, `O3:Q3`,
`O4:Q4`.

The detail-table headers are on row 12, from `A` to `Q`: customer reference,
article description, barcode, finished width, dimension separator, finished
height, ordered quantity, inner pack, outer pack, packs per pallet, ordered
packs, pallet count, net weight per item, pack weight, total weight, glass
allowance, and observations. The workbook's header spelling and accent marks
should be taken from the cells themselves when generating; do not add columns.

Observed per-order cells are the client, color-specific order title, and order
date on row 6; client order number and loading-date placeholder on row 7; and
the requested quantity cells in the detail table (column `G`, rows 13–24 in the
provided copy). Static article, packaging, dimensions, and weight data also
occupy the detail table; do not replace these with guessed values.

Formula cells include the pack counts in `I`, `K`, and `L`; weight calculations
in `N` and `O`; and derived area/glass calculations in `P` and `Q`. For example,
the data-row formulas include `I13=H13*J13`, `K13=IF(H13>0,G13/H13,0)`,
`N13=M13*H13*1.04/1000`, and `O13=N13*K13`. The 158 formulas per sheet also
include summary/derived calculations below the detail rows. These formulas are
part of the source workbook and must remain intact.

Print layout is landscape, A4 (`paperSize=9`), scaled to 70%, horizontally
centered, with 0.3-inch left/right/top/bottom margins. Preserve the existing
print settings when writing.

## `fiche_de_ramassage_ZEYNA.xlsx`

The available picking-sheet candidate; one `RESTAER` sheet, `A1:N27`, with 26
merged cells and 39 formulas.

| Row | Observed column headers / purpose |
|---|---|
| 2 | Client, customer order number, order date |
| 4 | Dimensions across `B:M`, total in `N` |
| 5–9 | Backing, stand, hanging, and face/glass specification by dimension |
| 10–13 | Quantities by ZEYNA color and dimension; total by row in `N` |
| 14 | Totals by dimension and grand total |
| 17–18 | Carton quantities derived from frame quantities |
| 20 onward | Supplies / accessory sections |

Formula cells are in `B:M` for the derived carton values and `N` for row and
section totals; for example, `N10=SUM(B10:M10)`, `N14=SUM(N10:N13)`, and
`B18=SUM(B10:B13)/6`. Preserve the source formulas.

The complete merged-cell list is:

`F27:I27`, `K2:N2`, `F20:I20`, `F21:I21`, `F22:I22`, `F24:I24`, `A1:N1`,
`B21:C21`, `J24:K24`, `A16:N16`, `B23:C23`, `J27:K27`, `J26:K26`,
`F2:J2`, `J20:K20`, `A2:E2`, `J25:K25`, `J22:K22`, `N4:N9`, `A20`,
`B22:C22`, `J21:K21`, `B20:C20`, `F25:I25`, `F26:I26`.

Print layout is landscape A4 at 70%, horizontally centered, with approximately
0.51-inch left/right margins and 0.59-inch top/bottom margins.

## `DA SVERRE RESTAER 23-10-2026.xlsx`

One `RESTAER (2)` sheet, `A1:P38`, with 19 merged cells and 43 formulas.
This is a filled history example, not a blank template.

The table headers are on row 6, columns `A:P`: date, client, order quantity/
remark, frame description, previous remaining quantity, need, gap, facing
dimensions, proposed units per sheet, sheet number, sheets ordered, sheet
price, total ordered pieces, total price, a further remaining-quantity
calculation, and a final column. The provided labels have spelling and
abbreviation inconsistencies; reproduce the template text rather than
silently renaming columns.

Per-order/history values occupy rows 7–23. The DA generator clears these sample
rows and their vertical merges before writing a new order, so the filled history
example's customer data is not carried into the generated workbook. It writes
only dimensions present in the order and recalculates the `G`, `M`, and `O`
summary formulas. `O` (`E + M - F`) is the inventory after the order; `E`
shows current app stock aggregated across the order's colors, while the
generated cutting plan uses full order quantities and does not subtract stock.
Generating a workbook does not create stock movements. Initial stock values are
test data (999,999 per supported item).

The `CUTTING_LAYOUTS` worksheet lists each planned piece's plate number,
dimension, coordinates, and oriented width/height in millimeters. The confirmed
rules are 93x64-cm plates, zero edge margin and kerf, 90-degree rotation
allowed, and dimensions kept in the order's original sequence. Different plates
may use different size mixes. A best-short-side-fit heuristic creates valid
layouts but does not guarantee the minimum number of plates.

Merged cells:

`A26:E27`, `A28:E29`, `A30:E31`, `A32:E33`, `G37:I38`, `I27:I29`,
`J27:M29`, `A24:D24`, `A1:B3`, `D1:M3`, `N1:P3`, `A7:A23`, `B7:B23`,
`H7:H23`, `P7:P23`, `L7:L23`, `N7:N23`, `J7:J23`, `K7:K23`.

Print layout is landscape A4, scaled to 35%, with 0.25-inch left/right and
0.75-inch top/bottom margins.

## `FICHE DE RAMASSAGE RESTAER 20261020.xlsx`

This is a filled picking workbook with two historical-order sheets and one
empty sheet, **not** the blank generation template above.

| Sheet | Used range | Merged cells | Formula cells |
|---|---|---:|---:|
| `REASTER` | `A1:AF82` | 23 | 199 |
| `REASTER (2)` | `A1:AH92` | 23 | 213 |
| `Feuil2` | empty | 0 | 0 |

Both populated sheets contain a dimensions-by-color picking grid, calculated
row/column totals, carton sections, and merged title/order/date areas. The
first sheet has its order heading in row 2 and color quantities in rows 12–18;
the second sheet has its own different order number and a wider dimensions
grid. The sheets use different formulas and different merged ranges, so the
workbook should not be substituted for the one-sheet blank ZEYNA picking
template without a confirmed template selection.

Sheet `REASTER` merged cells:

`R2:T2`, `B2:Q2`, `B63:S63`, `T6:T11`, `B74:C74`, `D73:E73`, `D74:E74`,
`G74:K74`, `B76:C76`, `B75:C75`, `D76:E76`, `D75:E75`, `G77:K77`,
`G76:K76`, `G75:K75`, `B78:C78`, `B77:C77`, `D78:E78`, `D77:E77`,
`G82:K82`, `G81:K81`, `G80:K80`, `G79:K79`.

Sheet `REASTER (2)` merged cells:

`D87:E87`, `G87:L87`, `A2:T2`, `U2:V2`, `V6:V11`, `B74:U74`,
`D83:E83`, `B88:C88`, `D88:E88`, `P84:T84`, `B85:C85`, `D85:E85`,
`G85:L85`, `B86:C86`, `D86:E86`, `G86:L86`, `B84:C84`, `D84:E84`,
`G84:L84`, `P85:T85`, `P86:T86`, `P87:T87`, `B87:C87`.

The populated sheets use landscape A4 print layouts with different scales:
43% (two pages high) for `REASTER` and 40% (two pages high) for `REASTER (2)`.
Their observed print margins are zero. Do not copy one sheet's page setup to
the other.

## `MEDIDIS M20261021 ES.xlsx`

Two-sheet Spanish order workbook, likely the “MEDIDIS_M20261021_ES” example
mentioned in the specification; its role and relationship to the proforma
remain **TO CONFIRM**.

| Sheet | Used range | Merged cells | Formula cells |
|---|---|---:|---:|
| `Sheet1` | `A1:K40` | `D1:I1` | 60 |
| `Feuil1` | `A1:C81` | none | 0 |

`Sheet1` contains the order heading and a line table beginning at row 6.
The row-6 headings are at `B` (reference), `D` (code), `E` (description),
and `F:K` (units per case, units per pallet, pallets, quantity, pallet
weight, total weight). **The values beneath those headings do not all align:
the reference item code and barcode occupy `C` and `D`, respectively.**
Confirm the intended mapping before using this workbook as a template. The
per-order header is on rows 3–5; line data starts at row 7. Formula cells
include description lookups in `E`, calculations in `H`, and total weight in
`K`; `K7=J7*H7` is a representative calculation. The descriptions in `E`
use external-workbook `XLOOKUP` formulas.

`Feuil1` is an 81-row lookup table with columns `A:C` headed `REF. CLT`,
`REF. MEDIDIS`, and `Designation`. Preserve it as a separate lookup sheet
if this workbook is ever confirmed as a template. The order sheet has an
autofilter on `B6:I40`; its page setup is portrait A4 at 56%.

## `FICHE_SUIVI_COMMANDE_VIDE_x2.xlsx`

The blank follow-up workbook is now available locally. It contains one `FICHE`
sheet with two repeated forms, rows 1–12 and 14–25. The print area is `A1:F25`,
with 24 merged regions, portrait A4 layout, and 100% scale. Each form has
labels for client, customer order number, month, order/desired/agreed dates,
frame count, shipping, volume, weight, pallet, and a checklist of related
documents. Keep this customer workbook local and preserve its formatting when
writing; field mappings and the exact output filename still need confirmation.

## Other files and remaining confirmations

- `MEDIDIS FACTURE PROFORMA 2600087.pdf` is the two-page source proforma. It
  contains 33 frame lines, a declared frame subtotal, and a separate transport
  supplement. It is the parser's local reference sample.
- `PORTAFOTOS 03.06.2025.pdf` and `PORTAFOTOS 24.02.2025.pdf` are additional
  PDF reference material; they are not the sample proforma specified for the
  first parser acceptance test.
- The DA output is `da_facing.xlsx`; the ZEYNA picking output is
  `fiche_ramassage.xlsx`. Order follow-up and manufacturing-order generation
  remain unavailable pending confirmed field mappings.
