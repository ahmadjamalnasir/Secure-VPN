package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.VpnServer

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val country: String,
    val city: String,
    val ip_address: String,
    val is_premium: Boolean,
    val status: String,
    val load_percent: Int,
    val wg_public_key: String? = null,
    val wg_endpoint: String? = null
)

// Extension function to map Entity to Domain Model
fun ServerEntity.toDomainModel() = VpnServer(
    id = id,
    country = country,
    city = city,
    ip_address = ip_address,
    is_premium = is_premium,
    status = status,
    load_percent = load_percent,
    wg_public_key = wg_public_key,
    wg_endpoint = wg_endpoint
)

fun VpnServer.toEntityModel() = ServerEntity(
    id = id,
    country = country,
    city = city,
    ip_address = ip_address,
    is_premium = is_premium,
    status = status,
    load_percent = load_percent,
    wg_public_key = wg_public_key,
    wg_endpoint = wg_endpoint
)
