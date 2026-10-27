CREATE TABLE customer_order (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer TEXT NOT NULL,
    client_order_number TEXT NOT NULL,
    proforma_number TEXT,
    proforma_date TEXT,
    incoterm TEXT,
    month TEXT,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    folder_path TEXT,
    created_at TEXT NOT NULL,
    UNIQUE (customer, client_order_number)
);

CREATE TABLE order_line (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id INTEGER NOT NULL REFERENCES customer_order(id),
    item_code TEXT NOT NULL,
    client_ref TEXT,
    collection TEXT NOT NULL,
    color TEXT NOT NULL,
    format TEXT NOT NULL,
    quantity INTEGER NOT NULL
);

CREATE TABLE stock_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer TEXT,
    collection TEXT,
    color TEXT,
    format TEXT,
    material TEXT,
    quantity INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL
);

CREATE TABLE stock_movement (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    stock_item_id INTEGER NOT NULL REFERENCES stock_item(id),
    order_id INTEGER REFERENCES customer_order(id),
    delta INTEGER NOT NULL,
    reason TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE generated_document (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id INTEGER NOT NULL REFERENCES customer_order(id),
    type TEXT NOT NULL,
    path TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE da_facing_row (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id INTEGER NOT NULL REFERENCES generated_document(id),
    row_key TEXT NOT NULL,
    inventory INTEGER,
    need INTEGER,
    gap INTEGER,
    extra_json TEXT
);

CREATE INDEX idx_order_line_order_id ON order_line(order_id);
CREATE INDEX idx_stock_movement_stock_item_id ON stock_movement(stock_item_id);
CREATE INDEX idx_generated_document_order_id ON generated_document(order_id);
CREATE INDEX idx_da_facing_row_document_id ON da_facing_row(document_id);
