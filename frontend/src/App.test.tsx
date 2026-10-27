import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import "./i18n";

describe("Order Automation home", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("renders the proforma import workflow", () => {
    render(<App />);

    expect(
      screen.getByRole("heading", { name: "Prepare a customer order" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Customer")).toHaveValue("RESTAER");
    expect(
      screen.getByRole("button", { name: "Check proforma" }),
    ).toBeDisabled();
  });

  it("navigates between order history and the new-order workflow", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => [],
      }),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Orders" }));
    expect(
      await screen.findByRole("heading", { name: "Orders history" }),
    ).toBeInTheDocument();
    fireEvent.click(
      within(
        screen.getByRole("navigation", { name: "Main navigation" }),
      ).getByRole("button", { name: "New order" }),
    );
    expect(
      screen.getByRole("heading", { name: "Prepare a customer order" }),
    ).toBeInTheDocument();
    fireEvent.click(
      within(
        screen.getByRole("navigation", { name: "Main navigation" }),
      ).getByRole("button", { name: "Stock" }),
    );
    expect(
      await screen.findByRole("heading", { name: "Stock management" }),
    ).toBeInTheDocument();
  });

  it("resumes a saved draft in the review step and saves its changes", async () => {
    const savedOrder = {
      id: 8,
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
          clientRef: "208119",
          collection: "ZEYNA",
          color: "BLANC",
          format: "13x18",
          quantity: 1892,
        },
      ],
    };
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => [savedOrder],
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => savedOrder,
      })
      .mockResolvedValueOnce({
        ok: true,
        headers: {
          get: () => 'attachment; filename="TEST_ORDER_fiche_ramassage.xlsx"',
        },
        blob: async () => new Blob(["xlsx data"]),
      });
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn().mockReturnValue("blob:test"),
      revokeObjectURL: vi.fn(),
    });
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(
      () => undefined,
    );
    vi.stubGlobal("fetch", fetchMock);
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Orders" }));
    fireEvent.click(
      await screen.findByRole("button", { name: "Continue draft" }),
    );
    expect(
      await screen.findByRole("heading", { name: "Proforma details" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("spinbutton", {
        name: "Edit quantity for item 9340300",
      }),
    ).toHaveValue(1000);

    fireEvent.change(
      screen.getByRole("spinbutton", {
        name: "Edit quantity for item 9340300",
      }),
      { target: { value: "999" } },
    );
    fireEvent.change(
      screen.getByRole("spinbutton", {
        name: "Edit quantity for item 9340301",
      }),
      { target: { value: "1893" } },
    );
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(
      await screen.findByRole("heading", { name: "Generate order documents" }),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenLastCalledWith(
      "/api/orders/8/lines",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({
          lines: [
            {
              itemCode: "9340300",
              clientRef: "208118",
              collection: "ZEYNA",
              color: "BLANC",
              format: "10x15",
              quantity: 999,
            },
            {
              itemCode: "9340301",
              clientRef: "208119",
              collection: "ZEYNA",
              color: "BLANC",
              format: "13x18",
              quantity: 1893,
            },
          ],
        }),
      }),
    );
    fireEvent.click(
      screen.getByRole("button", { name: "Generate picking sheet" }),
    );
    expect(
      await screen.findByText("Picking sheet generated"),
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenLastCalledWith(
      "/api/orders/8/documents/picking",
      { method: "POST" },
    );
  });

  it("accepts a proforma dropped onto the file picker", () => {
    render(<App />);
    const file = new File(["pdf content"], "dropped.pdf", {
      type: "application/pdf",
    });
    const input = screen.getByLabelText("Choose a PDF proforma");
    const dropZone = input.closest("label");

    expect(dropZone).not.toBeNull();
    if (dropZone) {
      fireEvent.drop(dropZone, { dataTransfer: { files: [file] } });
    }

    expect(screen.getByText("dropped.pdf")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Check proforma" }),
    ).toBeEnabled();
  });

  it("uploads a PDF and displays parsed order lines", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          proformaNumber: "2600087",
          clientOrderNumber: "M20261020",
          date: "2026-10-22",
          incoterm: "CFR",
          customerName: "RESTAER BUSINESS",
          lines: [
            {
              itemCode: "9340300",
              clientRef: "208118",
              collection: "ZEYNA",
              color: "BLANC",
              format: "10x15",
              rawFormat: "10X15CM",
              quantity: 1000,
              invalidReasons: [],
            },
            {
              itemCode: "9340301",
              clientRef: "208119",
              collection: "ZEYNA",
              color: "BLANC",
              format: "13x18",
              rawFormat: "13X18CM",
              quantity: 1892,
              invalidReasons: [],
            },
          ],
          ignoredLines: [
            {
              itemCode: "8999999",
              description: "Transport supplement",
              quantity: 1,
              reason: "Transport supplement",
            },
          ],
          declaredSubtotal: 2892,
          warnings: [],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          id: 7,
          customer: "RESTAER",
          clientOrderNumber: "M20261020",
          proformaNumber: "2600087",
          proformaDate: "2026-10-22",
          incoterm: "CFR",
          status: "DRAFT",
          lines: [],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          id: 7,
          customer: "RESTAER",
          clientOrderNumber: "M20261020",
          proformaNumber: "2600087",
          proformaDate: "2026-10-22",
          incoterm: "CFR",
          status: "DRAFT",
          lines: [],
        }),
      });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);
    const file = new File(["pdf content"], "proforma.pdf", {
      type: "application/pdf",
    });

    fireEvent.change(screen.getByLabelText("Choose a PDF proforma"), {
      target: { files: [file] },
    });
    fireEvent.click(screen.getByRole("button", { name: "Check proforma" }));

    expect(
      await screen.findByRole("heading", { name: "Proforma details" }),
    ).toBeInTheDocument();
    expect(screen.getByText("9340300")).toBeInTheDocument();
    expect(screen.getByDisplayValue("1000")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Quantities match");
    expect(screen.getByText("8999999")).toBeInTheDocument();
    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        "/api/proformas/parse",
        expect.objectContaining({ method: "POST" }),
      );
    });

    const quantityInput = screen.getByRole("spinbutton", {
      name: "Edit quantity for item 9340300",
    });
    fireEvent.change(quantityInput, { target: { value: "2900" } });

    expect(screen.getByRole("status")).toHaveTextContent(
      "Quantities do not match",
    );
    expect(screen.getByText("4,792")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continue" })).toBeDisabled();

    fireEvent.change(quantityInput, { target: { value: "1000" } });
    expect(screen.getByRole("status")).toHaveTextContent("Quantities match");
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    expect(
      await screen.findByRole("heading", { name: "Generate order documents" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Generate the selected ZEYNA picking workbook now",
    );
    expect(screen.getByText("Draft · M20261020")).toBeInTheDocument();
    const createOrderRequest = fetchMock.mock.calls[1];
    expect(createOrderRequest[0]).toBe("/api/orders");
    expect(JSON.parse(createOrderRequest[1].body as string)).toEqual({
      clientOrderNumber: "M20261020",
      proformaNumber: "2600087",
      proformaDate: "2026-10-22",
      incoterm: "CFR",
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
          clientRef: "208119",
          collection: "ZEYNA",
          color: "BLANC",
          format: "13x18",
          quantity: 1892,
        },
      ],
    });
    fireEvent.click(screen.getByRole("button", { name: "Back to check" }));
    expect(
      screen.getByRole("heading", { name: "Proforma details" }),
    ).toBeInTheDocument();
    fireEvent.change(
      screen.getByRole("spinbutton", {
        name: "Edit quantity for item 9340300",
      }),
      { target: { value: "1008" } },
    );
    fireEvent.change(
      screen.getByRole("spinbutton", {
        name: "Edit quantity for item 9340301",
      }),
      { target: { value: "1884" } },
    );
    expect(screen.getByRole("status")).toHaveTextContent("Quantities match");
    fireEvent.click(screen.getByRole("button", { name: "Continue" }));
    await screen.findByRole("heading", { name: "Generate order documents" });
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      "/api/orders/7/lines",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({
          lines: [
            {
              itemCode: "9340300",
              clientRef: "208118",
              collection: "ZEYNA",
              color: "BLANC",
              format: "10x15",
              quantity: 1008,
            },
            {
              itemCode: "9340301",
              clientRef: "208119",
              collection: "ZEYNA",
              color: "BLANC",
              format: "13x18",
              quantity: 1884,
            },
          ],
        }),
      }),
    );
  });

  it("shows a clear error when the local API rejects the PDF", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({ detail: "Scanned PDFs are not supported." }),
      }),
    );
    render(<App />);

    fireEvent.change(screen.getByLabelText("Choose a PDF proforma"), {
      target: {
        files: [
          new File(["pdf content"], "proforma.pdf", {
            type: "application/pdf",
          }),
        ],
      },
    });
    fireEvent.click(screen.getByRole("button", { name: "Check proforma" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Scanned PDFs are not supported.",
    );
  });

  it("keeps the review open when the order number already exists", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce({
          ok: true,
          json: async () => ({
            proformaNumber: "2600087",
            clientOrderNumber: "M20261020",
            date: "2026-10-22",
            incoterm: "CFR",
            customerName: "RESTAER BUSINESS",
            lines: [
              {
                itemCode: "9340300",
                clientRef: "208118",
                collection: "ZEYNA",
                color: "BLANC",
                format: "10x15",
                rawFormat: "10X15CM",
                quantity: 2,
                invalidReasons: [],
              },
            ],
            ignoredLines: [],
            declaredSubtotal: 2,
            warnings: [],
          }),
        })
        .mockResolvedValueOnce({
          ok: false,
          status: 409,
          json: async () => ({
            detail:
              "An order with customer order number M20261020 already exists.",
          }),
        }),
    );
    render(<App />);

    fireEvent.change(screen.getByLabelText("Choose a PDF proforma"), {
      target: {
        files: [
          new File(["pdf content"], "proforma.pdf", {
            type: "application/pdf",
          }),
        ],
      },
    });
    fireEvent.click(screen.getByRole("button", { name: "Check proforma" }));
    fireEvent.click(await screen.findByRole("button", { name: "Continue" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "already exists",
    );
    expect(
      screen.getByRole("heading", { name: "Proforma details" }),
    ).toBeInTheDocument();
  });
});
