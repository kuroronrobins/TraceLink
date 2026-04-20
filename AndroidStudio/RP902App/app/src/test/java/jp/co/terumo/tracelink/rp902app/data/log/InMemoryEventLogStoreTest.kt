package jp.co.terumo.tracelink.rp902app.data.log

import jp.co.terumo.tracelink.rp902app.domain.log.AppLogCategory
import jp.co.terumo.tracelink.rp902app.domain.log.AppLogLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryEventLogStoreTest {
    @Test
    fun append_keepsStructuredEntriesNewestFirstWithMaxLimit() = runBlocking {
        val store = InMemoryEventLogStore(maxEntries = 2)

        store.append(
            occurredAtEpochMillis = 1000L,
            level = AppLogLevel.Info,
            category = AppLogCategory.System,
            message = "first",
        )
        store.append(
            occurredAtEpochMillis = 2000L,
            level = AppLogLevel.Warning,
            category = AppLogCategory.ResultRegistration,
            message = "second",
        )
        store.append(
            occurredAtEpochMillis = 3000L,
            level = AppLogLevel.Error,
            category = AppLogCategory.Reader,
            message = "third",
        )

        val logs = store.logs.value

        assertEquals(2, logs.size)
        assertEquals("third", logs[0].message)
        assertEquals(AppLogLevel.Error, logs[0].level)
        assertEquals(AppLogCategory.Reader, logs[0].category)
        assertEquals("second", logs[1].message)
    }
}
