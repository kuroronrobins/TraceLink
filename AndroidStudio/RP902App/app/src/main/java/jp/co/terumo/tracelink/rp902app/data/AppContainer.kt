package jp.co.terumo.tracelink.rp902app.data

import jp.co.terumo.tracelink.rp902app.data.equipment.FakeEquipmentMasterRepository
import jp.co.terumo.tracelink.rp902app.data.inventory.DefaultInventoryRepository
import jp.co.terumo.tracelink.rp902app.data.judgement.SimpleReadJudgementService
import jp.co.terumo.tracelink.rp902app.data.log.InMemoryEventLogStore
import jp.co.terumo.tracelink.rp902app.data.postgres.JdbcPostgresGateway
import jp.co.terumo.tracelink.rp902app.data.postgres.PostgresConnectionSettings
import jp.co.terumo.tracelink.rp902app.data.postgres.PostgresEquipmentMasterRepository
import jp.co.terumo.tracelink.rp902app.data.postgres.PostgresReadResultRepository
import jp.co.terumo.tracelink.rp902app.data.postgres.PostgresRuleRepository
import jp.co.terumo.tracelink.rp902app.data.postgres.PostgresWorkContextRepository
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
import jp.co.terumo.tracelink.rp902app.domain.readresult.RegistrationEnvironment
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
    private val dataAccessMode: DataAccessMode = DataAccessMode.Fake,
    private val postgresConnectionSettings: PostgresConnectionSettings? = null,
    private val registrationEnvironment: RegistrationEnvironment = RegistrationEnvironment(),
) {
    private val readerSettingsRepository = InMemoryReaderSettingsRepository(
        initialSettings = initialReaderSettings,
    )
    private val readerRuntimeStateRepository = InMemoryReaderRuntimeStateRepository()

    private val postgresGateway = when (dataAccessMode) {
        DataAccessMode.Fake -> null
        DataAccessMode.Postgres -> JdbcPostgresGateway(requirePostgresConnectionSettings())
    }

    private val workContextRepository: WorkContextRepository = when (dataAccessMode) {
        DataAccessMode.Fake -> FakeWorkContextRepository()
        DataAccessMode.Postgres -> PostgresWorkContextRepository(requireNotNull(postgresGateway))
    }
    private val ruleRepository: RuleRepository = when (dataAccessMode) {
        DataAccessMode.Fake -> FakeRuleRepository()
        DataAccessMode.Postgres -> PostgresRuleRepository(requireNotNull(postgresGateway))
    }
    private val equipmentMasterRepository: EquipmentMasterRepository = when (dataAccessMode) {
        DataAccessMode.Fake -> FakeEquipmentMasterRepository()
        DataAccessMode.Postgres -> PostgresEquipmentMasterRepository(requireNotNull(postgresGateway))
    }
    private val readJudgementService: ReadJudgementService = SimpleReadJudgementService()
    private val readResultBundleFactory: ReadResultBundleFactory = DefaultReadResultBundleFactory()
    private val readResultRepository: ReadResultRepository = when (dataAccessMode) {
        DataAccessMode.Fake -> FakeReadResultRepository()
        DataAccessMode.Postgres -> PostgresReadResultRepository(requireNotNull(postgresGateway))
    }
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
        registrationEnvironment = registrationEnvironment,
    )

    private fun requirePostgresConnectionSettings(): PostgresConnectionSettings =
        requireNotNull(postgresConnectionSettings) {
            "PostgreSQL mode requires PostgresConnectionSettings. Fake mode remains the default."
        }
}
