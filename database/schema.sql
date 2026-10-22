CREATE DATABASE IF NOT EXISTS ecommerce_catalog CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ecommerce_catalog;

CREATE TABLE users (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('customer','admin') NOT NULL DEFAULT 'customer',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE categories (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL UNIQUE,
  slug VARCHAR(100) NOT NULL UNIQUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE products (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  category_id INT UNSIGNED NOT NULL,
  name VARCHAR(150) NOT NULL,
  description TEXT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  stock_quantity INT NOT NULL DEFAULT 0,
  image_url VARCHAR(500) NULL,
  is_featured BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_product_price CHECK (price >= 0),
  CONSTRAINT chk_product_stock CHECK (stock_quantity >= 0),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  INDEX idx_products_category (category_id),
  INDEX idx_products_name (name)
) ENGINE=InnoDB;

CREATE TABLE coupons (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL UNIQUE,
  discount_type ENUM('percent','fixed') NOT NULL,
  discount_value DECIMAL(10,2) NOT NULL,
  minimum_order DECIMAL(10,2) NOT NULL DEFAULT 0,
  usage_limit INT UNSIGNED NULL,
  times_used INT UNSIGNED NOT NULL DEFAULT 0,
  starts_at DATETIME NULL,
  expires_at DATETIME NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT chk_coupon_value CHECK (discount_value > 0),
  CONSTRAINT chk_coupon_minimum CHECK (minimum_order >= 0)
) ENGINE=InnoDB;

CREATE TABLE wishlists (
  user_id INT UNSIGNED NOT NULL,
  product_id INT UNSIGNED NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, product_id),
  CONSTRAINT fk_wishlist_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_wishlist_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE product_reviews (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id INT UNSIGNED NOT NULL,
  product_id INT UNSIGNED NOT NULL,
  rating TINYINT UNSIGNED NOT NULL,
  comment VARCHAR(1000) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY unique_user_review (user_id, product_id),
  CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
  CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
  INDEX idx_reviews_product (product_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE orders (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id INT UNSIGNED NOT NULL,
  status ENUM('pending','paid','processing','shipped','delivered','cancelled') NOT NULL DEFAULT 'pending',
  total_amount DECIMAL(10,2) NOT NULL,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  coupon_id INT UNSIGNED NULL,
  shipping_address TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_order_total CHECK (total_amount >= 0),
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_orders_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON UPDATE CASCADE ON DELETE SET NULL,
  INDEX idx_orders_user_created (user_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE order_items (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  order_id INT UNSIGNED NOT NULL,
  product_id INT UNSIGNED NOT NULL,
  product_name VARCHAR(150) NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  quantity INT UNSIGNED NOT NULL,
  CONSTRAINT chk_item_price CHECK (unit_price >= 0),
  CONSTRAINT chk_item_quantity CHECK (quantity > 0),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  INDEX idx_order_items_order (order_id)
) ENGINE=InnoDB;

CREATE TABLE api_tokens (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id INT UNSIGNED NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at TIMESTAMP NULL,
  CONSTRAINT fk_api_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_api_tokens_user (user_id)
) ENGINE=InnoDB;

INSERT INTO categories (name, slug) VALUES
('Electronics', 'electronics'), ('Home & Living', 'home-living'), ('Fashion', 'fashion'), ('Books', 'books');

INSERT INTO products (category_id, name, description, price, stock_quantity, image_url, is_featured) VALUES
(1, 'Wireless Noise-Cancelling Headphones', 'Comfortable over-ear headphones with immersive sound and all-day battery life.', 89.99, 25, 'https://placehold.co/600x400/173b5e/ffffff?text=Headphones', 1),
(1, 'Smart LED Desk Lamp', 'Adjustable brightness, warm-to-cool light, and a USB charging port.', 34.50, 18, 'https://placehold.co/600x400/0f766e/ffffff?text=Desk+Lamp', 1),
(2, 'Minimal Ceramic Vase', 'A handcrafted matte ceramic vase for flowers or clean shelf styling.', 24.00, 32, 'https://placehold.co/600x400/a16207/ffffff?text=Ceramic+Vase', 1),
(2, 'Cotton Throw Blanket', 'Soft woven cotton throw in a calm neutral palette.', 42.00, 15, 'https://placehold.co/600x400/854d0e/ffffff?text=Throw+Blanket', 0),
(3, 'Everyday Canvas Tote', 'Durable carry-all tote with reinforced handles and interior pocket.', 19.95, 41, 'https://placehold.co/600x400/7c2d12/ffffff?text=Canvas+Tote', 0),
(3, 'Classic White Sneakers', 'Versatile low-profile sneakers designed for everyday wear.', 64.00, 20, 'https://placehold.co/600x400/334155/ffffff?text=Sneakers', 1),
(4, 'Data Design Handbook', 'Practical techniques for creating clear and effective data products.', 29.99, 26, 'https://placehold.co/600x400/5b21b6/ffffff?text=Data+Design', 1),
(4, 'Modern SQL Essentials', 'A hands-on guide to relational modeling, queries, and performance.', 31.50, 22, 'https://placehold.co/600x400/1e40af/ffffff?text=SQL+Book', 0);

INSERT INTO coupons (code, discount_type, discount_value, minimum_order, usage_limit, expires_at) VALUES
('WELCOME10', 'percent', 10.00, 30.00, 250, '2027-12-31 23:59:59'),
('SAVE15', 'fixed', 15.00, 75.00, 100, '2027-12-31 23:59:59');

-- After registering through the app, promote your account to administrator:
-- UPDATE users SET role = 'admin' WHERE email = 'your-email@example.com';
