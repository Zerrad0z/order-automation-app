import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
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
    expect(
      screen.getByRole("button", { name: "Check proforma" }),
    ).toBeDisabled();
  });

  it("uploads a PDF and displays parsed order lines", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
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
              quantity: 2892,
              invalidReasons: [],
            },
          ],
          ignoredLines: [],
          declaredSubtotal: 2892,
          warnings: [],
        }),
      }),
    );
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
    expect(
      screen.getByRole("row", { name: /9340300.*2,892/ }),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        "/api/proformas/parse",
        expect.objectContaining({ method: "POST" }),
      );
    });
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
});
