import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import OrdersHistory from "./OrdersHistory";
import "./i18n";

function renderHistory(onNewOrder = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return {
    onNewOrder,
    ...render(
      <QueryClientProvider client={queryClient}>
        <OrdersHistory onNewOrder={onNewOrder} />
      </QueryClientProvider>,
    ),
  };
}

const savedOrders = [
  {
    id: 7,
    customer: "RESTAER",
    clientOrderNumber: "M20261020",
    proformaNumber: "2600087",
    proformaDate: "2026-10-22",
    incoterm: "CFR",
    status: "DRAFT",
    lines: [
      {
        itemCode: "9340300",
        clientRef: "208118",
        collection: "ZEYNA",
        color: "BLANC",
        format: "10x15",
        quantity: 1000,
      },
      {
        itemCode: "9340301",
        clientRef: null,
        collection: "ZEYNA",
        color: "BLANC",
        format: "13x18",
        quantity: 1892,
      },
    ],
  },
];

describe("Orders history", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("lists saved orders and opens their line details", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => savedOrders,
    });
    vi.stubGlobal("fetch", fetchMock);
    renderHistory();

    expect(await screen.findByText("M20261020")).toBeInTheDocument();
    expect(screen.getByText("2600087")).toBeInTheDocument();
    expect(screen.getByText("2,892")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "View" }));

    expect(
      screen.getByRole("heading", { name: "M20261020" }),
    ).toBeInTheDocument();
    expect(screen.getByText("9340300")).toBeInTheDocument();
    expect(screen.getByText("CFR")).toBeInTheDocument();

    fireEvent.change(screen.getByRole("searchbox"), {
      target: { value: "2600087" },
    });
    expect(screen.getAllByText("M20261020")).toHaveLength(2);
    fireEvent.change(screen.getByRole("searchbox"), {
      target: { value: "not found" },
    });
    expect(
      screen.getByText("No orders match your search."),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/orders");
  });

  it("shows a clear empty state and starts a new order", async () => {
    const onNewOrder = vi.fn();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [],
      }),
    );
    renderHistory(onNewOrder);

    expect(
      await screen.findByText("No orders have been saved yet."),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Import a proforma" }));
    expect(onNewOrder).toHaveBeenCalledOnce();
  });

  it("shows a retry action when loading fails", async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new Error("Local API is unavailable."))
      .mockResolvedValueOnce({
        ok: true,
        json: async () => savedOrders,
      });
    vi.stubGlobal("fetch", fetchMock);
    renderHistory();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Local API is unavailable.",
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("M20261020")).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  });

  it("archives drafts only after confirmation and allows restoring them", async () => {
    const archivedOrder = { ...savedOrders[0], status: "ARCHIVED" };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => savedOrders,
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => archivedOrder,
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => savedOrders[0],
      });
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);
    vi.stubGlobal("fetch", fetchMock);
    renderHistory();

    expect(await screen.findByText("M20261020")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Archive M20261020" }));

    expect(confirmSpy).toHaveBeenCalledWith(
      "Archive draft order M20261020? You can restore it later.",
    );
    expect(
      await screen.findByText(/All saved orders are archived/),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/orders/7/archive", {
      method: "POST",
    });

    fireEvent.click(
      screen.getByRole("checkbox", { name: "Show archived orders" }),
    );
    expect(await screen.findByText("M20261020")).toBeInTheDocument();
    expect(screen.getByText("Archived")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Restore M20261020" }));
    expect(await screen.findByText("Draft")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/orders/7/restore", {
      method: "POST",
    });
  });

  it("does not archive a draft when confirmation is cancelled", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => savedOrders,
    });
    vi.spyOn(window, "confirm").mockReturnValue(false);
    vi.stubGlobal("fetch", fetchMock);
    renderHistory();

    expect(await screen.findByText("M20261020")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Archive M20261020" }));

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.getByText("M20261020")).toBeInTheDocument();
  });
});
