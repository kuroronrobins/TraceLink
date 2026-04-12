package jp.co.terumo.tracelink.rp902app.data.reader.real

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Rp902TagEventMapperTest {
    @Test
    fun map_validTagCreatesReaderTagRead() {
        val mapping = Rp902TagEventMapper.map(
            rawTag = " E2806894000040035A1F90A1 ",
            params = null,
            seenAtEpochMillis = 1234L,
            callbackIndex = 1L,
        )

        val read = mapping.read

        assertNotNull(read)
        assertEquals("E2806894000040035A1F90A1", read?.epc)
        assertEquals(1234L, read?.seenAtEpochMillis)
        assertNull(mapping.failureMessage)
        assertTrue(mapping.diagnosticMessage.contains("rawLength=26"))
    }

    @Test
    fun map_blankTagIsIgnoredWithoutThrowing() {
        val mapping = Rp902TagEventMapper.map(
            rawTag = " ",
            params = Any(),
            seenAtEpochMillis = 1234L,
            callbackIndex = 2L,
        )

        assertNull(mapping.read)
        assertEquals("RP902 tag event ignored: EPC is blank.", mapping.failureMessage)
        assertTrue(mapping.diagnosticMessage.contains("paramsType=java.lang.Object"))
    }

    @Test
    fun map_overSizedTagIsIgnoredWithoutThrowing() {
        val mapping = Rp902TagEventMapper.map(
            rawTag = "A".repeat(513),
            params = null,
            seenAtEpochMillis = 1234L,
            callbackIndex = 3L,
        )

        assertNull(mapping.read)
        assertEquals("RP902 tag event ignored: EPC is too long (513).", mapping.failureMessage)
    }
}
