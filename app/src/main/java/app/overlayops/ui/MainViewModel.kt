package app.overlayops.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.overlayops.core.AccessMode
import app.overlayops.core.AppOpsBridge
import app.overlayops.core.Backend
import app.overlayops.core.OpCatalog
import app.overlayops.core.OpDef
import app.overlayops.core.OpStatus
import app.overlayops.core.StatusFilter
import app.overlayops.core.ShizukuBridge
import app.overlayops.data.AppsRepository
import app.overlayops.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val accessMode: AccessMode = AccessMode.NONE,
    val backend: Backend = Backend.NONE,
    val loading: Boolean = false,
    val apps: List<AppEntry> = emptyList(),
    val query: String = "",
    val filter: StatusFilter = StatusFilter.ALL,
    val tab: Int = 0,
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppsRepository(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Coba sambung ke Shizuku + IAppOpsService. Panggil dari main thread. */
    fun connect(onResult: (AccessMode) -> Unit) {
        viewModelScope.launch {
            val mode = withContext(Dispatchers.IO) {
                if (!ShizukuBridge.hasPermission()) {
                    AppOpsBridge.disconnect()
                    AccessMode.NONE
                } else {
                    val ok = AppOpsBridge.connect()
                    when {
                        ok && ShizukuBridge.isRoot() -> AccessMode.SHIZUKU_ROOT
                        ok -> AccessMode.SHIZUKU_SHELL
                        else -> AccessMode.NONE
                    }
                }
            }
            _state.update { it.copy(accessMode = mode, backend = AppOpsBridge.backend) }
            onResult(mode)
        }
    }

    fun refresh(showSpinner: Boolean = true) {
        if (showSpinner) _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { repo.loadApps() }.getOrElse { emptyList() }
            }
            _state.update {
                it.copy(
                    apps = list,
                    loading = false,
                    backend = AppOpsBridge.backend,
                    message = if (list.isEmpty()) "Daftar app kosong" else null,
                )
            }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setTab(index: Int) = _state.update { it.copy(tab = index) }

    fun setFilter(filter: StatusFilter) = _state.update { it.copy(filter = filter) }

    fun readOps(entry: AppEntry, onDone: (List<Pair<OpDef, OpStatus>>) -> Unit) {
        viewModelScope.launch {
            val ops = withContext(Dispatchers.IO) { runCatching { repo.readOps(entry) }.getOrDefault(emptyList()) }
            onDone(ops)
        }
    }

    /** Ubah mode sebuah op. `onDone` menerima pesan error, atau null kalau sukses. */
    fun applyStatus(entry: AppEntry, def: OpDef, status: OpStatus, onDone: (String?) -> Unit) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { repo.writeOp(entry, def, status) }
                    .getOrElse { it.message ?: it.javaClass.simpleName }
            }
            if (error == null && def.op == OpCatalog.OVERLAY.op) {
                _state.update { s ->
                    s.copy(apps = s.apps.map { if (it.packageName == entry.packageName) it.copy(overlayStatus = status) else it })
                }
            }
            _state.update { it.copy(loading = false) }
            onDone(error)
        }
    }

    fun deviceReport(): String = ShizukuBridge.deviceSummary()
}
