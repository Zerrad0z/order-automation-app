CREATE UNIQUE INDEX idx_stock_item_identity
    ON stock_item (
        ifnull(customer, ''),
        ifnull(collection, ''),
        ifnull(color, ''),
        ifnull(format, ''),
        ifnull(material, '')
    );

WITH formats(format) AS (
    VALUES ('9x13'), ('10x15'), ('13x18'), ('15x20'), ('18x24'), ('20x25'),
           ('21x29.7'), ('24x30'), ('29.7x42'), ('30x40'), ('40x50'), ('50x70')
),
colors(color) AS (
    VALUES ('BLANC'), ('NOIR'), ('BOIS'), ('MARRON')
)
INSERT INTO stock_item (
    customer, collection, color, format, material, quantity, updated_at
)
SELECT 'RESTAER', 'ZEYNA', colors.color, formats.format, NULL, 999999,
       strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
FROM formats CROSS JOIN colors;

INSERT INTO stock_movement (stock_item_id, delta, reason, created_at)
SELECT id, quantity, 'Initial test stock seed', updated_at
FROM stock_item
WHERE customer = 'RESTAER' AND collection = 'ZEYNA' AND quantity = 999999;
