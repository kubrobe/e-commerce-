package com.shopsphere.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val storeJson = Json { ignoreUnknownKeys = true }

/** Remembers the signed-in user and API token between launches. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val _user = MutableStateFlow(loadUser())
    val user: StateFlow<User?> = _user.asStateFlow()

    val token: String? get() = prefs.getString("token", null)

    fun save(response: AuthResponse) {
        prefs.edit()
            .putString("token", response.token)
            .putString("user", storeJson.encodeToString(User.serializer(), response.user))
            .apply()
        _user.value = response.user
    }

    fun clear() {
        prefs.edit().clear().apply()
        _user.value = null
    }

    private fun loadUser(): User? {
        if (prefs.getString("token", null) == null) return null
        val raw = prefs.getString("user", null) ?: return null
        return runCatching { storeJson.decodeFromString(User.serializer(), raw) }.getOrNull()
    }
}

/** The cart lives on the phone; the server prices it on demand. */
class CartStore(context: Context) {
    private val prefs = context.getSharedPreferences("cart", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(CartEntry.serializer())

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<CartEntry>> = _entries.asStateFlow()

    fun add(productId: Int, quantity: Int, maxStock: Int) {
        val current = _entries.value
        val existing = current.firstOrNull { it.productId == productId }
        val newQty = ((existing?.quantity ?: 0) + quantity).coerceIn(1, maxOf(1, maxStock))
        set(if (existing == null) current + CartEntry(productId, newQty)
            else current.map { if (it.productId == productId) it.copy(quantity = newQty) else it })
    }

    fun setQuantity(productId: Int, quantity: Int) {
        set(if (quantity <= 0) _entries.value.filterNot { it.productId == productId }
            else _entries.value.map { if (it.productId == productId) it.copy(quantity = quantity) else it })
    }

    fun replaceAll(entries: List<CartEntry>) = set(entries)

    fun clear() = set(emptyList())

    private fun set(list: List<CartEntry>) {
        _entries.value = list
        prefs.edit().putString("entries", storeJson.encodeToString(serializer, list)).apply()
    }

    private fun load(): List<CartEntry> {
        val raw = prefs.getString("entries", null) ?: return emptyList()
        return runCatching { storeJson.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
