package com.shopsphere.app.data

import android.content.Context
import com.shopsphere.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = 0) : Exception(message)

class Repository(context: Context) {
    val session = SessionStore(context)
    val cart = CartStore(context)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = session.token
            val request = if (token != null) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else chain.request()
            chain.proceed(request)
        }
        .build()

    private val api: ShopApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ShopApi::class.java)

    private suspend fun <T> call(block: suspend (ShopApi) -> T): Result<T> = try {
        Result.success(block(api))
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        val message = runCatching {
            json.decodeFromString(ErrorBody.serializer(), e.response()?.errorBody()?.string().orEmpty()).error
        }.getOrNull() ?: "Server error (${e.code()})."
        // An expired/revoked token on an authenticated call means we are effectively signed out.
        if (e.code() == 401 && session.token != null) session.clear()
        Result.failure(ApiException(message, e.code()))
    } catch (e: IOException) {
        Result.failure(ApiException("Can't reach the server. Check your connection and the server address."))
    } catch (e: Exception) {
        Result.failure(ApiException(e.message ?: "Unexpected error."))
    }

    suspend fun login(email: String, password: String) =
        call { it.login(LoginRequest(email.trim(), password)) }.onSuccess { session.save(it) }

    suspend fun register(name: String, email: String, password: String) =
        call { it.register(RegisterRequest(name.trim(), email.trim(), password)) }.onSuccess { session.save(it) }

    suspend fun logout() {
        call { it.logout() }
        session.clear()
    }

    suspend fun categories() = call { it.categories() }.map { it.categories }
    suspend fun products(query: String?, category: Int?, sort: String?) =
        call { it.products(query?.takeIf { it.isNotBlank() }, category, sort) }.map { it.products }
    suspend fun product(id: Int) = call { it.product(id) }
    suspend fun review(id: Int, rating: Int, comment: String) = call { it.review(id, ReviewRequest(rating, comment)) }
    suspend fun quote(items: List<CartEntry>, coupon: String?) =
        call { it.quote(QuoteRequest(items, coupon?.takeIf { it.isNotBlank() })) }
    suspend fun placeOrder(items: List<CartEntry>, coupon: String?, address: String) =
        call { it.placeOrder(OrderRequest(items, coupon?.takeIf { it.isNotBlank() }, address)) }.map { it.order }
    suspend fun orders() = call { it.orders() }.map { it.orders }
    suspend fun wishlist() = call { it.wishlist() }.map { it.products }
    suspend fun setWishlisted(id: Int, wishlisted: Boolean) =
        call { if (wishlisted) it.addToWishlist(id) else it.removeFromWishlist(id) }.map { it.wishlisted }
}
