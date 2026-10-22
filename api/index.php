<?php
declare(strict_types=1);

/**
 * ShopSphere JSON API for the Android app.
 * Token auth (Authorization: Bearer <token>). The cart lives in the app and is priced server-side.
 */

require __DIR__ . '/../config.php';
session_write_close();
header_remove('Set-Cookie');
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

// ---------- helpers ----------

function respond(int $status, array $data): never
{
    http_response_code($status);
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRESERVE_ZERO_FRACTION);
    exit;
}

function fail(int $status, string $message): never
{
    respond($status, ['error' => $message]);
}

function json_body(): array
{
    $raw = file_get_contents('php://input');
    if ($raw === false || trim($raw) === '') return [];
    $data = json_decode($raw, true);
    if (!is_array($data)) fail(400, 'Request body must be valid JSON.');
    return $data;
}

function bearer_token(): ?string
{
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    if ($header === '' && function_exists('getallheaders')) {
        foreach (getallheaders() as $name => $value) {
            if (strtolower($name) === 'authorization') { $header = $value; break; }
        }
    }
    return preg_match('/^Bearer\s+([A-Za-z0-9]+)$/', $header, $m) ? $m[1] : null;
}

function auth_user(): ?array
{
    static $resolved = false, $user = null;
    if ($resolved) return $user;
    $resolved = true;
    $token = bearer_token();
    if (!$token) return null;
    $hash = hash('sha256', $token);
    $stmt = db()->prepare('SELECT u.id, u.name, u.email, u.role FROM api_tokens t JOIN users u ON u.id = t.user_id WHERE t.token_hash = ?');
    $stmt->execute([$hash]);
    $user = $stmt->fetch() ?: null;
    if ($user) {
        db()->prepare('UPDATE api_tokens SET last_used_at = NOW() WHERE token_hash = ?')->execute([$hash]);
        $user['id'] = (int) $user['id'];
    }
    return $user;
}

function require_auth(): array
{
    return auth_user() ?? fail(401, 'Please sign in to continue.');
}

function issue_token(int $userId): string
{
    $token = bin2hex(random_bytes(32));
    db()->prepare('INSERT INTO api_tokens (user_id, token_hash) VALUES (?, ?)')->execute([$userId, hash('sha256', $token)]);
    return $token;
}

function format_product(array $p): array
{
    return [
        'id' => (int) $p['id'],
        'category_id' => (int) $p['category_id'],
        'category_name' => $p['category_name'] ?? null,
        'name' => $p['name'],
        'description' => $p['description'],
        'price' => (float) $p['price'],
        'stock_quantity' => (int) $p['stock_quantity'],
        'image_url' => $p['image_url'] ?: null,
        'is_featured' => (bool) $p['is_featured'],
        'rating_avg' => isset($p['rating_avg']) ? round((float) $p['rating_avg'], 1) : 0.0,
        'review_count' => (int) ($p['review_count'] ?? 0),
    ];
}

const PRODUCT_SELECT = 'SELECT p.*, c.name AS category_name,
    (SELECT COALESCE(AVG(rating), 0) FROM product_reviews r WHERE r.product_id = p.id) AS rating_avg,
    (SELECT COUNT(*) FROM product_reviews r WHERE r.product_id = p.id) AS review_count
    FROM products p JOIN categories c ON c.id = p.category_id';

function find_product(int $id): ?array
{
    $stmt = db()->prepare(PRODUCT_SELECT . ' WHERE p.id = ?');
    $stmt->execute([$id]);
    return $stmt->fetch() ?: null;
}

/** Merge duplicate lines and sanitise quantities from the app's cart. */
function normalize_items(mixed $items): array
{
    if (!is_array($items)) fail(422, 'items must be a list.');
    $merged = [];
    foreach ($items as $item) {
        $id = (int) ($item['product_id'] ?? 0);
        $qty = (int) ($item['quantity'] ?? 0);
        if ($id < 1 || $qty < 1) continue;
        $merged[$id] = ($merged[$id] ?? 0) + $qty;
    }
    return $merged;
}

function lookup_coupon(string $code, bool $lock = false): ?array
{
    $sql = 'SELECT * FROM coupons WHERE code = ? AND is_active = 1
            AND (starts_at IS NULL OR starts_at <= NOW()) AND (expires_at IS NULL OR expires_at > NOW())
            AND (usage_limit IS NULL OR times_used < usage_limit)' . ($lock ? ' FOR UPDATE' : '');
    $stmt = db()->prepare($sql);
    $stmt->execute([strtoupper(trim($code))]);
    return $stmt->fetch() ?: null;
}

function coupon_discount(array $coupon, float $subtotal): float
{
    $discount = $coupon['discount_type'] === 'percent'
        ? $subtotal * ((float) $coupon['discount_value'] / 100)
        : (float) $coupon['discount_value'];
    return round(min($discount, $subtotal), 2);
}

// ---------- routing ----------

$uri = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
if (!preg_match('#/api(?:/index\.php)?(/.*)?$#', $uri, $m)) fail(404, 'Not found.');
$segments = array_values(array_filter(explode('/', $m[1] ?? ''), fn($s) => $s !== ''));
$method = $_SERVER['REQUEST_METHOD'];
$route = $segments[0] ?? '';

try {
    // --- auth ---
    if ($route === 'auth' && ($segments[1] ?? '') === 'register' && $method === 'POST') {
        $b = json_body();
        $name = trim((string) ($b['name'] ?? ''));
        $email = filter_var(trim((string) ($b['email'] ?? '')), FILTER_VALIDATE_EMAIL);
        $password = (string) ($b['password'] ?? '');
        if ($name === '' || mb_strlen($name) > 100 || !$email || strlen($password) < 8) {
            fail(422, 'Use a valid name, email, and a password with at least 8 characters.');
        }
        try {
            db()->prepare('INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)')
                ->execute([$name, $email, password_hash($password, PASSWORD_DEFAULT)]);
        } catch (PDOException $e) {
            fail(409, 'That email address already has an account.');
        }
        $id = (int) db()->lastInsertId();
        respond(201, ['token' => issue_token($id), 'user' => ['id' => $id, 'name' => $name, 'email' => $email, 'role' => 'customer']]);
    }

    if ($route === 'auth' && ($segments[1] ?? '') === 'login' && $method === 'POST') {
        $b = json_body();
        $stmt = db()->prepare('SELECT id, name, email, password_hash, role FROM users WHERE email = ?');
        $stmt->execute([trim((string) ($b['email'] ?? ''))]);
        $user = $stmt->fetch();
        if (!$user || !password_verify((string) ($b['password'] ?? ''), $user['password_hash'])) {
            fail(401, 'Incorrect email or password.');
        }
        respond(200, ['token' => issue_token((int) $user['id']), 'user' => [
            'id' => (int) $user['id'], 'name' => $user['name'], 'email' => $user['email'], 'role' => $user['role'],
        ]]);
    }

    if ($route === 'auth' && ($segments[1] ?? '') === 'logout' && $method === 'POST') {
        $token = bearer_token();
        if ($token) db()->prepare('DELETE FROM api_tokens WHERE token_hash = ?')->execute([hash('sha256', $token)]);
        respond(200, ['ok' => true]);
    }

    if ($route === 'me' && $method === 'GET') {
        respond(200, ['user' => require_auth()]);
    }

    // --- catalog ---
    if ($route === 'categories' && $method === 'GET') {
        $rows = db()->query('SELECT id, name, slug FROM categories ORDER BY name')->fetchAll();
        respond(200, ['categories' => array_map(fn($r) => ['id' => (int) $r['id'], 'name' => $r['name'], 'slug' => $r['slug']], $rows)]);
    }

    if ($route === 'products' && count($segments) === 1 && $method === 'GET') {
        $where = ['p.stock_quantity > 0'];
        $values = [];
        $q = trim((string) ($_GET['q'] ?? ''));
        $category = (int) ($_GET['category'] ?? 0);
        if ($q !== '') {
            $like = '%' . addcslashes($q, '%_\\') . '%';
            $where[] = '(p.name LIKE ? OR p.description LIKE ?)';
            $values[] = $like; $values[] = $like;
        }
        if ($category) { $where[] = 'p.category_id = ?'; $values[] = $category; }
        if (!empty($_GET['featured'])) $where[] = 'p.is_featured = 1';
        $order = match ($_GET['sort'] ?? 'newest') {
            'price_asc' => 'p.price ASC', 'price_desc' => 'p.price DESC', 'name' => 'p.name ASC', default => 'p.created_at DESC, p.id DESC',
        };
        $stmt = db()->prepare(PRODUCT_SELECT . ' WHERE ' . implode(' AND ', $where) . " ORDER BY $order");
        $stmt->execute($values);
        respond(200, ['products' => array_map('format_product', $stmt->fetchAll())]);
    }

    if ($route === 'products' && count($segments) === 2 && $method === 'GET') {
        $product = find_product((int) $segments[1]);
        if (!$product) fail(404, 'Product not found.');
        $stmt = db()->prepare('SELECT r.id, r.rating, r.comment, r.created_at, u.name AS user_name, r.user_id FROM product_reviews r JOIN users u ON u.id = r.user_id WHERE r.product_id = ? ORDER BY r.created_at DESC');
        $stmt->execute([$product['id']]);
        $reviews = array_map(fn($r) => [
            'id' => (int) $r['id'], 'user_id' => (int) $r['user_id'], 'user_name' => $r['user_name'],
            'rating' => (int) $r['rating'], 'comment' => $r['comment'], 'created_at' => $r['created_at'],
        ], $stmt->fetchAll());
        $wishlisted = false;
        if ($user = auth_user()) {
            $w = db()->prepare('SELECT 1 FROM wishlists WHERE user_id = ? AND product_id = ?');
            $w->execute([$user['id'], $product['id']]);
            $wishlisted = (bool) $w->fetchColumn();
        }
        respond(200, ['product' => format_product($product), 'reviews' => $reviews, 'wishlisted' => $wishlisted]);
    }

    if ($route === 'products' && count($segments) === 3 && $segments[2] === 'reviews' && $method === 'POST') {
        $user = require_auth();
        $product = find_product((int) $segments[1]);
        if (!$product) fail(404, 'Product not found.');
        $b = json_body();
        $rating = (int) ($b['rating'] ?? 0);
        $comment = trim((string) ($b['comment'] ?? ''));
        if ($rating < 1 || $rating > 5 || mb_strlen($comment) < 10 || mb_strlen($comment) > 1000) {
            fail(422, 'Add a rating and a review of 10 to 1000 characters.');
        }
        db()->prepare('INSERT INTO product_reviews (user_id, product_id, rating, comment) VALUES (?, ?, ?, ?)
                       ON DUPLICATE KEY UPDATE rating = VALUES(rating), comment = VALUES(comment), updated_at = CURRENT_TIMESTAMP')
            ->execute([$user['id'], $product['id'], $rating, $comment]);
        respond(200, ['ok' => true]);
    }

    // --- cart pricing ---
    if ($route === 'cart' && ($segments[1] ?? '') === 'quote' && $method === 'POST') {
        $b = json_body();
        $wanted = normalize_items($b['items'] ?? []);
        $lines = []; $warnings = []; $subtotal = 0.0;
        if ($wanted) {
            $ids = array_keys($wanted);
            $stmt = db()->prepare('SELECT id, name, price, stock_quantity, image_url FROM products WHERE id IN (' . implode(',', array_fill(0, count($ids), '?')) . ')');
            $stmt->execute($ids);
            $found = [];
            foreach ($stmt->fetchAll() as $p) $found[(int) $p['id']] = $p;
            foreach ($wanted as $id => $qty) {
                $p = $found[$id] ?? null;
                if (!$p || (int) $p['stock_quantity'] < 1) { $warnings[] = ['product_id' => $id, 'message' => 'No longer available.']; continue; }
                $stock = (int) $p['stock_quantity'];
                if ($qty > $stock) { $warnings[] = ['product_id' => $id, 'message' => "Only $stock left in stock."]; $qty = $stock; }
                $lineTotal = round((float) $p['price'] * $qty, 2);
                $subtotal += $lineTotal;
                $lines[] = ['product_id' => $id, 'name' => $p['name'], 'image_url' => $p['image_url'] ?: null,
                    'unit_price' => (float) $p['price'], 'quantity' => $qty, 'stock_quantity' => $stock, 'line_total' => $lineTotal];
            }
        }
        $subtotal = round($subtotal, 2);
        $discount = 0.0; $couponInfo = null; $couponError = null;
        $code = trim((string) ($b['coupon_code'] ?? ''));
        if ($code !== '') {
            $coupon = lookup_coupon($code);
            if (!$coupon) $couponError = 'That coupon is invalid or expired.';
            elseif ($subtotal < (float) $coupon['minimum_order']) $couponError = sprintf('This coupon requires a minimum order of $%.2f.', $coupon['minimum_order']);
            else { $discount = coupon_discount($coupon, $subtotal); $couponInfo = ['code' => $coupon['code'], 'discount_amount' => $discount]; }
        }
        respond(200, ['items' => $lines, 'subtotal' => $subtotal, 'discount' => $discount, 'total' => round($subtotal - $discount, 2),
            'coupon' => $couponInfo, 'coupon_error' => $couponError, 'warnings' => $warnings]);
    }

    // --- orders ---
    if ($route === 'orders' && count($segments) === 1 && $method === 'POST') {
        $user = require_auth();
        $b = json_body();
        $address = trim((string) ($b['shipping_address'] ?? ''));
        $wanted = normalize_items($b['items'] ?? []);
        if (mb_strlen($address) < 10 || !$wanted) fail(422, 'Add items and provide a complete delivery address.');
        $pdo = db();
        try {
            $pdo->beginTransaction();
            $subtotal = 0.0; $locked = [];
            ksort($wanted); // consistent lock order avoids deadlocks
            foreach ($wanted as $id => $qty) {
                $stmt = $pdo->prepare('SELECT id, name, price, stock_quantity FROM products WHERE id = ? FOR UPDATE');
                $stmt->execute([$id]);
                $p = $stmt->fetch();
                if (!$p) throw new RuntimeException('A product in your cart no longer exists.');
                if ((int) $p['stock_quantity'] < $qty) throw new RuntimeException($p['name'] . ' no longer has enough stock.');
                $locked[] = [$p, $qty];
                $subtotal += (float) $p['price'] * $qty;
            }
            $subtotal = round($subtotal, 2);
            $coupon = null; $discount = 0.0;
            $code = trim((string) ($b['coupon_code'] ?? ''));
            if ($code !== '') {
                $coupon = lookup_coupon($code, true);
                if (!$coupon) throw new RuntimeException('That coupon is invalid or expired.');
                if ($subtotal < (float) $coupon['minimum_order']) throw new RuntimeException('Your order is below the coupon minimum.');
                $discount = coupon_discount($coupon, $subtotal);
            }
            $total = round($subtotal - $discount, 2);
            $pdo->prepare('INSERT INTO orders (user_id, total_amount, discount_amount, coupon_id, shipping_address) VALUES (?, ?, ?, ?, ?)')
                ->execute([$user['id'], $total, $discount, $coupon['id'] ?? null, $address]);
            $orderId = (int) $pdo->lastInsertId();
            foreach ($locked as [$p, $qty]) {
                $pdo->prepare('INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, ?, ?)')
                    ->execute([$orderId, $p['id'], $p['name'], $p['price'], $qty]);
                $pdo->prepare('UPDATE products SET stock_quantity = stock_quantity - ? WHERE id = ?')->execute([$qty, $p['id']]);
            }
            if ($coupon) $pdo->prepare('UPDATE coupons SET times_used = times_used + 1 WHERE id = ?')->execute([$coupon['id']]);
            $pdo->commit();
        } catch (RuntimeException $e) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            fail(409, $e->getMessage());
        } catch (Throwable $e) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            throw $e;
        }
        respond(201, ['order' => load_order($orderId, $user['id'])]);
    }

    if ($route === 'orders' && count($segments) === 1 && $method === 'GET') {
        $user = require_auth();
        $stmt = db()->prepare('SELECT id FROM orders WHERE user_id = ? ORDER BY created_at DESC, id DESC');
        $stmt->execute([$user['id']]);
        respond(200, ['orders' => array_map(fn($r) => load_order((int) $r['id'], $user['id']), $stmt->fetchAll())]);
    }

    if ($route === 'orders' && count($segments) === 2 && $method === 'GET') {
        $user = require_auth();
        $order = load_order((int) $segments[1], $user['id']);
        if (!$order) fail(404, 'Order not found.');
        respond(200, ['order' => $order]);
    }

    // --- wishlist ---
    if ($route === 'wishlist' && count($segments) === 1 && $method === 'GET') {
        $user = require_auth();
        $stmt = db()->prepare(PRODUCT_SELECT . ' JOIN wishlists w ON w.product_id = p.id WHERE w.user_id = ? ORDER BY w.created_at DESC');
        $stmt->execute([$user['id']]);
        respond(200, ['products' => array_map('format_product', $stmt->fetchAll())]);
    }

    if ($route === 'wishlist' && count($segments) === 2 && in_array($method, ['PUT', 'DELETE'], true)) {
        $user = require_auth();
        $productId = (int) $segments[1];
        if ($method === 'PUT') {
            if (!find_product($productId)) fail(404, 'Product not found.');
            db()->prepare('INSERT IGNORE INTO wishlists (user_id, product_id) VALUES (?, ?)')->execute([$user['id'], $productId]);
        } else {
            db()->prepare('DELETE FROM wishlists WHERE user_id = ? AND product_id = ?')->execute([$user['id'], $productId]);
        }
        respond(200, ['wishlisted' => $method === 'PUT']);
    }

    fail(404, 'Not found.');
} catch (Throwable $e) {
    error_log('ShopSphere API error: ' . $e->getMessage());
    fail(500, 'Something went wrong on the server.');
}

function load_order(int $orderId, int $userId): ?array
{
    $stmt = db()->prepare('SELECT o.*, c.code AS coupon_code FROM orders o LEFT JOIN coupons c ON c.id = o.coupon_id WHERE o.id = ? AND o.user_id = ?');
    $stmt->execute([$orderId, $userId]);
    $o = $stmt->fetch();
    if (!$o) return null;
    $items = db()->prepare('SELECT product_id, product_name, unit_price, quantity FROM order_items WHERE order_id = ? ORDER BY id');
    $items->execute([$orderId]);
    return [
        'id' => (int) $o['id'], 'status' => $o['status'], 'total_amount' => (float) $o['total_amount'],
        'discount_amount' => (float) $o['discount_amount'], 'coupon_code' => $o['coupon_code'],
        'shipping_address' => $o['shipping_address'], 'created_at' => $o['created_at'],
        'items' => array_map(fn($i) => [
            'product_id' => (int) $i['product_id'], 'product_name' => $i['product_name'],
            'unit_price' => (float) $i['unit_price'], 'quantity' => (int) $i['quantity'],
        ], $items->fetchAll()),
    ];
}
