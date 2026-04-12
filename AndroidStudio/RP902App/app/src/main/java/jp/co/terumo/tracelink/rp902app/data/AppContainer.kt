package jp.co.terumo.tracelink.rp902app.data

import jp.co.terumo.tracelink.rp902app.data.inventory.DefaultInventoryRepository
import jp.co.terumo.tracelink.rp902app.data.reader.ConfigurableReaderGateway
import jp.co.terumo.tracelink.rp902app.data.settings.InMemoryReaderSettingsRepository
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryRepository
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettings
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettingsRepository

class AppContainer(
    initialReaderSettings: ReaderSettings = ReaderSettings(),
) {
    private val readerSettingsRepository = InMemoryReaderSettingsRepository(
        initialSettings = initialReaderSettings,
    )

    fun readerSettingsRepository(): ReaderSettingsRepository = readerSettingsRepository

    fun inventoryRepository(): InventoryRepository = DefaultInventoryRepository(
        readerGateway = ConfigurableReaderGateway(
            settingsRepository = readerSettingsRepository,
        ),
    )
}
