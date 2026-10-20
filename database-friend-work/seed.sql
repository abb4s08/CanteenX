USE canteenx;
INSERT INTO canteens(name,token_prefix) VALUES ('REC Cafe','CAF'),('REC Hut','HUT')
ON DUPLICATE KEY UPDATE token_prefix=VALUES(token_prefix);
INSERT IGNORE INTO token_counters(canteen_id,`last_value`) SELECT canteen_id,0 FROM canteens;
INSERT INTO menu_items(canteen_id,name,category,price,is_available)
SELECT c.canteen_id,v.name,v.category,v.price,TRUE FROM canteens c
JOIN (SELECT 'Masala Dosa' name,'Breakfast' category,65.00 price UNION ALL SELECT 'Cold Coffee','Beverages',55.00 UNION ALL SELECT 'Veg Sandwich','Snacks',50.00) v
WHERE c.token_prefix='CAF' AND NOT EXISTS(SELECT 1 FROM menu_items m WHERE m.canteen_id=c.canteen_id AND m.name=v.name);
INSERT INTO menu_items(canteen_id,name,category,price,is_available)
SELECT c.canteen_id,v.name,v.category,v.price,TRUE FROM canteens c
JOIN (SELECT 'Paneer Roll' name,'Snacks' category,80.00 price UNION ALL SELECT 'Lemon Soda','Beverages',35.00 UNION ALL SELECT 'Veg Fried Rice','Meals',95.00) v
WHERE c.token_prefix='HUT' AND NOT EXISTS(SELECT 1 FROM menu_items m WHERE m.canteen_id=c.canteen_id AND m.name=v.name);
-- Create demonstration accounts through the app's Admin > Create demo accounts action.
