package com.manfredlabs.atmosfera.db

import app.cash.sqldelight.db.SqlDriver

expect fun currentTimeMs(): Long

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
