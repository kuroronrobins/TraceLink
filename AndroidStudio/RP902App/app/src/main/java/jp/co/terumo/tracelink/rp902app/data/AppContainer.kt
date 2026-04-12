package jp.co.terumo.tracelink.rp902app.data

import jp.co.terumo.tracelink.rp902app.data.inventory.DefaultInventoryRepository
import jp.co.terumo.tracelink.rp902app.data.reader.ConfigurableReaderGateway
import jp.co.terumo.tracelink.rp902app.data.reader.bluetooth.InMemoryReaderRuntimeStateRepository
import jp.co.terumo.tracelink.rp902app.data.settings.InMemoryReaderSettingsRepository
import jp.co.terumo.tracelink.rp902app.domain.inventory.InventoryRepository
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderRuntimeStateRepository
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettings
import jp.co.terumo.tracelink.rp902app.domain.reader.ReaderSettingsRepository

/**
 * アプリ全体で使う依存関係を組み立てる手動 DI コンテナ。
 *
 * 現在は小さな実装 slice のため DI framework は導入せず、ここで Repository や Gateway を作る。
 * `ReaderSettings()` の既定 gateway mode は fake reader なので、実機 RP902 がない環境でも起動とテストができる。
 * Bluetooth MAC は今回運用の 1 台固定値で初期化されるが、real 接続は明示選択時だけ行う。
 *
 * 将来、upload 実装、retry queue 永続化、settings 永続化を差し替える場合も、
 * まずこのファイルを見ると app 全体へのつながりを追いやすい。
 */
class AppContainer(
    initialReaderSettings: ReaderSettings = ReaderSettings(),
) {
    private val readerSettingsRepository = InMemoryReaderSettingsRepository(
        initialSettings = initialReaderSettings,
    )
    private val readerRuntimeStateRepository = InMemoryReaderRuntimeStateRepository()

    fun readerSettingsRepository(): ReaderSettingsRepository = readerSettingsRepository
    fun readerRuntimeStateRepository(): ReaderRuntimeStateRepository = readerRuntimeStateRepository

    /**
     * Inventory 機能の root repository を作る。
     *
     * UI や ViewModel は fake/real の詳細を知らず、ここで作る `ConfigurableReaderGateway` が
     * Settings の値に応じて実際の reader 実装を切り替える。
     */
    fun inventoryRepository(): InventoryRepository = DefaultInventoryRepository(
        readerGateway = ConfigurableReaderGateway(
            settingsRepository = readerSettingsRepository,
            runtimeStateRepository = readerRuntimeStateRepository,
        ),
    )
}
