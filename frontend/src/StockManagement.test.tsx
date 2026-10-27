import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import StockManagement from "./StockManagement";
import "./i18n";

const stockItem = {
  id: 1,
  customer: "RESTAER",
  collection: "ZEYNA",
  color: "BLANC",
  format: "9x13",
  material: null,
  quantity: 999999,
  updatedAt: "2026-10-27T00:00:00Z",
};

function renderStock() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <StockManagement />
    </QueryClientProvider>,
  );
}

describe("Stock management", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("shows stock and saves a manual adjustment with an audit reason", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [stockItem],
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [
          {
            id: 1,
            stockItemId: 1,
            orderId: null,
            delta: 999999,
            reason: "Initial test stock seed",
            createdAt: "2026-10-27T00:00:00Z",
          },
        ],
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ ...stockItem, quantity: 12345 }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [
          {
            id: 2,
            stockItemId: 1,
            orderId: null,
            delta: -987654,
            reason: "Physical stock count",
            createdAt: "2026-10-27T00:00:01Z",
          },
        ],
      });
    vi.stubGlobal("fetch", fetchMock);
    renderStock();

    expect(await screen.findByText("999,999")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Adjust BLANC 9x13" }));
    fireEvent.change(screen.getByLabelText("New quantity"), {
      target: { value: "12345" },
    });
    fireEvent.change(screen.getByLabelText("Reason for adjustment"), {
      target: { value: "Physical stock count" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save adjustment" }));

    expect(
      await screen.findByText("Stock quantity and movement history updated."),
    ).toBeInTheDocument();
    expect(await screen.findByText("Physical stock count")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      "/api/stock/1",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({
          quantity: 12345,
          reason: "Physical stock count",
        }),
      }),
    );
    expect(screen.getByText("12,345")).toBeInTheDocument();
  });

  it("shows a retryable error when stock cannot be loaded", async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new Error("Stock service is unavailable."))
      .mockResolvedValueOnce({ ok: true, json: async () => [stockItem] });
    vi.stubGlobal("fetch", fetchMock);
    renderStock();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Stock service is unavailable.",
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("999,999")).toBeInTheDocument();
  });
});
