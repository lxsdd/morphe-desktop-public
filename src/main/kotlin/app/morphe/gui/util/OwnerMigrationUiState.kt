package app.morphe.gui.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.morphe.engine.util.AdbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One shared, explicitly opted-in migration state for Simple and Expert results. */
class OwnerMigrationUiState(private val manager: AdbManager) {
    var request by mutableStateOf<UpdateOwnerMigrationRequest?>(null)
        private set
    var dialogVisible by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun recordFailure(cause: Throwable?, target: DeviceOperationTarget, path: String) {
        request = withContext(Dispatchers.IO) { migrationRequestOrNull(cause, target, path) }
        dialogVisible = false
        error = null
    }

    fun showDialog() {
        if (!busy && request != null) {
            error = null
            dialogVisible = true
        }
    }

    fun dismiss() {
        if (!busy) {
            dialogVisible = false
            error = null
        }
    }

    fun clear() {
        if (!busy) {
            request = null
            dialogVisible = false
            error = null
        }
    }

    /**
     * Reserve the migration synchronously in the click handler so another install
     * cannot start before a coroutine reaches the first suspension point.
     */
    fun beginConfirmation(): Boolean {
        if (busy || request == null || !dialogVisible) return false
        busy = true
        return true
    }

    suspend fun confirm(selectedDeviceSerial: String?): UpdateOwnerMigrationResult {
        if (!busy) return UpdateOwnerMigrationResult.Blocked("Confirmation was not started.")
        return try {
            val pending = request
                ?: return UpdateOwnerMigrationResult.Blocked("No migration request.")
            if (selectedDeviceSerial != pending.deviceSerial) {
                UpdateOwnerMigrationResult.Blocked("Selected device changed. No app was removed.")
            } else {
                UpdateOwnerMigrationCoordinator.using(manager).execute(pending, confirmedDataLoss = true)
            }
        } finally {
            busy = false
        }
    }

    fun reportError(message: String) {
        error = message
    }
}
