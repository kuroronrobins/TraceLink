package jp.co.terumo.tracelink.rp902app.data

import jp.co.terumo.tracelink.rp902app.data.inventory.DefaultInventoryRepository
import jp.co.terumo.tracelink.rp902app.data.reader.FakeReaderGateway
import jp.co.terumo.tracelink.rp902app.data.reader.real.RealRp902Gateway
import jp.co.terumo.tracelink.rp902app.data.reader.real.RealRp902GatewayConfiguration
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryRepository
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderGateway

class AppContainer(
    private val readerGatewayMode: ReaderGatewayMode = ReaderGatewayMode.Fake,
    private val realRp902GatewayConfiguration: RealRp902GatewayConfiguration =
        RealRp902GatewayConfiguration(),
) {
    fun inventoryRepository(): InventoryRepository = DefaultInventoryRepository(
        readerGateway = readerGateway(),
    )

    private fun readerGateway(): ReaderGateway = when (readerGatewayMode) {
        ReaderGatewayMode.Fake -> FakeReaderGateway()
        ReaderGatewayMode.RealRp902 -> RealRp902Gateway(realRp902GatewayConfiguration)
    }
}

enum class ReaderGatewayMode {
    Fake,
    RealRp902,
}
