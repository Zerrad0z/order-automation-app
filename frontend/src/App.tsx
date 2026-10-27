import { useTranslation } from "react-i18next";

export default function App() {
  const { t } = useTranslation();

  return (
    <main className="app-shell">
      <section className="welcome-card" aria-labelledby="app-title">
        <div className="brand-mark" aria-hidden="true">
          OA
        </div>
        <p className="eyebrow">{t("app.kicker")}</p>
        <h1 id="app-title">{t("app.title")}</h1>
        <p className="welcome-copy">{t("app.description")}</p>
        <div className="status-note" role="status">
          <span className="status-dot" aria-hidden="true" />
          {t("app.status")}
        </div>
      </section>
    </main>
  );
}
