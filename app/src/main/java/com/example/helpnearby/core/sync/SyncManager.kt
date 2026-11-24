package com.example.helpnearby.core.sync

import android.content.Context
import androidx.work.*
import com.example.helpnearby.core.common.AppResult
import com.example.helpnearby.core.database.PendingSyncOperationEntity
import com.example.helpnearby.core.database.SyncQueueDao
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.core.network.NetworkMonitor
import com.example.helpnearby.data.remote.supabase.SupabaseRemoteGateway
import com.example.helpnearby.domain.model.BackendProvider
import com.example.helpnearby.domain.model.SyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncQueueDao: SyncQueueDao,
    private val networkMonitor: NetworkMonitor,
    private val backendConfig: BackendConfig,
    private val supabaseRemoteGateway: SupabaseRemoteGateway?
) {
    constructor(
        context: Context,
        syncQueueDao: SyncQueueDao,
        networkMonitor: NetworkMonitor,
        backendConfig: BackendConfig
    ) : this(context, syncQueueDao, networkMonitor, backendConfig, null)

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    val pendingCount: Flow<Int> = syncQueueDao.observePendingCount()

    suspend fun enqueueOperation(
        entityType: String,
        entityId: String,
        operationType: String,
        payloadJson: String,
        version: Int
    ): String {
        val operationId = "op_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val initialStatus = if (backendConfig.isOfflineLocalOnly) {
            SyncStatus.LOCAL_ONLY.name
        } else {
            SyncStatus.PENDING_CREATE.name
        }
        syncQueueDao.enqueue(
            PendingSyncOperationEntity(
                operationId = operationId,
                entityType = entityType,
                entityId = entityId,
                operationType = operationType,
                payloadJson = payloadJson,
                status = initialStatus,
                retryCount = 0,
                version = version,
                lastAttemptAt = null,
                createdAt = now
            )
        )
        scheduleOneTimeSync()
        return operationId
    }

    fun scheduleOneTimeSync() {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_SYNC_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    fun schedulePeriodicSync() {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodic = PeriodicWorkRequestBuilder<OfflineSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }
    }

    /**
     * Conflict resolution rule:
     * Never overwrite newer server data with stale local edits.
     */
    fun shouldLocalWin(
        localVersion: Int,
        localUpdatedAt: Long,
        remoteVersion: Int,
        remoteUpdatedAt: Long
    ): Boolean {
        return when {
            localVersion > remoteVersion -> true
            localVersion < remoteVersion -> false
            else -> localUpdatedAt >= remoteUpdatedAt
        }
    }

    suspend fun refreshFromCloud(city: String?, userId: String?): AppResult<Unit> {
        if (!backendConfig.isSupabaseConfigured || supabaseRemoteGateway == null) {
            return AppResult.Success(Unit)
        }
        if (!isOnline.value) {
            return AppResult.Failure("Device is offline.")
        }
        return supabaseRemoteGateway.refreshFromCloud(city, userId)
    }

    suspend fun processPendingQueueOnce(): SyncExecutionSummary {
        if (!isOnline.value) {
            return SyncExecutionSummary(processed = 0, succeeded = 0, failed = 0, skippedOffline = true)
        }
        val pending = syncQueueDao.getPendingOperations()
        var succeeded = 0
        var failed = 0

        for (op in pending) {
            val now = System.currentTimeMillis()
            if (backendConfig.isOfflineLocalOnly) {
                syncQueueDao.updateAttempt(
                    operationId = op.operationId,
                    status = SyncStatus.LOCAL_ONLY.name,
                    retryCount = op.retryCount,
                    attemptAt = now
                )
            } else if (backendConfig.activeProvider == BackendProvider.SUPABASE && supabaseRemoteGateway != null) {
                val dispatched = supabaseRemoteGateway.dispatchSyncOperation(op)
                if (dispatched) {
                    syncQueueDao.removeOperation(op.operationId)
                    succeeded++
                } else {
                    syncQueueDao.updateAttempt(
                        operationId = op.operationId,
                        status = SyncStatus.PENDING_UPDATE.name,
                        retryCount = op.retryCount + 1,
                        attemptAt = now
                    )
                    failed++
                }
            } else {
                syncQueueDao.updateAttempt(
                    operationId = op.operationId,
                    status = SyncStatus.PENDING_UPDATE.name,
                    retryCount = op.retryCount + 1,
                    attemptAt = now
                )
                failed++
            }
        }
        return SyncExecutionSummary(
            processed = pending.size,
            succeeded = succeeded,
            failed = failed,
            skippedOffline = false
        )
    }

    companion object {
        private const val UNIQUE_SYNC_WORK = "helpnearby_one_time_sync"
        private const val UNIQUE_PERIODIC_SYNC = "helpnearby_periodic_sync"
    }
}

data class SyncExecutionSummary(
    val processed: Int,
    val succeeded: Int,
    val failed: Int,
    val skippedOffline: Boolean
)
