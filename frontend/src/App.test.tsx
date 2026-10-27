import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "./App";
import "./i18n";

describe("Order Automation home", () => {
  it("renders the application name and local-workspace status", () => {
    render(<App />);

    expect(
      screen.getByRole("heading", { name: "Order Automation" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Your private workspace is ready.",
    );
  });
});
