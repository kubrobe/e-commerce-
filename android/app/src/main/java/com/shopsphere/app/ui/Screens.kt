@file:OptIn(ExperimentalMaterial3Api::class)

package com.shopsphere.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.shopsphere.app.data.Order
import com.shopsphere.app.data.Product

private val sortOptions = listOf(
    "newest" to "Newest",
    "price_asc" to "Price: low to high",
    "price_desc" to "Price: high to low",
    "name" to "Name",
)

// ---------------------------------------------------------------- Shop

@Composable
fun ShopScreen(vm: ShopViewModel, onOpenProduct: (Int) -> Unit) {
    val products by vm.products.collectAsState()
    val categories by vm.categories.collectAsState()
    val categoryId by vm.categoryId.collectAsState()
    val sort by vm.sort.collectAsState()
    val savedQuery by vm.query.collectAsState()
    var text by rememberSaveable { mutableStateOf(savedQuery) }
    var sortMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; if (it.isEmpty()) vm.setQuery("") },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            placeholder = { Text("Search products") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.setQuery(text) }),
            trailingIcon = { TextButton(onClick = { vm.setQuery(text) }) { Text("Go") } },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item { FilterChip(selected = categoryId == null, onClick = { vm.setCategory(null) }, label = { Text("All") }) }
            items(categories, key = { it.id }) { c ->
                FilterChip(selected = categoryId == c.id, onClick = { vm.setCategory(c.id) }, label = { Text(c.name) })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val count = products.data?.size ?: 0
            Text(if (products.data != null) "$count product${if (count == 1) "" else "s"}" else "", style = MaterialTheme.typography.labelLarge)
            Column {
                TextButton(onClick = { sortMenu = true }) { Text("Sort: ${sortOptions.first { it.first == sort }.second}") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    sortOptions.forEach { (key, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { vm.setSort(key); sortMenu = false })
                    }
                }
            }
        }

        val data = products.data
        when {
            data == null && products.loading -> CenterLoading()
            data == null -> ErrorState(products.error ?: "Could not load products.") { vm.loadCatalog() }
            data.isEmpty() -> MessageState("No products found", "Try a different search or category.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (products.error != null) {
                    item(span = { GridItemSpan(2) }) {
                        Text(products.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                items(data, key = { it.id }) { p ->
                    ProductCard(p, onClick = { onOpenProduct(p.id) }, onAdd = { vm.addToCart(p, 1) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Product detail

@Composable
fun ProductScreen(vm: ShopViewModel, productId: Int, onBack: () -> Unit, onSignIn: () -> Unit) {
    LaunchedEffect(productId) { vm.loadDetail(productId) }
    val detail by vm.detail.collectAsState()
    val user by vm.user.collectAsState()

    Column(Modifier.fillMaxSize()) {
        BackBar(detail.data?.product?.name ?: "Product", onBack)
        val data = detail.data
        when {
            data == null && detail.loading -> CenterLoading()
            data == null -> ErrorState(detail.error ?: "Could not load this product.") { vm.loadDetail(productId) }
            else -> {
                val p = data.product
                var qty by rememberSaveable(productId) { mutableStateOf(1) }
                var rating by rememberSaveable(productId) { mutableStateOf(0) }
                var comment by rememberSaveable(productId) { mutableStateOf("") }

                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ProductImage(p.imageUrl, p.name, Modifier.fillMaxWidth().height(260.dp))
                    Column(Modifier.padding(16.dp)) {
                        p.categoryName?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
                        Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (p.reviewCount > 0) "${stars(p.ratingAvg)} ${p.ratingAvg} (${p.reviewCount})" else "No ratings yet",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(p.price.money(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(p.description, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (p.stockQuantity > 0) "${p.stockQuantity} in stock" else "Out of stock",
                            color = if (p.stockQuantity > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (p.stockQuantity > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { if (qty > 1) qty-- }) { Text("−") }
                                Text("$qty", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = { if (qty < p.stockQuantity) qty++ }) { Text("+") }
                                Spacer(Modifier.width(12.dp))
                                Button(onClick = { vm.addToCart(p, qty) }, modifier = Modifier.weight(1f)) { Text("Add to cart") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { if (user == null) onSignIn() else vm.toggleWishlist(p.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (data.wishlisted) "♥ Saved to wishlist" else "♡ Save to wishlist") }

                        HorizontalDivider(Modifier.padding(vertical = 20.dp))
                        Text("Reviews", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))

                        if (user != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Your rating:")
                                (1..5).forEach { i ->
                                    Text(
                                        if (i <= rating) "★" else "☆",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.clickable { rating = i },
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = comment, onValueChange = { comment = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("What did you think of this product? (10+ characters)") },
                                minLines = 3,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { vm.submitReview(p.id, rating, comment.trim()) { comment = ""; rating = 0 } },
                                enabled = rating in 1..5 && comment.trim().length >= 10,
                            ) { Text("Publish review") }
                            Spacer(Modifier.height(12.dp))
                        } else {
                            TextButton(onClick = onSignIn) { Text("Sign in to write a review") }
                        }

                        if (data.reviews.isEmpty()) Text("Be the first to review this product.")
                        data.reviews.forEach { r ->
                            Card(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(r.userName, fontWeight = FontWeight.Bold)
                                        Text("★".repeat(r.rating), color = MaterialTheme.colorScheme.secondary)
                                    }
                                    Text(r.comment)
                                    Text(r.createdAt.take(10), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Cart

@Composable
fun CartScreen(vm: ShopViewModel, onOpenProduct: (Int) -> Unit, onCheckout: () -> Unit, onShop: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshQuote() }
    val entries by vm.cart.collectAsState()
    val quoteState by vm.quote.collectAsState()
    val coupon by vm.couponCode.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }

    if (entries.isEmpty()) {
        MessageState("Your cart is empty", "Browse the catalog to find something you like.", "Browse products", onShop)
        return
    }
    val quote = quoteState.data
    if (quote == null) {
        if (quoteState.error != null) ErrorState(quoteState.error!!) { vm.refreshQuote() } else CenterLoading()
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Your cart", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(quote.items, key = { it.productId }) { line ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProductImage(line.imageUrl, line.name, Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpenProduct(line.productId) })
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(line.name, style = MaterialTheme.typography.titleSmall)
                        Text("${line.unitPrice.money()} each", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.setCartQuantity(line.productId, line.quantity - 1) }) { Text("−") }
                            Text("${line.quantity}")
                            TextButton(
                                onClick = { vm.setCartQuantity(line.productId, line.quantity + 1) },
                                enabled = line.quantity < line.stockQuantity,
                            ) { Text("+") }
                            TextButton(onClick = { vm.setCartQuantity(line.productId, 0) }) { Text("Remove") }
                        }
                    }
                    Text(line.lineTotal.money(), fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Promo code", style = MaterialTheme.typography.titleSmall)
                    if (coupon.isNotEmpty() && quote.coupon != null) {
                        Text("${quote.coupon.code} applied — saving ${quote.coupon.discountAmount.money()}")
                        TextButton(onClick = { vm.removeCoupon() }) { Text("Remove coupon") }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = code, onValueChange = { code = it },
                                modifier = Modifier.weight(1f), singleLine = true,
                                placeholder = { Text("e.g. WELCOME10") },
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { vm.applyCoupon(code); code = "" }, enabled = code.isNotBlank()) { Text("Apply") }
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    LabeledRow("Subtotal", quote.subtotal.money())
                    if (quote.discount > 0) LabeledRow("Discount", "-" + quote.discount.money(), valueColor = MaterialTheme.colorScheme.secondary)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    LabeledRow("Total", quote.total.money(), bold = true)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onCheckout, enabled = quote.items.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Checkout") }
                }
            }
        }
        if (quoteState.loading) item { CircularProgressIndicator(Modifier.size(20.dp)) }
    }
}

// ---------------------------------------------------------------- Checkout

@Composable
fun CheckoutScreen(vm: ShopViewModel, onBack: () -> Unit, onPlaced: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshQuote() }
    val quote = vm.quote.collectAsState().value.data
    val placing by vm.placing.collectAsState()
    var address by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        BackBar("Checkout", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Delivery address", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = address, onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3,
                placeholder = { Text("Street, building, city, postal code") },
            )
            Spacer(Modifier.height(16.dp))
            Text("Order summary", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (quote == null) CircularProgressIndicator() else {
                quote.items.forEach { LabeledRow("${it.name} × ${it.quantity}", it.lineTotal.money()) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LabeledRow("Subtotal", quote.subtotal.money())
                if (quote.discount > 0) LabeledRow("Coupon discount", "-" + quote.discount.money(), valueColor = MaterialTheme.colorScheme.secondary)
                LabeledRow("Total", quote.total.money(), bold = true)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.placeOrder(address) { onPlaced() } },
                enabled = !placing && quote != null && quote.items.isNotEmpty() && address.trim().length >= 10,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (placing) "Placing order…" else "Confirm order") }
            if (address.isNotEmpty() && address.trim().length < 10) {
                Text("Please enter a complete address (10+ characters).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ---------------------------------------------------------------- Wishlist

@Composable
fun WishlistScreen(vm: ShopViewModel, onOpenProduct: (Int) -> Unit, onSignIn: () -> Unit, onShop: () -> Unit) {
    val user by vm.user.collectAsState()
    val state by vm.wishlist.collectAsState()
    LaunchedEffect(user) { vm.loadWishlist() }

    if (user == null) { SignInPrompt("Sign in to save products to your wishlist.", onSignIn); return }
    val data = state.data
    when {
        data == null && state.error != null -> ErrorState(state.error!!) { vm.loadWishlist() }
        data == null -> CenterLoading()
        data.isEmpty() -> MessageState("Nothing saved yet", "Tap ♡ on a product to save it for later.", "Discover products", onShop)
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) { Text("My wishlist", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            items(data, key = { it.id }) { p: Product ->
                ProductCard(p, onClick = { onOpenProduct(p.id) }, onAdd = { vm.addToCart(p, 1) })
            }
        }
    }
}

// ---------------------------------------------------------------- Orders

@Composable
fun OrdersScreen(vm: ShopViewModel, onSignIn: () -> Unit, onShop: () -> Unit) {
    val user by vm.user.collectAsState()
    val state by vm.orders.collectAsState()
    LaunchedEffect(user) { vm.loadOrders() }

    if (user == null) { SignInPrompt("Sign in to see and track your orders.", onSignIn); return }
    val data = state.data
    when {
        data == null && state.error != null -> ErrorState(state.error!!) { vm.loadOrders() }
        data == null -> CenterLoading()
        data.isEmpty() -> MessageState("No orders yet", null, "Start shopping", onShop)
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("My orders", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            items(data, key = { it.id }) { OrderCard(it) }
        }
    }
}

@Composable
private fun OrderCard(order: Order) {
    var open by rememberSaveable(order.id) { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { open = !open },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Order #${order.id}", style = MaterialTheme.typography.titleMedium)
                    Text(order.createdAt.take(10), style = MaterialTheme.typography.bodySmall)
                }
                StatusChip(order.status)
                Text(order.totalAmount.money(), fontWeight = FontWeight.Bold)
            }
            if (open) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                order.items.forEach { LabeledRow("${it.productName} × ${it.quantity}", (it.unitPrice * it.quantity).money()) }
                if (order.discountAmount > 0) {
                    LabeledRow("Discount${order.couponCode?.let { " ($it)" } ?: ""}", "-" + order.discountAmount.money())
                }
                if (order.shippingAddress.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Delivering to: ${order.shippingAddress}", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ---------------------------------------------------------------- Account & auth

@Composable
fun AccountScreen(vm: ShopViewModel, onSignIn: () -> Unit, onRegister: () -> Unit) {
    val user by vm.user.collectAsState()
    val u = user
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (u == null) {
            Text("Welcome to ShopSphere", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Sign in to check out, track orders, and save a wishlist.", Modifier.padding(vertical = 12.dp))
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
            OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Create an account") }
        } else {
            Text(u.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(u.email, Modifier.padding(bottom = 4.dp))
            if (u.role == "admin") {
                Text("Administrator accounts manage the catalog from the website.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        }
    }
}

@Composable
fun AuthScreen(vm: ShopViewModel, register: Boolean, onBack: () -> Unit, onSwitch: () -> Unit, onDone: () -> Unit) {
    val busy by vm.authBusy.collectAsState()
    val error by vm.authError.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(register) { vm.clearAuthError() }

    val valid = email.contains("@") && password.length >= 8 && (!register || name.isNotBlank())
    val submit = {
        if (valid) {
            if (register) vm.register(name, email, password, onDone) else vm.login(email, password, onDone)
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(if (register) "Create account" else "Sign in", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (register) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
            }
            OutlinedTextField(
                email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth(),
                label = { Text(if (register) "Password (8+ characters)" else "Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { submit() }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Please wait…" else if (register) "Create account" else "Sign in")
            }
            TextButton(onClick = onSwitch) { Text(if (register) "Already registered? Sign in" else "New here? Create an account") }
        }
    }
}

// ---------------------------------------------------------------- shared

@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    androidx.compose.material3.TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            androidx.compose.material3.IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}
