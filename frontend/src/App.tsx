import { useState } from "react";
import type { ChangeEvent, DragEvent, FormEvent } from "react";
import { useTranslation } from "react-i18next";
import OrdersHistory from "./OrdersHistory";
import StockManagement from "./StockManagement";

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

type SavedOrder = {
  id: number;
  status: string;
  clientOrderNumber: string;
};

type WorkflowStep = "import" | "check" | "generate";
type AppPage = "workflow" | "history" | "stock";

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

function isSavedOrder(value: unknown): value is SavedOrder {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const order = value as Record<string, unknown>;
  return (
    typeof order.id === "number" &&
    typeof order.status === "string" &&
    typeof order.clientOrderNumber === "string"
  );
}

export default function App() {
  const { t, i18n } = useTranslation();
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ProformaResult | null>(null);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [step, setStep] = useState<WorkflowStep>("import");
  const [savedOrder, setSavedOrder] = useState<SavedOrder | null>(null);
  const [isSavingDraft, setIsSavingDraft] = useState(false);
  const [draftError, setDraftError] = useState("");
  const [isDragging, setIsDragging] = useState(false);
  const [activePage, setActivePage] = useState<AppPage>("workflow");

  const productPieces =
    result?.lines.reduce((total, line) => total + line.quantity, 0) ?? 0;
  const integrityKnown = result !== null && result.declaredSubtotal !== null;
  const integrityMatches =
    integrityKnown && productPieces === result.declaredSubtotal;
  const reviewWarnings =
    result?.warnings.filter(
      (warning) =>
        !warning.startsWith(
          "Parsed quantities differ from the declared subtotal by",
        ),
    ) ?? [];

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    setSelectedFile(event.target.files?.[0] ?? null);
  }

  function setSelectedFile(selected: File | null) {
    setFile(selected);
    setResult(null);
    setError("");
    setStep("import");
    setSavedOrder(null);
    setDraftError("");
  }

  function startNewOrder() {
    setSelectedFile(null);
    setActivePage("workflow");
  }

  function dropFile(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    setIsDragging(false);
    setSelectedFile(event.dataTransfer.files[0] ?? null);
  }

  function updateQuantity(lineIndex: number, quantity: number) {
    setResult((current) => {
      if (!current) {
        return current;
      }
      const lines = current.lines.map((line, index) =>
        index === lineIndex ? { ...line, quantity } : line,
      );
      return { ...current, lines };
    });
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
    setSavedOrder(null);
    setDraftError("");

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
    setStep("check");
    setIsLoading(false);
  }

  async function continueToGenerate() {
    if (!result || !integrityMatches || isSavingDraft) {
      return;
    }

    setIsSavingDraft(true);
    setDraftError("");
    const lines = result.lines.map(
      ({ itemCode, clientRef, collection, color, format, quantity }) => ({
        itemCode,
        clientRef,
        collection,
        color,
        format,
        quantity,
      }),
    );
    const requestBody = savedOrder
      ? { lines }
      : {
          clientOrderNumber: result.clientOrderNumber,
          proformaNumber: result.proformaNumber,
          proformaDate: result.date,
          incoterm: result.incoterm,
          lines,
        };
    let response: Response;
    try {
      response = await fetch(
        savedOrder ? `/api/orders/${savedOrder.id}/lines` : "/api/orders",
        {
          method: savedOrder ? "PUT" : "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(requestBody),
        },
      );
    } catch {
      setDraftError(t("orders.networkError"));
      setIsSavingDraft(false);
      return;
    }

    let body: unknown;
    try {
      body = await response.json();
    } catch {
      setDraftError(t("orders.invalidResponse"));
      setIsSavingDraft(false);
      return;
    }

    if (!response.ok) {
      setDraftError(
        responseDetail(body) ??
          (response.status === 409
            ? t("orders.conflict")
            : t("orders.createError")),
      );
      setIsSavingDraft(false);
      return;
    }
    if (!isSavedOrder(body)) {
      setDraftError(t("orders.invalidResponse"));
      setIsSavingDraft(false);
      return;
    }

    setSavedOrder(body);
    setStep("generate");
    setIsSavingDraft(false);
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
        <nav className="topbar-nav" aria-label={t("navigation.label")}>
          <button
            className={activePage === "workflow" ? "active-nav" : ""}
            type="button"
            aria-current={activePage === "workflow" ? "page" : undefined}
            onClick={startNewOrder}
          >
            {t("navigation.newOrder")}
          </button>
          <button
            className={activePage === "history" ? "active-nav" : ""}
            type="button"
            aria-current={activePage === "history" ? "page" : undefined}
            onClick={() => setActivePage("history")}
          >
            {t("navigation.history")}
          </button>
          <button
            className={activePage === "stock" ? "active-nav" : ""}
            type="button"
            aria-current={activePage === "stock" ? "page" : undefined}
            onClick={() => setActivePage("stock")}
          >
            {t("navigation.stock")}
          </button>
        </nav>
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
          <h1 id="page-title">
            {activePage === "history"
              ? t("history.pageTitle")
              : activePage === "stock"
                ? t("stock.pageTitle")
                : t("app.description")}
          </h1>
          <p className="page-subtitle">
            {activePage === "history"
              ? t("history.pageDescription")
              : activePage === "stock"
                ? t("stock.pageDescription")
                : t("upload.intro")}
          </p>
        </div>

        {activePage === "workflow" && (
          <ol className="workflow-steps" aria-label={t("workflow.label")}>
            {(["import", "check", "generate"] as const).map(
              (workflowStep, index) => (
                <li
                  key={workflowStep}
                  className={step === workflowStep ? "active-step" : ""}
                  aria-current={step === workflowStep ? "step" : undefined}
                >
                  <span>{index + 1}</span>
                  {t(`workflow.${workflowStep}`)}
                </li>
              ),
            )}
          </ol>
        )}

        {activePage === "history" && (
          <OrdersHistory onNewOrder={startNewOrder} />
        )}

        {activePage === "stock" && <StockManagement />}

        {activePage === "workflow" && step === "import" && (
          <form
            className="upload-card"
            onSubmit={(event) => void submit(event)}
          >
            <div className="section-heading">
              <div className="step-number" aria-hidden="true">
                1
              </div>
              <div>
                <h2>{t("upload.title")}</h2>
                <p>{t("upload.description")}</p>
              </div>
            </div>
            <label className="customer-field">
              <span>{t("upload.customer")}</span>
              <select defaultValue="RESTAER">
                <option value="RESTAER">RESTAER</option>
                <option value="unavailable" disabled>
                  {t("upload.otherCustomers")}
                </option>
              </select>
            </label>
            <label
              className={`file-picker ${isDragging ? "file-picker-dragging" : ""}`}
              onDragOver={(event) => {
                event.preventDefault();
                setIsDragging(true);
              }}
              onDragLeave={() => setIsDragging(false)}
              onDrop={dropFile}
            >
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
        )}

        {activePage === "workflow" && result && step === "check" && (
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

            <dl className="summary-chips">
              <div>
                <dt>{t("results.proforma")}</dt>
                <dd>{result.proformaNumber}</dd>
              </div>
              <div>
                <dt>{t("results.order")}</dt>
                <dd>{result.clientOrderNumber}</dd>
              </div>
              <div>
                <dt>{t("results.lineCountLabel")}</dt>
                <dd>{result.lines.length.toLocaleString(i18n.language)}</dd>
              </div>
              <div>
                <dt>{t("results.pieces")}</dt>
                <dd>{productPieces.toLocaleString(i18n.language)}</dd>
              </div>
            </dl>

            <div
              className={`integrity-note ${!integrityKnown ? "integrity-unknown" : integrityMatches ? "integrity-match" : "integrity-mismatch"}`}
              role="status"
            >
              <strong>
                {result.declaredSubtotal === null
                  ? t("results.integrityUnknown")
                  : integrityMatches
                    ? t("results.integrityMatch")
                    : t("results.integrityMismatch")}
              </strong>
              <span>
                {t("results.integrityDetails", {
                  parsed: productPieces.toLocaleString(i18n.language),
                  declared:
                    result.declaredSubtotal?.toLocaleString(i18n.language) ??
                    "—",
                })}
              </span>
            </div>

            {reviewWarnings.length > 0 && (
              <div className="warning-panel" role="status">
                <h3>{t("results.reviewTitle")}</h3>
                <ul>
                  {reviewWarnings.map((warning, index) => (
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
                    <tr
                      key={`${line.itemCode}-${index}`}
                      className={
                        line.invalidReasons.length > 0
                          ? "warning-row"
                          : undefined
                      }
                    >
                      <td className="code-cell">{line.itemCode}</td>
                      <td>{line.clientRef || "—"}</td>
                      <td>{line.collection || "—"}</td>
                      <td>{line.color || "—"}</td>
                      <td>{line.format || line.rawFormat || "—"}</td>
                      <td className="number-cell quantity-input-cell">
                        <input
                          aria-label={t("results.editQuantity", {
                            itemCode: line.itemCode,
                          })}
                          type="number"
                          min="0"
                          step="1"
                          value={line.quantity}
                          onChange={(event) =>
                            updateQuantity(
                              index,
                              event.currentTarget.value === ""
                                ? 0
                                : Number(event.currentTarget.value),
                            )
                          }
                        />
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
                      <strong>{line.itemCode}</strong> — {line.description}
                      <span>
                        {t("results.ignoredQuantity", {
                          quantity: line.quantity,
                        })}
                      </span>
                      <span>{line.reason}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            <div className="workflow-actions">
              <button
                className="secondary-button"
                type="button"
                onClick={() => setStep("import")}
              >
                {t("workflow.back")}
              </button>
              <button
                type="button"
                onClick={() => void continueToGenerate()}
                disabled={!integrityMatches || isSavingDraft}
                title={
                  integrityMatches ? undefined : t("workflow.resolveWarnings")
                }
              >
                {isSavingDraft ? t("orders.saving") : t("workflow.continue")}
              </button>
            </div>
            {draftError && (
              <p className="error-message" role="alert">
                {draftError}
              </p>
            )}
          </section>
        )}

        {activePage === "workflow" && result && step === "generate" && (
          <section className="results-card" aria-labelledby="generate-title">
            <div className="results-heading">
              <div>
                <p className="eyebrow">{t("workflow.generate")}</p>
                <h2 id="generate-title">{t("generate.title")}</h2>
              </div>
              <span className="result-count">
                {savedOrder
                  ? t("orders.draftStatus", {
                      orderNumber: savedOrder.clientOrderNumber,
                    })
                  : result.clientOrderNumber}
              </span>
            </div>
            <div className="generation-blocked" role="status">
              <h3>{t("generate.blockedTitle")}</h3>
              <p>{t("generate.blockedDescription")}</p>
            </div>
            <ul className="document-list">
              {(["followUp", "of", "picking", "daFacing"] as const).map(
                (documentType) => (
                  <li key={documentType}>
                    <span className="document-status" aria-hidden="true">
                      —
                    </span>
                    {t(`generate.documents.${documentType}`)}
                    <span className="document-unavailable">
                      {t("generate.notReady")}
                    </span>
                  </li>
                ),
              )}
            </ul>
            <div className="workflow-actions">
              <button
                className="secondary-button"
                type="button"
                onClick={() => setStep("check")}
              >
                {t("workflow.backToCheck")}
              </button>
              <button type="button" disabled>
                {t("generate.generateButton")}
              </button>
            </div>
          </section>
        )}
      </section>
    </main>
  );
}
