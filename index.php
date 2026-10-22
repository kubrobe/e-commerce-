<?php
declare(strict_types=1);

require __DIR__ . '/config.php';
require __DIR__ . '/includes/helpers.php';
require __DIR__ . '/includes/layout.php';

$page = $_GET['page'] ?? 'home';

function product_by_id(int $id): ?array
{
    $statement = db()->prepare('SELECT p.*, c.name AS category_name FROM products p JOIN categories c ON c.id = p.category_id WHERE p.id = ?');
    $statement->execute([$id]);
    return $statement->fetch() ?: null;
}

function cart_products(): array
{
    $cart = $_SESSION['cart'] ?? [];
    if (!$cart) return [];
    $ids = array_keys($cart);
    $placeholders = implode(',', array_fill(0, count($ids), '?'));
    $statement = db()->prepare("SELECT id, name, price, stock_quantity, image_url FROM products WHERE id IN ($placeholders)");
    $statement->execute($ids);
    $products = [];
    foreach ($statement->fetchAll() as $product) {
        $product['quantity'] = min((int) $cart[$product['id']], (int) $product['stock_quantity']);
        if ($product['quantity'] > 0) {
            $products[] = $product;
        }
    }
    return $products;
}

function cart_subtotal(array $items): float
{
    return array_reduce($items, fn(float $sum, array $item): float => $sum + (float) $item['price'] * (int) $item['quantity'], 0.0);
}

function active_coupon(float $subtotal): ?array
{
    $coupon = $_SESSION['coupon'] ?? null;
    if (!$coupon) return null;
    $stmt = db()->prepare("SELECT * FROM coupons WHERE id = ? AND is_active = 1 AND (starts_at IS NULL OR starts_at <= NOW()) AND (expires_at IS NULL OR expires_at > NOW()) AND (usage_limit IS NULL OR times_used < usage_limit)");
    $stmt->execute([$coupon['id']]);
    $coupon = $stmt->fetch();
    if (!$coupon || $subtotal < (float) $coupon['minimum_order']) {
        unset($_SESSION['coupon']);
        return null;
    }
    return $coupon;
}

function cart_totals(array $items): array
{
    $subtotal = cart_subtotal($items);
    $coupon = active_coupon($subtotal);
    $discount = 0.0;
    if ($coupon) {
        $discount = $coupon['discount_type'] === 'percent'
            ? $subtotal * ((float) $coupon['discount_value'] / 100)
            : (float) $coupon['discount_value'];
        $discount = min($discount, $subtotal);
    }
    return ['subtotal' => $subtotal, 'discount' => $discount, 'total' => $subtotal - $discount, 'coupon' => $coupon];
}

function has_wishlisted(int $productId): bool
{
    if (!current_user()) return false;
    $stmt = db()->prepare('SELECT 1 FROM wishlists WHERE user_id = ? AND product_id = ?');
    $stmt->execute([current_user()['id'], $productId]);
    return (bool) $stmt->fetchColumn();
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    verify_csrf();
    $action = $_POST['action'] ?? '';

    if ($action === 'register') {
        $name = trim($_POST['name'] ?? '');
        $email = filter_var(trim($_POST['email'] ?? ''), FILTER_VALIDATE_EMAIL);
        $password = $_POST['password'] ?? '';
        if (!$name || !$email || strlen($password) < 8) {
            flash('Use a valid email and a password with at least 8 characters.', 'error'); redirect('register');
        }
        try {
            $stmt = db()->prepare('INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)');
            $stmt->execute([$name, $email, password_hash($password, PASSWORD_DEFAULT)]);
            $_SESSION['user'] = ['id' => (int) db()->lastInsertId(), 'name' => $name, 'email' => $email, 'role' => 'customer'];
            flash('Welcome to ShopSphere!'); redirect('catalog');
        } catch (PDOException $exception) {
            flash('That email address already has an account.', 'error'); redirect('register');
        }
    }

    if ($action === 'login') {
        $stmt = db()->prepare('SELECT id, name, email, password_hash, role FROM users WHERE email = ?');
        $stmt->execute([trim($_POST['email'] ?? '')]);
        $user = $stmt->fetch();
        if (!$user || !password_verify($_POST['password'] ?? '', $user['password_hash'])) {
            flash('Incorrect email or password.', 'error'); redirect('login');
        }
        session_regenerate_id(true);
        unset($user['password_hash']);
        $_SESSION['user'] = $user;
        flash('Welcome back, ' . $user['name'] . '!'); redirect('home');
    }

    if ($action === 'add_cart') {
        $product = product_by_id((int) ($_POST['product_id'] ?? 0));
        $quantity = max(1, (int) ($_POST['quantity'] ?? 1));
        if (!$product || (int) $product['stock_quantity'] < 1) {
            flash('That product is unavailable.', 'error'); redirect('catalog');
        }
        $_SESSION['cart'][$product['id']] = min((int) ($_SESSION['cart'][$product['id']] ?? 0) + $quantity, (int) $product['stock_quantity']);
        flash($product['name'] . ' was added to your cart.'); redirect('cart');
    }

    if ($action === 'update_cart') {
        foreach ($_POST['quantity'] ?? [] as $productId => $quantity) {
            $quantity = max(0, (int) $quantity);
            if ($quantity === 0) unset($_SESSION['cart'][(int) $productId]);
            else $_SESSION['cart'][(int) $productId] = $quantity;
        }
        flash('Cart updated.'); redirect('cart');
    }

    if ($action === 'apply_coupon') {
        $items = cart_products();
        $subtotal = cart_subtotal($items);
        $code = strtoupper(trim($_POST['coupon_code'] ?? ''));
        $stmt = db()->prepare("SELECT * FROM coupons WHERE code = ? AND is_active = 1 AND (starts_at IS NULL OR starts_at <= NOW()) AND (expires_at IS NULL OR expires_at > NOW()) AND (usage_limit IS NULL OR times_used < usage_limit)");
        $stmt->execute([$code]);
        $coupon = $stmt->fetch();
        if (!$coupon) { flash('That coupon is invalid or expired.', 'error'); redirect('cart'); }
        if ($subtotal < (float) $coupon['minimum_order']) { flash('This coupon requires a minimum order of ' . money($coupon['minimum_order']) . '.', 'warning'); redirect('cart'); }
        $_SESSION['coupon'] = ['id' => $coupon['id'], 'code' => $coupon['code']];
        flash($coupon['code'] . ' applied successfully.'); redirect('cart');
    }

    if ($action === 'remove_coupon') {
        unset($_SESSION['coupon']);
        flash('Coupon removed.'); redirect('cart');
    }

    if ($action === 'toggle_wishlist') {
        require_login();
        $productId = (int) ($_POST['product_id'] ?? 0);
        if (!product_by_id($productId)) { flash('Product not found.', 'error'); redirect('catalog'); }
        if (has_wishlisted($productId)) {
            db()->prepare('DELETE FROM wishlists WHERE user_id = ? AND product_id = ?')->execute([current_user()['id'], $productId]);
            flash('Removed from your wishlist.');
        } else {
            db()->prepare('INSERT IGNORE INTO wishlists (user_id, product_id) VALUES (?, ?)')->execute([current_user()['id'], $productId]);
            flash('Saved to your wishlist.');
        }
        redirect('product', ['id' => $productId]);
    }

    if ($action === 'add_review') {
        require_login();
        $productId = (int) ($_POST['product_id'] ?? 0);
        $rating = (int) ($_POST['rating'] ?? 0);
        $comment = trim($_POST['comment'] ?? '');
        if (!product_by_id($productId) || $rating < 1 || $rating > 5 || strlen($comment) < 10) {
            flash('Add a rating and a review of at least 10 characters.', 'error'); redirect('product', ['id' => $productId]);
        }
        $stmt = db()->prepare('INSERT INTO product_reviews (user_id, product_id, rating, comment) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE rating = VALUES(rating), comment = VALUES(comment), updated_at = CURRENT_TIMESTAMP');
        $stmt->execute([current_user()['id'], $productId, $rating, $comment]);
        flash('Your review has been saved.'); redirect('product', ['id' => $productId]);
    }

    if ($action === 'checkout') {
        require_login();
        $address = trim($_POST['shipping_address'] ?? '');
        $items = cart_products();
        if (strlen($address) < 10 || !$items) {
            flash('Add items and provide a complete delivery address.', 'error'); redirect('checkout');
        }
        $pdo = db();
        try {
            $pdo->beginTransaction();
            $subtotal = 0.0;
            $lockedProducts = [];
            foreach ($items as $item) {
                $stmt = $pdo->prepare('SELECT id, name, price, stock_quantity FROM products WHERE id = ? FOR UPDATE');
                $stmt->execute([$item['id']]);
                $product = $stmt->fetch();
                if (!$product || $product['stock_quantity'] < $item['quantity']) throw new RuntimeException($item['name'] . ' no longer has enough stock.');
                $lockedProducts[] = [$product, $item['quantity']];
                $subtotal += (float) $product['price'] * $item['quantity'];
            }
            $coupon = active_coupon($subtotal);
            $discount = $coupon ? min($coupon['discount_type'] === 'percent' ? $subtotal * ((float) $coupon['discount_value'] / 100) : (float) $coupon['discount_value'], $subtotal) : 0.0;
            $total = $subtotal - $discount;
            $stmt = $pdo->prepare('INSERT INTO orders (user_id, total_amount, discount_amount, coupon_id, shipping_address) VALUES (?, ?, ?, ?, ?)');
            $stmt->execute([current_user()['id'], $total, $discount, $coupon['id'] ?? null, $address]);
            $orderId = (int) $pdo->lastInsertId();
            foreach ($lockedProducts as [$product, $quantity]) {
                $pdo->prepare('INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, ?, ?)')->execute([$orderId, $product['id'], $product['name'], $product['price'], $quantity]);
                $pdo->prepare('UPDATE products SET stock_quantity = stock_quantity - ? WHERE id = ?')->execute([$quantity, $product['id']]);
            }
            if ($coupon) $pdo->prepare('UPDATE coupons SET times_used = times_used + 1 WHERE id = ?')->execute([$coupon['id']]);
            $pdo->commit();
            $_SESSION['cart'] = [];
            unset($_SESSION['coupon']);
            flash('Order #' . $orderId . ' has been placed successfully.'); redirect('orders');
        } catch (Throwable $exception) {
            if ($pdo->inTransaction()) $pdo->rollBack();
            flash($exception->getMessage(), 'error'); redirect('checkout');
        }
    }

    if ($action === 'add_product') {
        require_admin();
        $name = trim($_POST['name'] ?? '');
        $description = trim($_POST['description'] ?? '');
        $price = (float) ($_POST['price'] ?? -1);
        $stock = (int) ($_POST['stock'] ?? -1);
        $category = (int) ($_POST['category_id'] ?? 0);
        if (!$name || !$description || $price < 0 || $stock < 0 || !$category) {
            flash('Complete every product field with valid values.', 'error'); redirect('admin');
        }
        $stmt = db()->prepare('INSERT INTO products (category_id, name, description, price, stock_quantity, image_url, is_featured) VALUES (?, ?, ?, ?, ?, ?, ?)');
        $stmt->execute([$category, $name, $description, $price, $stock, trim($_POST['image_url'] ?? '') ?: null, isset($_POST['featured']) ? 1 : 0]);
        flash('Product added to the catalog.'); redirect('admin');
    }

    if ($action === 'update_order') {
        require_admin();
        $allowed = ['pending', 'paid', 'processing', 'shipped', 'delivered', 'cancelled'];
        $status = $_POST['status'] ?? '';
        if (in_array($status, $allowed, true)) {
            db()->prepare('UPDATE orders SET status = ? WHERE id = ?')->execute([$status, (int) ($_POST['order_id'] ?? 0)]);
            flash('Order status updated.');
        }
        redirect('admin');
    }
}

if ($page === 'logout') {
    unset($_SESSION['user']);
    flash('You have been signed out.'); redirect('home');
}

if ($page === 'home') {
    $featured = db()->query('SELECT * FROM products WHERE is_featured = 1 AND stock_quantity > 0 ORDER BY created_at DESC LIMIT 4')->fetchAll();
    page_header('Welcome'); ?>
    <section class="hero"><p class="eyebrow">Fresh finds, thoughtfully curated</p><h1>Everything you need,<br>all in one place.</h1><p>Discover useful products, simple checkout, and easy order tracking.</p><a class="button" href="<?= url('catalog') ?>">Shop the catalog</a></section>
    <section><div class="section-heading"><h2>Featured products</h2><a href="<?= url('catalog') ?>">View all</a></div><div class="product-grid"><?php foreach ($featured as $product): product_card($product); endforeach; ?></div></section>
    <?php page_footer(); exit;
}

if ($page === 'catalog') {
    $categories = db()->query('SELECT * FROM categories ORDER BY name')->fetchAll();
    $where = ['p.stock_quantity > 0']; $values = [];
    $q = trim($_GET['q'] ?? ''); $category = (int) ($_GET['category'] ?? 0);
    if ($q !== '') { $where[] = '(p.name LIKE ? OR p.description LIKE ?)'; $values[] = "%$q%"; $values[] = "%$q%"; }
    if ($category) { $where[] = 'p.category_id = ?'; $values[] = $category; }
    $sort = $_GET['sort'] ?? 'newest';
    $order = match ($sort) { 'price_asc' => 'p.price ASC', 'price_desc' => 'p.price DESC', 'name' => 'p.name ASC', default => 'p.created_at DESC' };
    $stmt = db()->prepare('SELECT p.*, c.name AS category_name FROM products p JOIN categories c ON c.id = p.category_id WHERE ' . implode(' AND ', $where) . " ORDER BY $order");
    $stmt->execute($values); $products = $stmt->fetchAll();
    page_header('Catalog'); ?>
    <div class="section-heading"><div><p class="eyebrow">Shop</p><h1>Product catalog</h1></div></div>
    <form class="filters" method="get"><input type="hidden" name="page" value="catalog"><input name="q" value="<?= e($q) ?>" placeholder="Search products"><select name="category"><option value="0">All categories</option><?php foreach ($categories as $item): ?><option value="<?= $item['id'] ?>" <?= $category === (int) $item['id'] ? 'selected' : '' ?>><?= e($item['name']) ?></option><?php endforeach; ?></select><select name="sort"><option value="newest">Newest</option><option value="price_asc" <?= $sort === 'price_asc' ? 'selected' : '' ?>>Price: low to high</option><option value="price_desc" <?= $sort === 'price_desc' ? 'selected' : '' ?>>Price: high to low</option><option value="name" <?= $sort === 'name' ? 'selected' : '' ?>>Name</option></select><button>Apply</button></form>
    <p class="result-count"><?= count($products) ?> product<?= count($products) === 1 ? '' : 's' ?> found</p><div class="product-grid"><?php foreach ($products as $product): product_card($product, true); endforeach; ?></div>
    <?php page_footer(); exit;
}

if ($page === 'product') {
    $product = product_by_id((int) ($_GET['id'] ?? 0));
    if (!$product) { http_response_code(404); exit('Product not found.'); }
    $reviewStmt = db()->prepare('SELECT r.*, u.name FROM product_reviews r JOIN users u ON u.id = r.user_id WHERE r.product_id = ? ORDER BY r.created_at DESC');
    $reviewStmt->execute([$product['id']]); $reviews = $reviewStmt->fetchAll();
    $rating = $reviews ? array_sum(array_column($reviews, 'rating')) / count($reviews) : 0;
    $wishlisted = has_wishlisted((int) $product['id']);
    page_header($product['name']); ?>
    <section class="product-detail"><img src="<?= e($product['image_url'] ?: 'https://placehold.co/800x600/173b5e/ffffff?text=Product') ?>" alt="<?= e($product['name']) ?>"><div><p class="eyebrow"><?= e($product['category_name']) ?></p><h1><?= e($product['name']) ?></h1><p class="rating-summary"><?= $reviews ? str_repeat('★', (int) round($rating)) . str_repeat('☆', 5 - (int) round($rating)) . ' ' . number_format($rating, 1) . ' (' . count($reviews) . ')' : 'No ratings yet' ?></p><p class="price"><?= money($product['price']) ?></p><p class="description"><?= nl2br(e($product['description'])) ?></p><p class="stock <?= $product['stock_quantity'] > 0 ? '' : 'out' ?>"><?= $product['stock_quantity'] > 0 ? $product['stock_quantity'] . ' in stock' : 'Out of stock' ?></p><?php if ($product['stock_quantity'] > 0): ?><form method="post" class="add-form"><input type="hidden" name="csrf" value="<?= csrf_token() ?>"><input type="hidden" name="action" value="add_cart"><input type="hidden" name="product_id" value="<?= $product['id'] ?>"><input name="quantity" type="number" min="1" max="<?= $product['stock_quantity'] ?>" value="1"><button class="button">Add to cart</button></form><?php endif; ?><?php if (current_user()): ?><form method="post" class="wishlist-form"><input type="hidden" name="csrf" value="<?= csrf_token() ?>"><input type="hidden" name="action" value="toggle_wishlist"><input type="hidden" name="product_id" value="<?= $product['id'] ?>"><button class="secondary"><?= $wishlisted ? '♥ Saved to wishlist' : '♡ Save to wishlist' ?></button></form><?php endif; ?></div></section>
    <section class="reviews"><div class="section-heading"><div><p class="eyebrow">Customer feedback</p><h2>Reviews</h2></div><strong><?= $reviews ? number_format($rating, 1) . ' / 5' : 'Be the first to review' ?></strong></div><?php if (current_user()): ?><form class="review-form" method="post"><input type="hidden" name="csrf" value="<?= csrf_token() ?>"><input type="hidden" name="action" value="add_review"><input type="hidden" name="product_id" value="<?= $product['id'] ?>"><select name="rating" required><option value="">Your rating</option><?php for ($i = 5; $i >= 1; $i--): ?><option value="<?= $i ?>"><?= $i ?> star<?= $i > 1 ? 's' : '' ?></option><?php endfor; ?></select><textarea name="comment" minlength="10" placeholder="What did you think of this product?" required></textarea><button>Publish review</button></form><?php endif; ?><div class="review-list"><?php foreach ($reviews as $review): ?><article class="review"><div><strong><?= e($review['name']) ?></strong><span><?= str_repeat('★', (int) $review['rating']) ?></span></div><p><?= e($review['comment']) ?></p><small><?= date('M j, Y', strtotime($review['created_at'])) ?></small></article><?php endforeach; ?></div></section>
    <?php page_footer(); exit;
}

if ($page === 'cart') {
    $items = cart_products(); $totals = cart_totals($items);
    page_header('Your cart'); ?><h1>Your cart</h1>
    <?php if (!$items): ?><div class="empty"><p>Your cart is empty.</p><a class="button" href="<?= url('catalog') ?>">Browse products</a></div><?php else: ?><form method="post"><input type="hidden" name="csrf" value="<?= csrf_token() ?>"><input type="hidden" name="action" value="update_cart"><div class="cart-list"><?php foreach ($items as $item): ?><article class="cart-item"><img src="<?= e($item['image_url'] ?: 'https://placehold.co/120x90') ?>" alt=""><div><h3><?= e($item['name']) ?></h3><p><?= money($item['price']) ?> each</p></div><input name="quantity[<?= $item['id'] ?>]" type="number" min="0" max="<?= $item['stock_quantity'] ?>" value="<?= $item['quantity'] ?>"><strong><?= money($item['price'] * $item['quantity']) ?></strong></article><?php endforeach; ?></div><div class="cart-actions"><button>Update cart</button><div><p>Total <strong><?= money($totals['total']) ?></strong></p><a class="button" href="<?= url('checkout') ?>">Checkout</a></div></div></form><div class="coupon-panel"><h3>Have a promo code?</h3><?php if ($totals['coupon']): ?><p><strong><?= e($totals['coupon']['code']) ?></strong> is applied - saving <?= money($totals['discount']) ?>.</p><form method="post"><input type="hidden" name="csrf" value="<?= csrf_token() ?>"><input type="hidden" name="action" value="remove_coupon"><button class="secondary">Remove coupon</button></form><?php else: ?><form method="post" class="coupon-form"><input type="hidden" name="csrf" value="<?= csrf_token() ?>"><input type="hidden" name="action" value="apply_coupon"><input name="coupon_code" placeholder="e.g. WELCOME10" required><button>Apply</button></form><small>Try WELCOME10 on orders of $30+.</small><?php endif; ?></div><?php endif; ?>
    <?php page_footer(); exit;
}

if ($page === 'checkout') {
    require_login(); $items = cart_products(); $totals = cart_totals($items);
    page_header('Checkout'); ?><div class="checkout"><div><p class="eyebrow">Checkout</p><h1>Place your order</h1><form method="post"><input type="hidden" name="csrf" value="<?= csrf_token() ?>"><input type="hidden" name="action" value="checkout"><label>Delivery address<textarea name="shipping_address" required placeholder="Street, building, city, postal code"></textarea></label><button class="button" <?= $items ? '' : 'disabled' ?>>Confirm order</button></form></div><aside><h2>Order summary</h2><?php foreach ($items as $item): ?><p><?= e($item['name']) ?> x <?= $item['quantity'] ?><strong><?= money($item['price'] * $item['quantity']) ?></strong></p><?php endforeach; ?><hr><p>Subtotal <strong><?= money($totals['subtotal']) ?></strong></p><?php if ($totals['discount']): ?><p class="discount">Coupon discount <strong>-<?= money($totals['discount']) ?></strong></p><?php endif; ?><p class="total">Total <strong><?= money($totals['total']) ?></strong></p></aside></div><?php page_footer(); exit;
}

if ($page === 'register' || $page === 'login') {
    page_header($page === 'register' ? 'Create account' : 'Sign in'); ?>
    <section class="auth"><p class="eyebrow"><?= $page === 'register' ? 'New customer' : 'Welcome back' ?></p><h1><?= $page === 'register' ? 'Create an account' : 'Sign in' ?></h1><form method="post"><input type="hidden" name="csrf" value="<?= csrf_token() ?>"><input type="hidden" name="action" value="<?= $page ?>"><?php if ($page === 'register'): ?><label>Full name<input name="name" required maxlength="100"></label><?php endif; ?><label>Email<input name="email" type="email" required></label><label>Password<input name="password" type="password" required minlength="8"></label><button class="button"><?= $page === 'register' ? 'Create account' : 'Sign in' ?></button></form><p><?= $page === 'register' ? 'Already registered?' : 'New here?' ?> <a href="<?= url($page === 'register' ? 'login' : 'register') ?>"><?= $page === 'register' ? 'Sign in' : 'Create an account' ?></a></p></section><?php page_footer(); exit;
}

if ($page === 'orders') {
    require_login(); $stmt = db()->prepare('SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC'); $stmt->execute([current_user()['id']]); $orders = $stmt->fetchAll();
    page_header('My orders'); ?><h1>My orders</h1><div class="orders"><?php if (!$orders): ?><div class="empty">No orders yet. <a href="<?= url('catalog') ?>">Start shopping</a>.</div><?php endif; foreach ($orders as $order): ?><article class="order"><div><h3>Order #<?= $order['id'] ?></h3><p><?= date('M j, Y', strtotime($order['created_at'])) ?></p></div><span class="status <?= e($order['status']) ?>"><?= e(ucfirst($order['status'])) ?></span><strong><?= money($order['total_amount']) ?></strong></article><?php endforeach; ?></div><?php page_footer(); exit;
}

if ($page === 'wishlist') {
    require_login();
    $stmt = db()->prepare('SELECT p.*, c.name AS category_name FROM wishlists w JOIN products p ON p.id = w.product_id JOIN categories c ON c.id = p.category_id WHERE w.user_id = ? ORDER BY w.created_at DESC');
    $stmt->execute([current_user()['id']]); $products = $stmt->fetchAll();
    page_header('Wishlist'); ?><div class="section-heading"><div><p class="eyebrow">Saved for later</p><h1>My wishlist</h1></div><a href="<?= url('catalog') ?>">Browse catalog</a></div><?php if (!$products): ?><div class="empty"><p>You have not saved anything yet.</p><a class="button" href="<?= url('catalog') ?>">Discover products</a></div><?php else: ?><div class="product-grid"><?php foreach ($products as $product): product_card($product, true); endforeach; ?></div><?php endif; ?><?php page_footer(); exit;
}

if ($page === 'admin') {
    require_admin(); $categories = db()->query('SELECT * FROM categories ORDER BY name')->fetchAll(); $products = db()->query('SELECT p.*, c.name AS category_name FROM products p JOIN categories c ON c.id = p.category_id ORDER BY p.created_at DESC')->fetchAll(); $orders = db()->query('SELECT o.*, u.name, u.email FROM orders o JOIN users u ON u.id = o.user_id ORDER BY o.created_at DESC')->fetchAll();
    $metrics = db()->query("SELECT COUNT(*) AS orders_count, COALESCE(SUM(CASE WHEN status <> 'cancelled' THEN total_amount END), 0) AS revenue, COALESCE(AVG(CASE WHEN status <> 'cancelled' THEN total_amount END), 0) AS average_order, (SELECT COUNT(*) FROM users WHERE role = 'customer') AS customers FROM orders")->fetch();
    $lowStock = db()->query('SELECT name, stock_quantity FROM products WHERE stock_quantity <= 8 ORDER BY stock_quantity, name LIMIT 8')->fetchAll();
    $sales = db()->query("SELECT DATE(created_at) AS sale_date, COALESCE(SUM(total_amount), 0) AS total FROM orders WHERE status <> 'cancelled' AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY DATE(created_at) ORDER BY sale_date")->fetchAll();
    $peak = max(array_column($sales, 'total') ?: [1]);
    page_header('Admin dashboard'); ?><p class="eyebrow">Administration</p><h1>Catalog control center</h1><section class="metric-grid"><article><span>Total revenue</span><strong><?= money($metrics['revenue']) ?></strong><small>Excluding cancelled orders</small></article><article><span>Orders</span><strong><?= $metrics['orders_count'] ?></strong><small><?= money($metrics['average_order']) ?> average order</small></article><article><span>Customers</span><strong><?= $metrics['customers'] ?></strong><small>Registered shoppers</small></article><article><span>Low stock</span><strong><?= count($lowStock) ?></strong><small>Products with 8 or fewer left</small></article></section><section class="analytics-panel panel"><div class="section-heading"><h2>Revenue - last 7 days</h2><span>Live order data</span></div><div class="bar-chart"><?php foreach ($sales as $day): ?><div class="bar-item"><div class="bar" style="height:<?= max(8, ((float) $day['total'] / $peak) * 150) ?>px"><span><?= money($day['total']) ?></span></div><small><?= date('D', strtotime($day['sale_date'])) ?></small></div><?php endforeach; ?><?php if (!$sales): ?><p>No sales yet - place a test order to populate this chart.</p><?php endif; ?></div></section><section class="admin-grid"><form class="panel" method="post"><h2>Add product</h2><input type="hidden" name="csrf" value="<?= csrf_token() ?>"><input type="hidden" name="action" value="add_product"><label>Name<input name="name" required></label><label>Category<select name="category_id" required><?php foreach ($categories as $category): ?><option value="<?= $category['id'] ?>"><?= e($category['name']) ?></option><?php endforeach; ?></select></label><label>Description<textarea name="description" required></textarea></label><div class="two-cols"><label>Price<input type="number" name="price" step="0.01" min="0" required></label><label>Stock<input type="number" name="stock" min="0" required></label></div><label>Image URL <input name="image_url" type="url"></label><label class="checkbox"><input name="featured" type="checkbox"> Feature this product</label><button class="button">Add product</button></form><div class="panel"><h2>Inventory watch</h2><?php if (!$lowStock): ?><p>All products are comfortably stocked.</p><?php else: ?><ul class="low-stock"><?php foreach ($lowStock as $product): ?><li><span><?= e($product['name']) ?></span><strong><?= $product['stock_quantity'] ?> left</strong></li><?php endforeach; ?></ul><?php endif; ?><h2>Products (<?= count($products) ?>)</h2><div class="scroll-table"><table><thead><tr><th>Product</th><th>Category</th><th>Price</th><th>Stock</th></tr></thead><tbody><?php foreach ($products as $product): ?><tr><td><?= e($product['name']) ?></td><td><?= e($product['category_name']) ?></td><td><?= money($product['price']) ?></td><td><?= $product['stock_quantity'] ?></td></tr><?php endforeach; ?></tbody></table></div></div></section><section class="panel"><h2>Recent orders</h2><div class="scroll-table"><table><thead><tr><th>Order</th><th>Customer</th><th>Total</th><th>Status</th><th>Update</th></tr></thead><tbody><?php foreach ($orders as $order): ?><tr><td>#<?= $order['id'] ?></td><td><?= e($order['name']) ?><br><small><?= e($order['email']) ?></small></td><td><?= money($order['total_amount']) ?></td><td><span class="status <?= e($order['status']) ?>"><?= e($order['status']) ?></span></td><td><form method="post" class="status-form"><input type="hidden" name="csrf" value="<?= csrf_token() ?>"><input type="hidden" name="action" value="update_order"><input type="hidden" name="order_id" value="<?= $order['id'] ?>"><select name="status"><?php foreach (['pending','paid','processing','shipped','delivered','cancelled'] as $status): ?><option <?= $status === $order['status'] ? 'selected' : '' ?>><?= $status ?></option><?php endforeach; ?></select><button>Save</button></form></td></tr><?php endforeach; ?></tbody></table></div></section><?php page_footer(); exit;
}

http_response_code(404);
page_header('Not found');
?><div class="empty"><h1>Page not found</h1><a class="button" href="<?= url() ?>">Back home</a></div><?php page_footer();

function product_card(array $product, bool $showCategory = false): void
{
    ?>
<article class="product-card"><a href="<?= url('product', ['id' => $product['id']]) ?>"><img src="<?= e($product['image_url'] ?: 'https://placehold.co/600x400/173b5e/ffffff?text=Product') ?>" alt="<?= e($product['name']) ?>"><div class="product-body"><?php if ($showCategory && isset($product['category_name'])): ?><p class="category"><?= e($product['category_name']) ?></p><?php endif; ?><h3><?= e($product['name']) ?></h3><p class="price"><?= money($product['price']) ?></p></div></a><form method="post"><input type="hidden" name="csrf" value="<?= csrf_token() ?>"><input type="hidden" name="action" value="add_cart"><input type="hidden" name="product_id" value="<?= $product['id'] ?>"><button>Add to cart</button></form></article>
<?php
}
