-- ShopSphere v2 upgrade. Run this once in phpMyAdmin on an existing database.
USE ecommerce_catalog;

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
  is_active BOOLEAN NOT NULL DEFAULT TRUE
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
  CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
  INDEX idx_reviews_product (product_id, created_at)
) ENGINE=InnoDB;

ALTER TABLE orders
  ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER total_amount,
  ADD COLUMN coupon_id INT UNSIGNED NULL AFTER discount_amount,
  ADD CONSTRAINT fk_orders_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON UPDATE CASCADE ON DELETE SET NULL;

INSERT INTO coupons (code, discount_type, discount_value, minimum_order, usage_limit, expires_at) VALUES
('WELCOME10', 'percent', 10.00, 30.00, 250, '2027-12-31 23:59:59'),
('SAVE15', 'fixed', 15.00, 75.00, 100, '2027-12-31 23:59:59');
