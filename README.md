# Order Automation App

Local order-document workflow for RESTAER (ZEYNA). The first milestone sets up
the application, local database, health endpoint, and offline-capable web shell.
The proforma samples and Excel templates required for document generation have
not yet been supplied.

## Requirements

- Java 21
- Node.js 22 and npm

## Run

On Windows, run `start.bat` to build the application, start the local server, and
open `http://localhost:8080`. The server binds to `127.0.0.1`.

The Maven wrapper downloads Maven and dependencies on the first build. Thereafter
the application itself makes no external network calls.

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
