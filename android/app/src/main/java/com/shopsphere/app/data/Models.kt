package com.shopsphere.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(val id: Int, val name: String, val email: String, val role: String = "customer")

@Serializable
data class AuthResponse(val token: String, val user: User)

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class Category(val id: Int, val name: String, val slug: String = "")

@Serializable
data class CategoriesResponse(val categories: List<Category> = emptyList())

@Serializable
data class Product(
    val id: Int,
    @SerialName("category_id") val categoryId: Int = 0,
    @SerialName("category_name") val categoryName: String? = null,
    val name: String,
    val description: String = "",
    val price: Double,
    @SerialName("stock_quantity") val stockQuantity: Int = 0,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("rating_avg") val ratingAvg: Double = 0.0,
    @SerialName("review_count") val reviewCount: Int = 0,
)

@Serializable
data class ProductsResponse(val products: List<Product> = emptyList())

@Serializable
data class Review(
    val id: Int,
    @SerialName("user_id") val userId: Int,
    @SerialName("user_name") val userName: String,
    val rating: Int,
    val comment: String,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class ProductDetail(
    val product: Product,
    val reviews: List<Review> = emptyList(),
    val wishlisted: Boolean = false,
)

@Serializable
data class ReviewRequest(val rating: Int, val comment: String)

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class WishlistResponse(val wishlisted: Boolean = false)

/** One cart line as stored on the phone: just the product and how many. */
@Serializable
data class CartEntry(
    @SerialName("product_id") val productId: Int,
    val quantity: Int,
)

@Serializable
data class QuoteRequest(
    val items: List<CartEntry>,
    @SerialName("coupon_code") val couponCode: String? = null,
)

@Serializable
data class QuoteLine(
    @SerialName("product_id") val productId: Int,
    val name: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("unit_price") val unitPrice: Double,
    val quantity: Int,
    @SerialName("stock_quantity") val stockQuantity: Int = 0,
    @SerialName("line_total") val lineTotal: Double,
)

@Serializable
data class CouponInfo(val code: String, @SerialName("discount_amount") val discountAmount: Double = 0.0)

@Serializable
data class QuoteWarning(@SerialName("product_id") val productId: Int, val message: String)

@Serializable
data class Quote(
    val items: List<QuoteLine> = emptyList(),
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val coupon: CouponInfo? = null,
    @SerialName("coupon_error") val couponError: String? = null,
    val warnings: List<QuoteWarning> = emptyList(),
)

@Serializable
data class OrderRequest(
    val items: List<CartEntry>,
    @SerialName("coupon_code") val couponCode: String? = null,
    @SerialName("shipping_address") val shippingAddress: String,
)

@Serializable
data class OrderItem(
    @SerialName("product_id") val productId: Int,
    @SerialName("product_name") val productName: String,
    @SerialName("unit_price") val unitPrice: Double,
    val quantity: Int,
)

@Serializable
data class Order(
    val id: Int,
    val status: String,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("coupon_code") val couponCode: String? = null,
    @SerialName("shipping_address") val shippingAddress: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val items: List<OrderItem> = emptyList(),
)

@Serializable
data class OrderResponse(val order: Order)

@Serializable
data class OrdersResponse(val orders: List<Order> = emptyList())

@Serializable
data class ErrorBody(val error: String? = null)
