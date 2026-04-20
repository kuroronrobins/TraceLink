package jp.co.terumo.tracelink.rp902app.domain.inventory

import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderTagRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InventorySessionTest {
    @Test
    fun record_mergesDuplicateEpcInSameSession() {
        val session = InventorySession()

        session.record(ReaderTagRead(epc = "E2806894000040035A1F90A1", seenAtEpochMillis = 1000L))
        val tags = session.record(
            ReaderTagRead(epc = "e2806894000040035a1f90a1", seenAtEpochMillis = 2500L),
        )

        assertEquals(1, tags.size)
        assertEquals("E2806894000040035A1F90A1", tags.single().epc)
        assertEquals(1000L, tags.single().firstSeenAtEpochMillis)
        assertEquals(2500L, tags.single().lastSeenAtEpochMillis)
        assertEquals(2, tags.single().readCount)
    }

    @Test
    fun record_keepsDifferentEpcsAsSeparateRows() {
        val session = InventorySession()

        session.record(ReaderTagRead(epc = "E2806894000040035A1F90A1", seenAtEpochMillis = 1000L))
        session.record(ReaderTagRead(epc = "E2806894000050035A1F90A2", seenAtEpochMillis = 1500L))
        val tags = session.snapshot()

        assertEquals(2, tags.size)
        assertEquals("E2806894000040035A1F90A1", tags[0].epc)
        assertEquals("E2806894000050035A1F90A2", tags[1].epc)
    }

    @Test
    fun record_normalizesEpcWhitespaceAndCaseBeforeFiltering() {
        val session = InventorySession()

        session.record(ReaderTagRead(epc = " e2806894000040035a1f90a1 ", seenAtEpochMillis = 1000L))
        val tags = session.record(
            ReaderTagRead(epc = "E2806894000040035A1F90A1", seenAtEpochMillis = 2000L),
        )

        assertEquals(1, tags.size)
        assertEquals("E2806894000040035A1F90A1", tags.single().epc)
        assertEquals(2, tags.single().readCount)
    }

    @Test
    fun record_rejectsBlankEpc() {
        val session = InventorySession()

        assertThrows(IllegalArgumentException::class.java) {
            session.record(ReaderTagRead(epc = "   ", seenAtEpochMillis = 1000L))
        }
    }

    @Test
    fun clear_resetsDuplicateFilteringForNextSession() {
        val session = InventorySession()

        session.record(ReaderTagRead(epc = "E2806894000040035A1F90A1", seenAtEpochMillis = 1000L))
        session.record(ReaderTagRead(epc = "E2806894000040035A1F90A1", seenAtEpochMillis = 2000L))
        session.clear()
        val tags = session.record(
            ReaderTagRead(epc = "E2806894000040035A1F90A1", seenAtEpochMillis = 3000L),
        )

        assertEquals(1, tags.size)
        assertEquals(3000L, tags.single().firstSeenAtEpochMillis)
        assertEquals(3000L, tags.single().lastSeenAtEpochMillis)
        assertEquals(1, tags.single().readCount)
    }

    @Test
    fun toRegistrationBundle_usesCurrentSessionTags() {
        val session = InventorySession()
        session.record(ReaderTagRead(epc = "E2806894000040035A1F90A1", seenAtEpochMillis = 1000L))
        session.record(ReaderTagRead(epc = "E2806894000040035A1F90A1", seenAtEpochMillis = 2000L))

        val bundle = session.toRegistrationBundle(
            sessionId = "session-1",
            registeredAtEpochMillis = 3000L,
            deviceId = "device-1",
            readerType = "RP902",
        )

        assertEquals("session-1", bundle.sessionId)
        assertEquals(1, bundle.tags.size)
        assertEquals(2, bundle.tags.single().readCount)
    }
}
