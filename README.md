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

## Android app (Kotlin + Jetpack Compose)

The `android/` folder is a native Android app for customers. It talks to the PHP backend through the JSON API in `api/`.

**Features:** browse, search, category filter and sorting; product details with ratings; account sign-up/sign-in; a cart that is priced by the server; promo codes; checkout with a delivery address; order history and status; wishlist; and writing reviews. Admin tools stay on the website.

### Backend setup for the app

1. Existing database? Import `database/upgrade_v3.sql` once in phpMyAdmin (it adds the `api_tokens` table). A fresh install of `database/schema.sql` already includes it.
2. Keep the project at `C:\xampp\htdocs\shopsphere` so the API is at `http://localhost/shopsphere/api/index.php/`.

### Run the app

1. Open the `android/` folder in Android Studio (Koala or newer) and let Gradle sync.
2. Emulator: it works as-is (`10.0.2.2` is the emulator's address for your computer). Real phone on the same Wi-Fi: add this line to `android/gradle.properties` using your computer's LAN IP:
   `shopsphere.apiBaseUrl=http://192.168.1.50/shopsphere/api/index.php/`
3. Press Run. Debug builds allow plain `http`; use an `https` API URL for release builds.

### API reference

Base URL: `/api/index.php/`. Requests and responses are JSON. Authenticated calls send `Authorization: Bearer <token>`.

| Method | Path | Notes |
|---|---|---|
| POST | `auth/register`, `auth/login` | Return `{token, user}` |
| POST | `auth/logout` | Revokes the token |
| GET | `me` | Current user |
| GET | `categories` | |
| GET | `products?q=&category=&sort=` | `sort`: `newest`, `price_asc`, `price_desc`, `name` |
| GET | `products/{id}` | Product, reviews, `wishlisted` |
| POST | `products/{id}/reviews` | `{rating, comment}` (one review per user, editable) |
| POST | `cart/quote` | `{items:[{product_id, quantity}], coupon_code}` returns server-side prices, discount and warnings |
| POST | `orders` | `{items, coupon_code, shipping_address}` runs the same transactional checkout as the website |
| GET | `orders` | The signed-in customer's orders |
| GET / PUT / DELETE | `wishlist`, `wishlist/{productId}` | |

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
