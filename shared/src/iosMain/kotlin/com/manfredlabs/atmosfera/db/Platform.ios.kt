package com.manfredlabs.atmosfera.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import platform.Foundation.NSDate

actual fun currentTimeMs(): Long = (NSDate.date().timeIntervalSince1970 * 1000).toLong()

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(AtmosDatabase.Schema, "atmosfera_db")
}
