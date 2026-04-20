package jp.co.terumo.tracelink.rp902app.data

import jp.co.terumo.tracelink.rp902app.data.equipment.FakeEquipmentMasterRepository
import jp.co.terumo.tracelink.rp902app.data.inventory.DefaultInventoryRepository
import jp.co.terumo.tracelink.rp902app.data.judgement.SimpleReadJudgementService
import jp.co.terumo.tracelink.rp902app.data.log.InMemoryEventLogStore
import jp.co.terumo.tracelink.rp902app.data.reader.ConfigurableReaderGateway
import jp.co.terumo.tracelink.rp902app.data.reader.bluetooth.InMemoryReaderRuntimeStateRepository
import jp.co.terumo.tracelink.rp902app.data.readresult.DefaultReadResultBundleFactory
import jp.co.terumo.tracelink.rp902app.data.readresult.FakeReadResultRepository
import jp.co.terumo.tracelink.rp902app.data.readresult.InMemoryPendingWriteQueue
import jp.co.terumo.tracelink.rp902app.data.rule.FakeRuleRepository
import jp.co.terumo.tracelink.rp902app.data.settings.InMemoryReaderSettingsRepository
import jp.co.terumo.tracelink.rp902app.data.work.FakeWorkContextRepository
import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentMasterRepository
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryRepository
import jp.co.terumo.tracelink.rp902app.domain.judgement.ReadJudgementService
import jp.co.terumo.tracelink.rp902app.domain.log.EventLogStore
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderRuntimeStateRepository
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettings
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettingsRepository
import jp.co.terumo.tracelink.rp902app.domain.readresult.PendingWriteQueue
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultBundleFactory
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRepository
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleRepository
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContextRepository

/**
 * アプリ全体の依存を組み立てる手動 DI container。
 *
 * 現在は fake / in-memory 実装で起動できる構成にしている。
 * PostgreSQL 接続時は `data.postgres` の repository adapter と実 gateway をここで差し替える。
 */
class AppContainer(
    initialReaderSettings: ReaderSettings = ReaderSettings(),
) {
    private val readerSettingsRepository = InMemoryReaderSettingsRepository(
        initialSettings = initialReaderSettings,
    )
    private val readerRuntimeStateRepository = InMemoryReaderRuntimeStateRepository()

    private val workContextRepository: WorkContextRepository = FakeWorkContextRepository()
    private val ruleRepository: RuleRepository = FakeRuleRepository()
    private val equipmentMasterRepository: EquipmentMasterRepository = FakeEquipmentMasterRepository()
    private val readJudgementService: ReadJudgementService = SimpleReadJudgementService()
    private val readResultBundleFactory: ReadResultBundleFactory = DefaultReadResultBundleFactory()
    private val readResultRepository: ReadResultRepository = FakeReadResultRepository()
    private val pendingWriteQueue: PendingWriteQueue = InMemoryPendingWriteQueue()
    private val eventLogStore: EventLogStore = InMemoryEventLogStore()

    fun readerSettingsRepository(): ReaderSettingsRepository = readerSettingsRepository
    fun readerRuntimeStateRepository(): ReaderRuntimeStateRepository = readerRuntimeStateRepository

    fun inventoryRepository(): InventoryRepository = DefaultInventoryRepository(
        readerGateway = ConfigurableReaderGateway(
            settingsRepository = readerSettingsRepository,
            runtimeStateRepository = readerRuntimeStateRepository,
        ),
        workContextRepository = workContextRepository,
        ruleRepository = ruleRepository,
        equipmentMasterRepository = equipmentMasterRepository,
        readJudgementService = readJudgementService,
        readResultBundleFactory = readResultBundleFactory,
        readResultRepository = readResultRepository,
        pendingWriteQueue = pendingWriteQueue,
        eventLogStore = eventLogStore,
    )
}
