package com.manfredlabs.atmosfera.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual fun currentTimeMs(): Long = System.currentTimeMillis()

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(AtmosDatabase.Schema, context, "atmosfera_db")
}
