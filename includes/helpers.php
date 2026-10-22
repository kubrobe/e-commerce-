<?php
declare(strict_types=1);

function e(?string $value): string
{
    return htmlspecialchars($value ?? '', ENT_QUOTES, 'UTF-8');
}

function url(string $page = 'home', array $params = []): string
{
    return '?' . http_build_query(array_merge(['page' => $page], $params));
}

function redirect(string $page = 'home', array $params = []): never
{
    header('Location: ' . url($page, $params));
    exit;
}

function csrf_token(): string
{
    if (empty($_SESSION['csrf'])) {
        $_SESSION['csrf'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf'];
}

function verify_csrf(): void
{
    if (!hash_equals($_SESSION['csrf'] ?? '', $_POST['csrf'] ?? '')) {
        http_response_code(419);
        exit('Invalid form token. Please go back and try again.');
    }
}

function flash(string $message, string $type = 'success'): void
{
    $_SESSION['flash'][] = ['message' => $message, 'type' => $type];
}

function pull_flashes(): array
{
    $messages = $_SESSION['flash'] ?? [];
    unset($_SESSION['flash']);
    return $messages;
}

function current_user(): ?array
{
    return $_SESSION['user'] ?? null;
}

function is_admin(): bool
{
    return (current_user()['role'] ?? '') === 'admin';
}

function require_login(): void
{
    if (!current_user()) {
        flash('Please sign in to continue.', 'warning');
        redirect('login');
    }
}

function require_admin(): void
{
    if (!is_admin()) {
        http_response_code(403);
        exit('Admin access required.');
    }
}

function cart_count(): int
{
    return array_sum($_SESSION['cart'] ?? []);
}

function money(float|string $amount): string
{
    return '$' . number_format((float) $amount, 2);
}

