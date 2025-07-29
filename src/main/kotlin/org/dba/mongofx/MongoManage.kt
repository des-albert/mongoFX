package org.dba.mongofx

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import org.slf4j.LoggerFactory

object MongoManage {
    private var client: MongoClient? = null

    private const val DATABASE_NAME = "config"
    private var database: MongoDatabase? = null
    lateinit var collection: MongoCollection<Config>

    private val logger = LoggerFactory.getLogger(MongoManage::class.java)

    fun connect() {
        if (client != null) {
            logger.warn("Attempted to connect, but already connected to MongoDB.")
            return
        }
        try {
            logger.info("Connecting to MongoDB at mongodb://localhost:27017")
            client = MongoClient.create("mongodb://localhost:27017")
            database = client?.getDatabase(DATABASE_NAME)
            collection = database?.getCollection<Config>("earlyConfigs")
                ?: throw IllegalStateException("MongoDB is not connected. Call MongoManage.connect() first.")
        } catch (e: Exception) {
            logger.error("Failed to connect to MongoDB: ${e.message}")
        }
    }

    fun close() {

        client?.let {
            logger.info("Closing MongoDB connection...")
            it.close()
            client = null
            database = null
            logger.info("MongoDB connection closed.")

        }
    }
}