<?php
declare(strict_types=1);

function page_header(string $title): void
{
    $user = current_user();
    ?>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title><?= e($title) ?> | ShopSphere</title>
  <link rel="stylesheet" href="assets/style.css">
</head>
<body>
<header class="site-header"><div class="nav-wrap">
  <a class="brand" href="<?= url() ?>">ShopSphere</a>
  <nav>
    <a href="<?= url('catalog') ?>">Catalog</a>
    <a href="<?= url('cart') ?>">Cart <span class="pill"><?= cart_count() ?></span></a>
    <?php if ($user): ?>
      <a href="<?= url('wishlist') ?>">Wishlist</a>
      <a href="<?= url('orders') ?>">My orders</a>
      <?php if (is_admin()): ?><a href="<?= url('admin') ?>">Admin</a><?php endif; ?>
      <a href="<?= url('logout') ?>">Sign out</a>
    <?php else: ?>
      <a href="<?= url('login') ?>">Sign in</a>
    <?php endif; ?>
  </nav>
</div></header>
<main class="container">
<?php foreach (pull_flashes() as $flash): ?>
  <div class="alert <?= e($flash['type']) ?>"><?= e($flash['message']) ?></div>
<?php endforeach; ?>
<?php
}

function page_footer(): void
{
    ?>
</main>
<footer>ShopSphere - Big Data Semester Project</footer>
</body></html>
<?php
}
