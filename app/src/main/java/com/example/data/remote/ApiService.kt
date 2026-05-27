package com.example.data.remote

import com.example.model.VpnServer
import com.example.model.User
import com.example.model.LoginRequest
import com.example.model.LoginResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body

interface ApiService {
    @GET("/servers")
    suspend fun getServers(): List<VpnServer>

    @GET("/servers/wg-demo")
    suspend fun getWgDemoServer(): VpnServer

    @GET("/users/me")
    suspend fun getCurrentUser(): User

    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}
