# Order Automation App

Local order-document workflow for RESTAER (ZEYNA). The current milestone
provides a local proforma import and review workflow, persisted draft orders, a
PDF parser, and a SQLite database.

## Requirements

- Java 21
- Node.js 22 and npm

## Run

On Windows, run `start.bat` to build the application, start the local server, and
open `http://localhost:8080`. The server binds to `127.0.0.1`.

The Maven wrapper downloads Maven and dependencies on the first build. Customer
proformas and Excel templates supplied in `docs/` are local reference material
and are excluded from Git; do not add customer files to source control.

## Proforma import

Choose a text-based PDF proforma (up to 10 MB) in the app to extract order
details and product lines. Review and edit quantities, check them against the
declared subtotal, and keep transport supplements separate from product
quantities. Scanned/image-only PDFs are not supported.

The parser endpoint is `POST /api/proformas/parse` with a multipart `file`
field. The parser currently targets the supplied RESTAER/ZEYNA proforma layout.
After checking the declared totals, the app saves a draft order. Draft orders
can also be listed at `GET /api/orders`, read at `GET /api/orders/{id}`, and
corrected with `PUT /api/orders/{id}/lines`. The Orders screen provides a
searchable history of saved orders and their product-line details. Drafts can
be archived after confirmation with `POST /api/orders/{id}/archive` and
restored with `POST /api/orders/{id}/restore`; generated orders cannot be
archived. Archived orders remain in the database and are hidden from the default
history view, where they can be shown and restored.

The Stock screen provides manually audited quantity adjustments. Its initial
quantities are test values (999,999 per supported color and size); replace them
with actual inventory before relying on stock figures. Order generation is being
implemented in stages. Unfinished drafts can be continued from Orders history.
The selected ZEYNA picking workbook can be generated from a draft; it is saved under
`%USERPROFILE%\OrderAutomation\orders\<order number>\fiche_ramassage.xlsx` and
downloaded by the browser. Set `order-automation.template-dir` if the local
`docs/` folder is elsewhere. The other document generators, including the DA
facing disposition, remain unavailable until their template rules are confirmed.
Orders do not yet trigger automatic stock movements.

## Build and test

```powershell
.\mvnw.cmd verify
cd frontend
npm ci
npm run lint
npm test
```

SQLite data is stored in `%USERPROFILE%\OrderAutomation\data\app.db`. Generated
orders will be stored in `%USERPROFILE%\OrderAutomation\orders` by default.
