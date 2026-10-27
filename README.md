# Order Automation App

Local order-document workflow for RESTAER (ZEYNA). The current milestone
provides a local proforma import and review workflow, a PDF parser, a SQLite
database foundation, and an API health endpoint.

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
Workbook generation is not enabled yet: the required blank follow-up template
is missing from the references, and the remaining template mappings need
confirmation. No documents or stock changes are made by the current workflow.

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
