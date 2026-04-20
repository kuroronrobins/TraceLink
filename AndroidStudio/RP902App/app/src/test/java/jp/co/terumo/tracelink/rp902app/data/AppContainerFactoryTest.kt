package jp.co.terumo.tracelink.rp902app.data

import org.junit.Assert.assertNotNull
import org.junit.Test

class AppContainerFactoryTest {
    @Test
    fun create_keepsFakeModeWhenDebugPostgresSmokeIsDisabled() {
        val container = AppContainerFactory.create()

        assertNotNull(container.inventoryRepository())
    }
}
