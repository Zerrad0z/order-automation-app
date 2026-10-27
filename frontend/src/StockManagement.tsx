import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";

type StockItem = {
  id: number;
  customer: string | null;
  collection: string | null;
  color: string | null;
  format: string | null;
  material: string | null;
  quantity: number;
  updatedAt: string;
};

type StockMovement = {
  id: number;
  stockItemId: number;
  orderId: number | null;
  delta: number;
  reason: string;
  createdAt: string;
};

function isStockItem(value: unknown): value is StockItem {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const item = value as Record<string, unknown>;
  return (
    typeof item.id === "number" &&
    (typeof item.customer === "string" || item.customer === null) &&
    (typeof item.collection === "string" || item.collection === null) &&
    (typeof item.color === "string" || item.color === null) &&
    (typeof item.format === "string" || item.format === null) &&
    (typeof item.material === "string" || item.material === null) &&
    typeof item.quantity === "number" &&
    typeof item.updatedAt === "string"
  );
}

function responseDetail(value: unknown): string | undefined {
  if (typeof value !== "object" || value === null) {
    return undefined;
  }
  const detail = (value as Record<string, unknown>).detail;
  return typeof detail === "string" ? detail : undefined;
}

function isStockMovement(value: unknown): value is StockMovement {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const movement = value as Record<string, unknown>;
  return (
    typeof movement.id === "number" &&
    typeof movement.stockItemId === "number" &&
    (typeof movement.orderId === "number" || movement.orderId === null) &&
    typeof movement.delta === "number" &&
    typeof movement.reason === "string" &&
    typeof movement.createdAt === "string"
  );
}

async function fetchStock(): Promise<StockItem[]> {
  const response = await fetch("/api/stock");
  const body: unknown = await response.json();
  if (!response.ok) {
    throw new Error(responseDetail(body) ?? "Could not load stock.");
  }
  if (!Array.isArray(body) || !body.every(isStockItem)) {
    throw new Error("The application returned an unreadable stock list.");
  }
  return body;
}

async function fetchMovements(stockItemId: number): Promise<StockMovement[]> {
  const response = await fetch(`/api/stock/${stockItemId}/movements`);
  const body: unknown = await response.json();
  if (!response.ok) {
    throw new Error(responseDetail(body) ?? "Could not load stock movements.");
  }
  if (!Array.isArray(body) || !body.every(isStockMovement)) {
    throw new Error("The application returned an unreadable stock history.");
  }
  return body;
}

async function updateStock(input: {
  id: number;
  quantity: number;
  reason: string;
}): Promise<StockItem> {
  const response = await fetch(`/api/stock/${input.id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ quantity: input.quantity, reason: input.reason }),
  });
  const body: unknown = await response.json();
  if (!response.ok) {
    throw new Error(responseDetail(body) ?? "Could not update stock.");
  }
  if (!isStockItem(body)) {
    throw new Error("The application returned an unreadable stock item.");
  }
  return body;
}

export default function StockManagement() {
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [quantity, setQuantity] = useState("");
  const [reason, setReason] = useState("");
  const [validationError, setValidationError] = useState("");
  const stockQuery = useQuery({ queryKey: ["stock"], queryFn: fetchStock });
  const movementsQuery = useQuery({
    queryKey: ["stock", selectedId, "movements"],
    queryFn: () => {
      if (selectedId === null) {
        throw new Error("Select a stock item to view its movement history.");
      }
      return fetchMovements(selectedId);
    },
    enabled: selectedId !== null,
  });
  const updateMutation = useMutation({
    mutationFn: updateStock,
    onSuccess: (updatedItem) => {
      queryClient.setQueryData<StockItem[]>(["stock"], (currentItems) =>
        currentItems?.map((item) =>
          item.id === updatedItem.id ? updatedItem : item,
        ),
      );
      void queryClient.invalidateQueries({
        queryKey: ["stock", updatedItem.id, "movements"],
      });
      if (selectedId === updatedItem.id) {
        setQuantity(String(updatedItem.quantity));
        setReason("");
      }
    },
  });

  const items = stockQuery.data ?? [];
  const selectedItem = items.find((item) => item.id === selectedId);

  function selectItem(item: StockItem) {
    setSelectedId(item.id);
    setQuantity(String(item.quantity));
    setReason("");
    setValidationError("");
    updateMutation.reset();
  }

  function saveQuantity(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsedQuantity = Number(quantity);
    if (
      selectedId === null ||
      !Number.isSafeInteger(parsedQuantity) ||
      parsedQuantity < 0 ||
      parsedQuantity > 2147483647 ||
      reason.trim().length === 0
    ) {
      setValidationError(t("stock.invalidUpdate"));
      return;
    }
    setValidationError("");
    updateMutation.mutate({
      id: selectedId,
      quantity: parsedQuantity,
      reason: reason.trim(),
    });
  }

  return (
    <section className="history-card stock-card" aria-labelledby="stock-title">
      <div className="history-heading">
        <div>
          <p className="eyebrow">{t("stock.kicker")}</p>
          <h2 id="stock-title">{t("stock.title")}</h2>
        </div>
      </div>

      <p className="stock-notice">{t("stock.demoNotice")}</p>

      {stockQuery.isPending && (
        <p className="history-message" role="status">
          {t("stock.loading")}
        </p>
      )}
      {stockQuery.isError && (
        <div className="history-error" role="alert">
          <p>{stockQuery.error.message || t("stock.loadError")}</p>
          <button type="button" onClick={() => void stockQuery.refetch()}>
            {t("stock.retry")}
          </button>
        </div>
      )}
      {stockQuery.isSuccess && (
        <>
          <div className="table-scroll">
            <table className="history-table stock-table">
              <thead>
                <tr>
                  <th>{t("stock.customer")}</th>
                  <th>{t("stock.collection")}</th>
                  <th>{t("stock.color")}</th>
                  <th>{t("stock.format")}</th>
                  <th className="number-cell">{t("stock.quantity")}</th>
                  <th>{t("history.actions")}</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td>{item.customer || "—"}</td>
                    <td>{item.collection || item.material || "—"}</td>
                    <td>{item.color || "—"}</td>
                    <td>{item.format || "—"}</td>
                    <td className="number-cell">
                      {item.quantity.toLocaleString(i18n.language)}
                    </td>
                    <td>
                      <button
                        className="text-button"
                        type="button"
                        disabled={updateMutation.isPending}
                        aria-pressed={selectedId === item.id}
                        onClick={() => selectItem(item)}
                      >
                        {t("stock.adjust", {
                          color: item.color || item.collection || "",
                          format: item.format || "",
                        })}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {items.length === 0 && (
            <p className="history-message">{t("stock.empty")}</p>
          )}
        </>
      )}

      {selectedItem && (
        <form className="stock-adjustment" onSubmit={saveQuantity}>
          <h3>
            {t("stock.adjustmentTitle", {
              color: selectedItem.color || selectedItem.collection || "",
              format: selectedItem.format || "",
            })}
          </h3>
          <div className="stock-adjustment-fields">
            <label>
              <span>{t("stock.newQuantity")}</span>
              <input
                type="number"
                min="0"
                max="2147483647"
                step="1"
                value={quantity}
                onChange={(event) => {
                  setQuantity(event.currentTarget.value);
                  updateMutation.reset();
                }}
                required
              />
            </label>
            <label>
              <span>{t("stock.reason")}</span>
              <input
                type="text"
                maxLength={200}
                value={reason}
                onChange={(event) => {
                  setReason(event.currentTarget.value);
                  updateMutation.reset();
                }}
                placeholder={t("stock.reasonPlaceholder")}
                required
              />
            </label>
            <button type="submit" disabled={updateMutation.isPending}>
              {updateMutation.isPending ? t("stock.saving") : t("stock.save")}
            </button>
          </div>
          {validationError && (
            <p className="error-message" role="alert">
              {validationError}
            </p>
          )}
          {updateMutation.isError && (
            <p className="error-message" role="alert">
              {updateMutation.error.message || t("stock.updateError")}
            </p>
          )}
          {updateMutation.isSuccess && (
            <p className="stock-saved" role="status">
              {t("stock.saved")}
            </p>
          )}
          <section
            className="movement-history"
            aria-labelledby="movement-title"
          >
            <h3 id="movement-title">{t("stock.movementHistory")}</h3>
            {movementsQuery.isPending && (
              <p className="history-message" role="status">
                {t("stock.loadingMovements")}
              </p>
            )}
            {movementsQuery.isError && (
              <div className="history-error" role="alert">
                <p>
                  {movementsQuery.error.message || t("stock.movementError")}
                </p>
                <button
                  type="button"
                  onClick={() => void movementsQuery.refetch()}
                >
                  {t("stock.retry")}
                </button>
              </div>
            )}
            {movementsQuery.isSuccess &&
              (movementsQuery.data.length === 0 ? (
                <p className="history-message">{t("stock.noMovements")}</p>
              ) : (
                <div className="table-scroll">
                  <table className="history-table">
                    <thead>
                      <tr>
                        <th>{t("stock.movementDate")}</th>
                        <th className="number-cell">{t("stock.change")}</th>
                        <th>{t("stock.reason")}</th>
                        <th>{t("stock.relatedOrder")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {movementsQuery.data.map((movement) => (
                        <tr key={movement.id}>
                          <td>{movement.createdAt}</td>
                          <td className="number-cell">
                            {movement.delta > 0 ? "+" : ""}
                            {movement.delta.toLocaleString(i18n.language)}
                          </td>
                          <td>{movement.reason}</td>
                          <td>{movement.orderId ?? "—"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ))}
          </section>
        </form>
      )}
    </section>
  );
}
