package jp.co.terumo.tracelink.rp902app.data.postgres

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import jp.co.terumo.tracelink.rp902app.domain.database.PostgresGateway
import jp.co.terumo.tracelink.rp902app.domain.equipment.EquipmentSnapshot
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationBundle
import jp.co.terumo.tracelink.rp902app.domain.readresult.ReadResultRegistrationResult
import jp.co.terumo.tracelink.rp902app.domain.rule.RuleBundle
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JDBC based PostgreSQL gateway.
 *
 * Connections are opened per operation so Android lifecycle events do not leave long-lived DB sessions
 * behind the repository facade.
 */
class JdbcPostgresGateway(
    private val connectionSettings: PostgresConnectionSettings,
    private val connectionFactory: JdbcConnectionFactory = DriverManagerJdbcConnectionFactory(
        connectionSettings,
    ),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PostgresGateway {
    override suspend fun fetchWorkContext(): WorkContext =
        execute("fetch work context") { connection ->
            connection.prepareStatement(PostgresSqlStatements.FetchWorkContext).use { statement ->
                statement.queryTimeout = connectionSettings.queryTimeoutSeconds()
                statement.executeQuery().use { resultSet ->
                    require(resultSet.next()) {
                        "Active work context view returned no rows."
                    }
                    PostgresRowMappers.workContext(
                        resultSet.toPostgresRow(PostgresRowMappers.WorkContextColumns),
                    )
                }
            }
        }

    override suspend fun fetchRuleBundle(workContext: WorkContext): RuleBundle =
        execute("fetch rule bundle") { connection ->
            connection.prepareStatement(PostgresSqlStatements.FetchRuleBundle).use { statement ->
                statement.queryTimeout = connectionSettings.queryTimeoutSeconds()
                statement.setString(1, workContext.workId)
                statement.executeQuery().singleRow(
                    columns = PostgresRowMappers.RuleBundleColumns,
                    emptyMessage = "Rule bundle function returned no rows for workId=${workContext.workId}.",
                    mapper = PostgresRowMappers::ruleBundle,
                )
            }
        }

    override suspend fun fetchEquipmentSnapshot(workContext: WorkContext): EquipmentSnapshot =
        execute("fetch equipment snapshot") { connection ->
            connection.prepareStatement(PostgresSqlStatements.FetchEquipmentSnapshot).use { statement ->
                statement.queryTimeout = connectionSettings.queryTimeoutSeconds()
                statement.setString(1, workContext.workId)
                statement.executeQuery().use { resultSet ->
                    val rows = resultSet.toRows(PostgresRowMappers.EquipmentSnapshotColumns)
                    PostgresRowMappers.equipmentSnapshot(rows)
                }
            }
        }

    override suspend fun registerReadResults(
        bundle: ReadResultRegistrationBundle,
    ): ReadResultRegistrationResult {
        val result = runCatching {
            execute("register read results") { connection ->
                connection.prepareStatement(PostgresSqlStatements.RegisterReadResultBundle).use { statement ->
                    statement.queryTimeout = connectionSettings.queryTimeoutSeconds()
                    statement.setString(1, ReadResultBundleJsonEncoder.encode(bundle))
                    statement.executeQuery().singleRow(
                        columns = PostgresRowMappers.RegistrationResultColumns,
                        emptyMessage = "Read result registration function returned no rows.",
                        mapper = PostgresRowMappers::registrationResult,
                    )
                }
            }
        }

        return result.getOrElse { throwable ->
            val gatewayException = PostgresErrorMapper.toGatewayException(
                operation = "register read results",
                throwable = throwable,
            )
            ReadResultRegistrationResult.Failure(
                message = gatewayException.message.orEmpty(),
                kind = PostgresErrorMapper.toRegistrationFailureKind(gatewayException.kind),
            )
        }
    }

    private suspend fun <T> execute(
        operation: String,
        block: (Connection) -> T,
    ): T = withContext(ioDispatcher) {
        try {
            connectionFactory.open().use(block)
        } catch (throwable: Throwable) {
            throw PostgresErrorMapper.toGatewayException(operation, throwable)
        }
    }
}

interface JdbcConnectionFactory {
    fun open(): Connection
}

class DriverManagerJdbcConnectionFactory(
    private val connectionSettings: PostgresConnectionSettings,
) : JdbcConnectionFactory {
    override fun open(): Connection {
        Class.forName("org.postgresql.Driver")
        return DriverManager.getConnection(
            connectionSettings.jdbcUrl(),
            connectionSettings.jdbcProperties(),
        )
    }
}

private fun <T> ResultSet.singleRow(
    columns: List<String>,
    emptyMessage: String,
    mapper: (PostgresRow) -> T,
): T = use { resultSet ->
    require(resultSet.next()) { emptyMessage }
    mapper(resultSet.toPostgresRow(columns))
}

private fun ResultSet.toRows(columns: List<String>): List<PostgresRow> {
    val rows = mutableListOf<PostgresRow>()
    while (next()) {
        rows += toPostgresRow(columns)
    }
    return rows
}
