# ShopSphere - E-Commerce Product Catalog

A database-driven catalog for the Big Data semester project. Customers can browse and search products, create accounts, maintain a cart, place orders, and track their order status. Administrators can add catalog items and manage orders.

## Technology

- PHP 8+ with PDO
- MySQL 8+ / MariaDB
- HTML and CSS
- XAMPP (recommended by the course guide)

## Features

- Product catalog with category filtering, full-text-style search, and sorting
- Product detail pages and inventory-aware cart
- Account registration and secure password hashing
- Transactional checkout that records orders and decrements inventory
- Customer order tracking
- Role-based admin dashboard for product creation and order-status updates
- Normalized MySQL design using users, categories, products, orders, and order_items

## Version 2 upgrades

- Wishlists for signed-in customers
- One review per customer per product, with ratings and editable feedback
- Coupon engine with percentage/fixed discounts, expiry, usage limits, and minimum-order rules
- Transactional order discount handling and coupon redemption counts
- Admin analytics: revenue, order count, average order value, customer count, seven-day sales chart, and low-stock alerts

### Upgrade an existing ShopSphere database

If the original app is already running, import `database/upgrade_v2.sql` once through phpMyAdmin before copying the updated PHP files into your XAMPP project folder. This keeps existing accounts, products, and orders intact while adding the new tables and columns.

Two demo coupons are added during the upgrade: `WELCOME10` (10% off orders of $30+) and `SAVE15` ($15 off orders of $75+).

## Local setup with XAMPP

1. Install and open XAMPP, then start **Apache** and **MySQL**.
2. Copy this project folder into `C:\xampp\htdocs\shopsphere`.
3. Go to `http://localhost/phpmyadmin`, choose **Import**, and import `database/schema.sql`.
4. Open `http://localhost/shopsphere/`.
5. Register an account. To make it an administrator, run this in phpMyAdmin after replacing the email:

   ```sql
   UPDATE users SET role = 'admin' WHERE email = 'your-email@example.com';
   ```

The defaults in `config.php` match a standard XAMPP MySQL setup (`root` with no password). Change them if your local database differs.

## Suggested proposal summary

**Title:** ShopSphere: E-Commerce Product Catalog and Order Management System

**Problem:** Small sellers need a simple, organized way to publish products, control stock, and handle customer orders without relying on manual records.

**Objective:** Build a web application that lets customers discover, filter, and buy products while giving administrators reliable catalog, inventory, and order controls.

**Core entities:** User, Category, Product, Order, and Order Item.

## Database relationships

```text
Category 1 ---- * Product
User     1 ---- * Order
Order    1 ---- * OrderItem
Product  1 ---- * OrderItem
```

`order_items` stores the purchased product name and price at checkout so historical orders remain accurate even if the catalog changes later.
# e-commerce-
