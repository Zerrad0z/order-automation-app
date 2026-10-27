import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";

type OrderLine = {
  itemCode: string;
  clientRef: string | null;
  collection: string;
  color: string;
  format: string;
  quantity: number;
};

type SavedOrder = {
  id: number;
  customer: string;
  clientOrderNumber: string;
  proformaNumber: string;
  proformaDate: string;
  incoterm: string;
  status: string;
  lines: OrderLine[];
};

function isOrderLine(value: unknown): value is OrderLine {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const line = value as Record<string, unknown>;
  return (
    typeof line.itemCode === "string" &&
    (typeof line.clientRef === "string" || line.clientRef === null) &&
    typeof line.collection === "string" &&
    typeof line.color === "string" &&
    typeof line.format === "string" &&
    typeof line.quantity === "number"
  );
}

function isSavedOrder(value: unknown): value is SavedOrder {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const order = value as Record<string, unknown>;
  return (
    typeof order.id === "number" &&
    typeof order.customer === "string" &&
    typeof order.clientOrderNumber === "string" &&
    typeof order.proformaNumber === "string" &&
    typeof order.proformaDate === "string" &&
    typeof order.incoterm === "string" &&
    typeof order.status === "string" &&
    Array.isArray(order.lines) &&
    order.lines.every(isOrderLine)
  );
}

async function fetchOrders(): Promise<SavedOrder[]> {
  const response = await fetch("/api/orders");
  const body: unknown = await response.json();
  if (!response.ok) {
    const detail =
      typeof body === "object" && body !== null
        ? (body as Record<string, unknown>).detail
        : undefined;
    throw new Error(
      typeof detail === "string" ? detail : "Could not load saved orders.",
    );
  }
  if (!Array.isArray(body) || !body.every(isSavedOrder)) {
    throw new Error("The application returned an unreadable orders list.");
  }
  return body;
}

type OrdersHistoryProps = {
  onNewOrder: () => void;
};

export default function OrdersHistory({ onNewOrder }: OrdersHistoryProps) {
  const { t, i18n } = useTranslation();
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const ordersQuery = useQuery({
    queryKey: ["orders"],
    queryFn: fetchOrders,
  });

  const orders = ordersQuery.data ?? [];
  const normalizedSearch = search.trim().toLocaleLowerCase(i18n.language);
  const filteredOrders = orders.filter((order) =>
    `${order.clientOrderNumber} ${order.proformaNumber} ${order.customer}`
      .toLocaleLowerCase(i18n.language)
      .includes(normalizedSearch),
  );
  const selectedOrder = orders.find((order) => order.id === selectedId);

  return (
    <section className="history-card" aria-labelledby="history-title">
      <div className="history-heading">
        <div>
          <p className="eyebrow">{t("history.kicker")}</p>
          <h2 id="history-title">{t("history.title")}</h2>
        </div>
        <button type="button" onClick={onNewOrder}>
          {t("history.newOrder")}
        </button>
      </div>

      <label className="history-search">
        <span className="sr-only">{t("history.searchLabel")}</span>
        <input
          type="search"
          value={search}
          onChange={(event) => setSearch(event.currentTarget.value)}
          placeholder={t("history.searchPlaceholder")}
        />
      </label>

      {ordersQuery.isPending && (
        <p className="history-message" role="status">
          {t("history.loading")}
        </p>
      )}
      {ordersQuery.isError && (
        <div className="history-error" role="alert">
          <p>{ordersQuery.error.message || t("history.loadError")}</p>
          <button type="button" onClick={() => void ordersQuery.refetch()}>
            {t("history.retry")}
          </button>
        </div>
      )}
      {ordersQuery.isSuccess && orders.length === 0 && (
        <div className="history-empty">
          <p>{t("history.empty")}</p>
          <button type="button" onClick={onNewOrder}>
            {t("history.startOrder")}
          </button>
        </div>
      )}
      {ordersQuery.isSuccess && orders.length > 0 && (
        <>
          {filteredOrders.length === 0 ? (
            <p className="history-message">{t("history.noMatches")}</p>
          ) : (
            <div className="table-scroll">
              <table className="history-table">
                <thead>
                  <tr>
                    <th>{t("history.order")}</th>
                    <th>{t("history.proforma")}</th>
                    <th>{t("history.date")}</th>
                    <th>{t("history.lines")}</th>
                    <th>{t("history.pieces")}</th>
                    <th>{t("history.status")}</th>
                    <th>{t("history.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredOrders.map((order) => (
                    <tr key={order.id}>
                      <td className="code-cell">{order.clientOrderNumber}</td>
                      <td>{order.proformaNumber}</td>
                      <td>{order.proformaDate}</td>
                      <td>{order.lines.length}</td>
                      <td className="number-cell">
                        {order.lines
                          .reduce((sum, line) => sum + line.quantity, 0)
                          .toLocaleString(i18n.language)}
                      </td>
                      <td>
                        <span
                          className={`order-status ${order.status.toLowerCase()}`}
                        >
                          {t(`history.statuses.${order.status}`, {
                            defaultValue: order.status,
                          })}
                        </span>
                      </td>
                      <td>
                        <button
                          className="text-button"
                          type="button"
                          onClick={() =>
                            setSelectedId(
                              selectedId === order.id ? null : order.id,
                            )
                          }
                        >
                          {selectedId === order.id
                            ? t("history.closeDetails")
                            : t("history.viewDetails")}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {selectedOrder && (
        <section className="order-detail" aria-labelledby="order-detail-title">
          <div className="detail-heading">
            <div>
              <p className="eyebrow">{selectedOrder.customer}</p>
              <h3 id="order-detail-title">{selectedOrder.clientOrderNumber}</h3>
            </div>
            <span
              className={`order-status ${selectedOrder.status.toLowerCase()}`}
            >
              {t(`history.statuses.${selectedOrder.status}`, {
                defaultValue: selectedOrder.status,
              })}
            </span>
          </div>
          <dl className="metadata-grid">
            <div>
              <dt>{t("history.proforma")}</dt>
              <dd>{selectedOrder.proformaNumber}</dd>
            </div>
            <div>
              <dt>{t("history.date")}</dt>
              <dd>{selectedOrder.proformaDate}</dd>
            </div>
            <div>
              <dt>{t("history.incoterm")}</dt>
              <dd>{selectedOrder.incoterm}</dd>
            </div>
            <div>
              <dt>{t("history.lines")}</dt>
              <dd>{selectedOrder.lines.length}</dd>
            </div>
            <div>
              <dt>{t("history.pieces")}</dt>
              <dd>
                {selectedOrder.lines
                  .reduce((sum, line) => sum + line.quantity, 0)
                  .toLocaleString(i18n.language)}
              </dd>
            </div>
          </dl>
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
                </tr>
              </thead>
              <tbody>
                {selectedOrder.lines.map((line, index) => (
                  <tr key={`${line.itemCode}-${index}`}>
                    <td className="code-cell">{line.itemCode}</td>
                    <td>{line.clientRef || "—"}</td>
                    <td>{line.collection}</td>
                    <td>{line.color}</td>
                    <td>{line.format}</td>
                    <td className="number-cell">
                      {line.quantity.toLocaleString(i18n.language)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </section>
  );
}
