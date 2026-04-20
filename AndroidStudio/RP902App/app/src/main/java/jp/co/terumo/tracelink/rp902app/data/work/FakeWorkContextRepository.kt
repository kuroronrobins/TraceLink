package jp.co.terumo.tracelink.rp902app.data.work

import jp.co.terumo.tracelink.rp902app.domain.work.WorkContext
import jp.co.terumo.tracelink.rp902app.domain.work.WorkContextRepository

/**
 * PostgreSQL 未接続期間に使う固定 work context。
 */
class FakeWorkContextRepository(
    private val workContext: WorkContext = WorkContext(
        workId = "fake-work-001",
        reportId = "fake-report-001",
        operatorId = "fake-operator",
        startedAtEpochMillis = 0L,
    ),
) : WorkContextRepository {
    override suspend fun resolveCurrentWorkContext(): WorkContext = workContext
}
