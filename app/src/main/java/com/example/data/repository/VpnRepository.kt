package com.example.data.repository

import com.example.data.local.ServerDao
import com.example.data.local.toDomainModel
import com.example.data.local.toEntityModel
import com.example.data.remote.ApiService
import com.example.model.VpnServer
import com.example.model.LoginRequest
import com.example.model.LoginResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException

class VpnRepository(
    private val apiService: ApiService,
    private val serverDao: ServerDao
) {
    val servers: Flow<List<VpnServer>> = serverDao.getAllServers().map { entities ->
        entities.map { it.toDomainModel() }
    }

    suspend fun refreshServers(): Result<Unit> {
        return try {
            val remoteServers = apiService.getServers()
            
            // For demo purposes, we will also fetch the WG demo server and add it to our list
            val allServers = remoteServers.toMutableList()
            try {
                val wgDemo = apiService.getWgDemoServer()
                if (!allServers.any { it.id == wgDemo.id }) {
                    allServers.add(wgDemo)
                }
            } catch (e: Exception) {
                // Ignore missing demo server
                e.printStackTrace()
            }

            serverDao.insertServers(allServers.map { it.toEntityModel() })
            Result.success(Unit)
        } catch (e: IOException) {
            // Network error
            Result.failure(e)
        } catch (e: HttpException) {
            // API error
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, pass: String): Result<LoginResponse> {
        return try {
            val res = apiService.login(LoginRequest(email, pass))
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signup(email: String, pass: String): Result<LoginResponse> {
        return try {
            val res = apiService.signup(LoginRequest(email, pass))
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
