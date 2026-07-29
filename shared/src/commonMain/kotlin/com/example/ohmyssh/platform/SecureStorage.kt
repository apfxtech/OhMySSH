package com.example.ohmyssh.platform

expect object SecureStorage {
    suspend fun write(key: String, value: String)
    suspend fun read(key: String): String?
    suspend fun delete(key: String)
    suspend fun containsKey(key: String): Boolean
}
