package com.example.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VpnServer(
    val id: String,
    val country: String,
    val city: String,
    val ip_address: String,
    val is_premium: Boolean,
    val status: String,
    val load_percent: Int,
    val wg_public_key: String? = null,
    val wg_endpoint: String? = null
)

@JsonClass(generateAdapter = true)
data class User(
    val id: String,
    val email: String,
    val is_premium: Boolean,
    val subscription_expiry: String?
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val access_token: String,
    val token_type: String,
    val is_premium: Boolean
)

