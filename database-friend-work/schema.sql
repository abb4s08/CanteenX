CREATE DATABASE IF NOT EXISTS canteenx CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE canteenx;

CREATE TABLE IF NOT EXISTS canteens (
  canteen_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL UNIQUE,
  token_prefix CHAR(3) NOT NULL UNIQUE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS users (
  user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(190) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role ENUM('STUDENT','SHOPKEEPER','ADMIN') NOT NULL,
  canteen_id BIGINT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_user_canteen FOREIGN KEY(canteen_id) REFERENCES canteens(canteen_id)
);
CREATE TABLE IF NOT EXISTS menu_items (
  item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  canteen_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(60) NOT NULL,
  price DECIMAL(10,2) NOT NULL CHECK(price >= 0),
  is_available BOOLEAN NOT NULL DEFAULT TRUE,
  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_item_canteen FOREIGN KEY(canteen_id) REFERENCES canteens(canteen_id),
  INDEX idx_menu_canteen_available(canteen_id,is_available,is_deleted)
);
CREATE TABLE IF NOT EXISTS token_counters (
  canteen_id BIGINT PRIMARY KEY,
  `last_value` BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_counter_canteen FOREIGN KEY(canteen_id) REFERENCES canteens(canteen_id)
);
CREATE TABLE IF NOT EXISTS orders (
  order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  canteen_id BIGINT NOT NULL,
  token_code VARCHAR(24) NOT NULL UNIQUE,
  total_amount DECIMAL(10,2) NOT NULL CHECK(total_amount >= 0),
  payment_method ENUM('CAMPUS_UPI','SMART_CARD','CASH') NOT NULL,
  payment_status ENUM('DEMO_PAID','UNPAID','FAILED') NOT NULL DEFAULT 'DEMO_PAID',
  order_status ENUM('PLACED','REDEEMED','CANCELLED') NOT NULL DEFAULT 'PLACED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  redeemed_at TIMESTAMP NULL,
  CONSTRAINT fk_order_user FOREIGN KEY(user_id) REFERENCES users(user_id),
  CONSTRAINT fk_order_canteen FOREIGN KEY(canteen_id) REFERENCES canteens(canteen_id),
  INDEX idx_orders_owner(user_id,created_at), INDEX idx_orders_queue(canteen_id,order_status,created_at)
);
CREATE TABLE IF NOT EXISTS order_items (
  order_item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  item_id BIGINT NULL,
  item_name_snapshot VARCHAR(120) NOT NULL,
  quantity INT NOT NULL CHECK(quantity > 0),
  unit_price DECIMAL(10,2) NOT NULL CHECK(unit_price >= 0),
  CONSTRAINT fk_line_order FOREIGN KEY(order_id) REFERENCES orders(order_id),
  CONSTRAINT fk_line_item FOREIGN KEY(item_id) REFERENCES menu_items(item_id) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS redemption_logs (
  log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  redeemed_by BIGINT NOT NULL,
  redeemed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_log_order FOREIGN KEY(order_id) REFERENCES orders(order_id),
  CONSTRAINT fk_log_user FOREIGN KEY(redeemed_by) REFERENCES users(user_id),
  UNIQUE KEY uq_redemption_once(order_id)
);

CREATE TABLE IF NOT EXISTS payment_transactions (
  payment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  payment_method ENUM('CAMPUS_UPI','SMART_CARD','CASH') NOT NULL,
  amount DECIMAL(10,2) NOT NULL CHECK(amount >= 0),
  payment_status ENUM('DEMO_PAID','UNPAID','FAILED') NOT NULL DEFAULT 'DEMO_PAID',
  provider_reference VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_payment_order FOREIGN KEY(order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
  INDEX idx_payment_order(order_id)
);

CREATE TABLE IF NOT EXISTS audit_logs (
  audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT NULL,
  canteen_id BIGINT NULL,
  order_id BIGINT NULL,
  event_type VARCHAR(64) NOT NULL,
  event_description TEXT NOT NULL,
  result ENUM('SUCCESS','FAILURE','WARNING') NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_actor FOREIGN KEY(actor_user_id) REFERENCES users(user_id) ON DELETE SET NULL,
  CONSTRAINT fk_audit_canteen FOREIGN KEY(canteen_id) REFERENCES canteens(canteen_id) ON DELETE SET NULL,
  CONSTRAINT fk_audit_order FOREIGN KEY(order_id) REFERENCES orders(order_id) ON DELETE SET NULL,
  INDEX idx_audit_created(created_at),
  INDEX idx_audit_event(event_type)
);
