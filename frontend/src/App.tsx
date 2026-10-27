import { useState } from "react";
import type { ChangeEvent, FormEvent } from "react";
import { useTranslation } from "react-i18next";

type ProformaLine = {
  itemCode: string;
  clientRef: string;
  collection: string;
  color: string;
  format: string;
  rawFormat: string;
  quantity: number;
  invalidReasons: string[];
};

type IgnoredLine = {
  itemCode: string;
  description: string;
  quantity: number;
  reason: string;
};

type ProformaResult = {
  proformaNumber: string;
  clientOrderNumber: string;
  date: string;
  incoterm: string;
  customerName: string;
  lines: ProformaLine[];
  ignoredLines: IgnoredLine[];
  declaredSubtotal: number | null;
  warnings: string[];
};

const MAX_FILE_SIZE = 10 * 1024 * 1024;

function isProformaResult(value: unknown): value is ProformaResult {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const result = value as Record<string, unknown>;
  return (
    typeof result.proformaNumber === "string" &&
    typeof result.clientOrderNumber === "string" &&
    Array.isArray(result.lines) &&
    Array.isArray(result.ignoredLines) &&
    Array.isArray(result.warnings)
  );
}

function responseDetail(value: unknown): string | undefined {
  if (typeof value !== "object" || value === null) {
    return undefined;
  }
  const detail = (value as Record<string, unknown>).detail;
  return typeof detail === "string" ? detail : undefined;
}

export default function App() {
  const { t, i18n } = useTranslation();
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ProformaResult | null>(null);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0] ?? null;
    setFile(selected);
    setResult(null);
    setError("");
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file || isLoading) {
      return;
    }
    if (!file.name.toLowerCase().endsWith(".pdf")) {
      setError(t("upload.pdfOnly"));
      return;
    }
    if (file.size > MAX_FILE_SIZE) {
      setError(t("upload.tooLarge"));
      return;
    }

    setIsLoading(true);
    setError("");
    setResult(null);

    const formData = new FormData();
    formData.append("file", file);
    let response: Response;
    try {
      response = await fetch("/api/proformas/parse", {
        method: "POST",
        body: formData,
      });
    } catch {
      setError(t("upload.networkError"));
      setIsLoading(false);
      return;
    }

    let body: unknown;
    try {
      body = await response.json();
    } catch {
      setError(t("upload.invalidResponse"));
      setIsLoading(false);
      return;
    }

    if (!response.ok) {
      setError(responseDetail(body) ?? t("upload.parseError"));
      setIsLoading(false);
      return;
    }
    if (!isProformaResult(body)) {
      setError(t("upload.invalidResponse"));
      setIsLoading(false);
      return;
    }

    setResult(body);
    setIsLoading(false);
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label={t("app.title")}>
          <span className="brand-mark" aria-hidden="true">
            OA
          </span>
          <span>
            <span className="brand-name">{t("app.title")}</span>
            <span className="brand-caption">{t("app.kicker")}</span>
          </span>
        </a>
        <label className="language-picker">
          <span className="sr-only">{t("app.language")}</span>
          <select
            value={i18n.resolvedLanguage?.startsWith("fr") ? "fr" : "en"}
            onChange={(event) => void i18n.changeLanguage(event.target.value)}
          >
            <option value="en">English</option>
            <option value="fr">Français</option>
          </select>
        </label>
      </header>

      <section className="workspace" aria-labelledby="page-title">
        <div className="page-heading">
          <p className="eyebrow">{t("app.kicker")}</p>
          <h1 id="page-title">{t("app.description")}</h1>
          <p className="page-subtitle">{t("upload.intro")}</p>
        </div>

        <form className="upload-card" onSubmit={(event) => void submit(event)}>
          <div className="section-heading">
            <div className="step-number" aria-hidden="true">
              1
            </div>
            <div>
              <h2>{t("upload.title")}</h2>
              <p>{t("upload.description")}</p>
            </div>
          </div>
          <label className="file-picker">
            <input
              type="file"
              accept=".pdf,application/pdf"
              onChange={chooseFile}
              aria-label={t("upload.selectFile")}
              aria-describedby="file-help"
            />
            <span className="file-icon" aria-hidden="true">
              PDF
            </span>
            <span className="file-picker-copy">
              <strong>{file ? file.name : t("upload.selectFile")}</strong>
              <span id="file-help">{t("upload.fileHelp")}</span>
            </span>
            <span className="browse-button">{t("upload.browse")}</span>
          </label>
          <div className="upload-actions">
            <p className="privacy-note">
              <span aria-hidden="true">●</span> {t("upload.localNote")}
            </p>
            <button type="submit" disabled={!file || isLoading}>
              {isLoading ? t("upload.working") : t("upload.submit")}
            </button>
          </div>
          {error && (
            <p className="error-message" role="alert">
              {error}
            </p>
          )}
        </form>

        {result && (
          <section className="results-card" aria-labelledby="results-title">
            <div className="results-heading">
              <div>
                <p className="eyebrow">{t("results.kicker")}</p>
                <h2 id="results-title">{t("results.title")}</h2>
              </div>
              <span className="result-count">
                {t("results.lineCount", { count: result.lines.length })}
              </span>
            </div>

            <dl className="metadata-grid">
              <div>
                <dt>{t("results.proforma")}</dt>
                <dd>{result.proformaNumber}</dd>
              </div>
              <div>
                <dt>{t("results.order")}</dt>
                <dd>{result.clientOrderNumber}</dd>
              </div>
              <div>
                <dt>{t("results.customer")}</dt>
                <dd>{result.customerName || "—"}</dd>
              </div>
              <div>
                <dt>{t("results.date")}</dt>
                <dd>{result.date || "—"}</dd>
              </div>
              <div>
                <dt>{t("results.incoterm")}</dt>
                <dd>{result.incoterm || "—"}</dd>
              </div>
              <div>
                <dt>{t("results.subtotal")}</dt>
                <dd>
                  {result.declaredSubtotal === null
                    ? "—"
                    : result.declaredSubtotal.toLocaleString(i18n.language)}
                </dd>
              </div>
            </dl>

            {result.warnings.length > 0 && (
              <div className="warning-panel" role="status">
                <h3>{t("results.reviewTitle")}</h3>
                <ul>
                  {result.warnings.map((warning, index) => (
                    <li key={`${warning}-${index}`}>{warning}</li>
                  ))}
                </ul>
              </div>
            )}

            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{t("results.itemCode")}</th>
                    <th>{t("results.reference")}</th>
                    <th>{t("results.collection")}</th>
                    <th>{t("results.color")}</th>
                    <th>{t("results.format")}</th>
                    <th className="number-cell">{t("results.quantity")}</th>
                    <th>{t("results.validation")}</th>
                  </tr>
                </thead>
                <tbody>
                  {result.lines.map((line, index) => (
                    <tr key={`${line.itemCode}-${index}`}>
                      <td className="code-cell">{line.itemCode}</td>
                      <td>{line.clientRef || "—"}</td>
                      <td>{line.collection || "—"}</td>
                      <td>{line.color || "—"}</td>
                      <td>{line.format || line.rawFormat || "—"}</td>
                      <td className="number-cell">
                        {line.quantity.toLocaleString(i18n.language)}
                      </td>
                      <td>
                        {line.invalidReasons.length === 0 ? (
                          <span className="valid-tag">
                            {t("results.valid")}
                          </span>
                        ) : (
                          <span className="invalid-tag">
                            {line.invalidReasons.join(" ")}
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {result.ignoredLines.length > 0 && (
              <div className="ignored-lines">
                <h3>{t("results.ignoredTitle")}</h3>
                <ul>
                  {result.ignoredLines.map((line, index) => (
                    <li key={`${line.itemCode}-${index}`}>
                      <strong>{line.itemCode}</strong> — {line.description} (
                      {t("results.quantity")} {line.quantity}): {line.reason}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </section>
        )}
      </section>
    </main>
  );
}
