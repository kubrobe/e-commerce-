package com.shopsphere.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shopsphere.app.ShopApp
import com.shopsphere.app.data.CartEntry
import com.shopsphere.app.data.Category
import com.shopsphere.app.data.Order
import com.shopsphere.app.data.Product
import com.shopsphere.app.data.ProductDetail
import com.shopsphere.app.data.Quote
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Generic "loading / data / error" holder used by every screen. */
data class Load<T>(val loading: Boolean = false, val data: T? = null, val error: String? = null)

class ShopViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as ShopApp).repository

    val user = repo.session.user
    val cart: StateFlow<List<CartEntry>> = repo.cart.entries

    /** One-off messages shown as snackbars. */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<String> = _events.asSharedFlow()

    // ---------- catalog ----------
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _products = MutableStateFlow(Load<List<Product>>(loading = true))
    val products: StateFlow<Load<List<Product>>> = _products.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _categoryId = MutableStateFlow<Int?>(null)
    val categoryId: StateFlow<Int?> = _categoryId.asStateFlow()
    private val _sort = MutableStateFlow("newest")
    val sort: StateFlow<String> = _sort.asStateFlow()

    private var productsJob: Job? = null

    fun setQuery(q: String) { _query.value = q; loadCatalog() }
    fun setCategory(id: Int?) { _categoryId.value = id; loadCatalog() }
    fun setSort(s: String) { _sort.value = s; loadCatalog() }

    fun loadCatalog() {
        productsJob?.cancel()
        productsJob = viewModelScope.launch {
            _products.update { it.copy(loading = true, error = null) }
            repo.products(_query.value, _categoryId.value, _sort.value)
                .onSuccess { list -> _products.value = Load(data = list) }
                .onFailure { e -> _products.update { it.copy(loading = false, error = e.message) } }
        }
    }

    // ---------- product detail ----------
    private val _detail = MutableStateFlow(Load<ProductDetail>(loading = true))
    val detail: StateFlow<Load<ProductDetail>> = _detail.asStateFlow()

    fun loadDetail(id: Int) {
        viewModelScope.launch {
            _detail.value = Load(loading = true)
            repo.product(id)
                .onSuccess { _detail.value = Load(data = it) }
                .onFailure { _detail.value = Load(error = it.message) }
        }
    }

    fun toggleWishlist(productId: Int) {
        val current = _detail.value.data ?: return
        viewModelScope.launch {
            repo.setWishlisted(productId, !current.wishlisted)
                .onSuccess { now ->
                    _detail.update { it.copy(data = it.data?.copy(wishlisted = now)) }
                    _events.tryEmit(if (now) "Saved to your wishlist." else "Removed from your wishlist.")
                    loadWishlist()
                }
                .onFailure { _events.tryEmit(it.message ?: "Could not update wishlist.") }
        }
    }

    fun submitReview(productId: Int, rating: Int, comment: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.review(productId, rating, comment)
                .onSuccess {
                    _events.tryEmit("Your review has been saved.")
                    onDone()
                    repo.product(productId).onSuccess { d -> _detail.value = Load(data = d) }
                }
                .onFailure { _events.tryEmit(it.message ?: "Could not save review.") }
        }
    }

    // ---------- wishlist ----------
    private val _wishlist = MutableStateFlow(Load<List<Product>>())
    val wishlist: StateFlow<Load<List<Product>>> = _wishlist.asStateFlow()

    fun loadWishlist() {
        if (user.value == null) return
        viewModelScope.launch {
            _wishlist.update { it.copy(loading = true, error = null) }
            repo.wishlist()
                .onSuccess { _wishlist.value = Load(data = it) }
                .onFailure { e -> _wishlist.update { it.copy(loading = false, error = e.message) } }
        }
    }

    // ---------- cart ----------
    private val _quote = MutableStateFlow(Load<Quote>())
    val quote: StateFlow<Load<Quote>> = _quote.asStateFlow()
    private val _couponCode = MutableStateFlow("")
    val couponCode: StateFlow<String> = _couponCode.asStateFlow()

    private var quoteJob: Job? = null

    fun addToCart(product: Product, quantity: Int) {
        if (product.stockQuantity < 1) return
        repo.cart.add(product.id, quantity, product.stockQuantity)
        _events.tryEmit("${product.name} added to your cart.")
        refreshQuote()
    }

    fun setCartQuantity(productId: Int, quantity: Int) {
        repo.cart.setQuantity(productId, quantity)
        refreshQuote()
    }

    fun applyCoupon(code: String) {
        _couponCode.value = code.trim().uppercase()
        refreshQuote(announceCoupon = true)
    }

    fun removeCoupon() {
        _couponCode.value = ""
        refreshQuote()
    }

    /** Asks the server to price the cart; also trims quantities / drops items that are out of stock. */
    fun refreshQuote(announceCoupon: Boolean = false) {
        quoteJob?.cancel()
        val entries = repo.cart.entries.value
        if (entries.isEmpty()) {
            _quote.value = Load(data = Quote())
            return
        }
        quoteJob = viewModelScope.launch {
            _quote.update { it.copy(loading = true, error = null) }
            repo.quote(entries, _couponCode.value)
                .onSuccess { q ->
                    // Keep the local cart in line with what the server can actually supply.
                    val synced = q.items.map { CartEntry(it.productId, it.quantity) }
                    if (synced != repo.cart.entries.value) repo.cart.replaceAll(synced)
                    if (q.couponError != null) {
                        _couponCode.value = ""
                        if (announceCoupon) _events.tryEmit(q.couponError)
                    } else if (announceCoupon && q.coupon != null) {
                        _events.tryEmit("${q.coupon.code} applied.")
                    }
                    q.warnings.forEach { _events.tryEmit(it.message) }
                    _quote.value = Load(data = q)
                }
                .onFailure { e -> _quote.update { it.copy(loading = false, error = e.message) } }
        }
    }

    // ---------- checkout & orders ----------
    private val _placing = MutableStateFlow(false)
    val placing: StateFlow<Boolean> = _placing.asStateFlow()

    fun placeOrder(address: String, onSuccess: (Order) -> Unit) {
        val entries = repo.cart.entries.value
        if (entries.isEmpty() || _placing.value) return
        viewModelScope.launch {
            _placing.value = true
            repo.placeOrder(entries, _couponCode.value, address.trim())
                .onSuccess { order ->
                    repo.cart.clear()
                    _couponCode.value = ""
                    _quote.value = Load(data = Quote())
                    _events.tryEmit("Order #${order.id} has been placed successfully.")
                    loadOrders()
                    loadCatalog()
                    onSuccess(order)
                }
                .onFailure {
                    _events.tryEmit(it.message ?: "Could not place the order.")
                    refreshQuote()
                }
            _placing.value = false
        }
    }

    private val _orders = MutableStateFlow(Load<List<Order>>())
    val orders: StateFlow<Load<List<Order>>> = _orders.asStateFlow()

    fun loadOrders() {
        if (user.value == null) return
        viewModelScope.launch {
            _orders.update { it.copy(loading = true, error = null) }
            repo.orders()
                .onSuccess { _orders.value = Load(data = it) }
                .onFailure { e -> _orders.update { it.copy(loading = false, error = e.message) } }
        }
    }

    // ---------- auth ----------
    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun clearAuthError() { _authError.value = null }

    fun login(email: String, password: String, onSuccess: () -> Unit) = auth(onSuccess) { repo.login(email, password) }

    fun register(name: String, email: String, password: String, onSuccess: () -> Unit) =
        auth(onSuccess) { repo.register(name, email, password) }

    private fun auth(onSuccess: () -> Unit, block: suspend () -> Result<*>) {
        if (_authBusy.value) return
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            block()
                .onSuccess {
                    _events.tryEmit("Welcome, ${user.value?.name ?: ""}!")
                    loadOrders(); loadWishlist()
                    onSuccess()
                }
                .onFailure { _authError.value = it.message }
            _authBusy.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            _orders.value = Load()
            _wishlist.value = Load()
            _events.tryEmit("You have been signed out.")
        }
    }

    // Runs last so every state holder above is initialised first.
    init {
        loadCatalog()
        viewModelScope.launch { repo.categories().onSuccess { _categories.value = it } }
        if (repo.cart.entries.value.isNotEmpty()) refreshQuote()
    }
}
