package org.dba.mongofx

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase

object MongoManage {
    private lateinit var client: MongoClient

    private const val DATABASE_NAME = "config"
    lateinit var database: MongoDatabase
        private set
    lateinit var collection: MongoCollection<Config>

    fun connect() {
        try {
            client = MongoClient.create("mongodb://localhost:27017")
            database = client.getDatabase(DATABASE_NAME)
            collection = database.getCollection<Config>("earlyConfigs")
        } catch (e: Exception) {
            println("Failed to connect to MongoDB: ${e.message}")
            e.printStackTrace()
        }
    }

    fun close() {

        if (::client.isInitialized) {
            println("Closing MongoDB connection...")
            client.close()
            println("MongoDB connection closed.")


        }
    }
}