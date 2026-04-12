package jp.co.terumo.tracelink.rp902app.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderBluetoothAddressTest {
    @Test
    fun parse_acceptsColonSeparatedAddressAndNormalizesCase() {
        val address = ReaderBluetoothAddress.parse("00:11:aa:33:44:ff")

        assertEquals("00:11:AA:33:44:FF", address?.value)
    }

    @Test
    fun parse_acceptsCompactAddressAndInsertsColons() {
        val address = ReaderBluetoothAddress.parse("0011aa3344ff")

        assertEquals("00:11:AA:33:44:FF", address?.value)
    }

    @Test
    fun parse_rejectsInvalidAddress() {
        assertNull(ReaderBluetoothAddress.parse("00:11:22"))
        assertNull(ReaderBluetoothAddress.parse("not-a-mac"))
    }
}
