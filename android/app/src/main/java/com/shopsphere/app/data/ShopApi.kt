package com.shopsphere.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ShopApi {
    @POST("auth/register") suspend fun register(@Body body: RegisterRequest): AuthResponse
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): AuthResponse
    @POST("auth/logout") suspend fun logout(): OkResponse

    @GET("categories") suspend fun categories(): CategoriesResponse

    @GET("products")
    suspend fun products(
        @Query("q") query: String?,
        @Query("category") category: Int?,
        @Query("sort") sort: String?,
    ): ProductsResponse

    @GET("products/{id}") suspend fun product(@Path("id") id: Int): ProductDetail
    @POST("products/{id}/reviews") suspend fun review(@Path("id") id: Int, @Body body: ReviewRequest): OkResponse

    @POST("cart/quote") suspend fun quote(@Body body: QuoteRequest): Quote

    @POST("orders") suspend fun placeOrder(@Body body: OrderRequest): OrderResponse
    @GET("orders") suspend fun orders(): OrdersResponse

    @GET("wishlist") suspend fun wishlist(): ProductsResponse
    @PUT("wishlist/{id}") suspend fun addToWishlist(@Path("id") id: Int): WishlistResponse
    @DELETE("wishlist/{id}") suspend fun removeFromWishlist(@Path("id") id: Int): WishlistResponse
}
