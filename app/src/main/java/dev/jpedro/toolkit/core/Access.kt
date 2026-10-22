package dev.jpedro.toolkit.core

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val SHIZUKU_REQUEST_CODE = 4107

enum class ShizukuState { MISSING, NOT_RUNNING, NEEDS_PERMISSION, GRANTED }

enum class RootState { UNKNOWN, DENIED, GRANTED }

enum class BackendPreference(val label: String) {
    AUTO("Auto"),
    ROOT("Root only"),
    SHIZUKU("Shizuku only")
}

class AccessManager(private val context: Context) {

    private val shizukuState = MutableStateFlow(ShizukuState.MISSING)
    private val rootState = MutableStateFlow(RootState.UNKNOWN)

    val shizuku: StateFlow<ShizukuState> = shizukuState.asStateFlow()
    val root: StateFlow<RootState> = rootState.asStateFlow()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshShizuku() }
    private val binderDead = Shizuku.OnBinderDeadListener { refreshShizuku() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refreshShizuku() }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refreshShizuku()
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
    }

    fun refreshShizuku() {
        shizukuState.value = when {
            !Shizuku.pingBinder() ->
                if (shizukuInstalled()) ShizukuState.NOT_RUNNING else ShizukuState.MISSING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.GRANTED
            else -> ShizukuState.NEEDS_PERMISSION
        }
    }

    fun requestShizuku() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        ) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    private var cachedRootGranted: Boolean? = null

    fun resetRootCache() {
        cachedRootGranted = null
    }

    suspend fun requestRoot(): Boolean {
        if (cachedRootGranted == true) {
            rootState.value = RootState.GRANTED
            return true
        }
        val result = withContext(Dispatchers.IO) {
            runCatching { RootBackend.run("id") }.getOrNull()
        }
        val granted = result?.output?.contains("uid=0") == true
        if (granted) {
            cachedRootGranted = true
        }
        rootState.value = if (granted) RootState.GRANTED else RootState.DENIED
        return granted
    }

    fun backend(preference: BackendPreference): Backend? {
        val rootReady = rootState.value == RootState.GRANTED
        val shizukuReady = shizukuState.value == ShizukuState.GRANTED
        return when (preference) {
            BackendPreference.AUTO -> when {
                rootReady -> RootBackend
                shizukuReady -> ShizukuBackend
                else -> null
            }
            BackendPreference.ROOT -> if (rootReady) RootBackend else null
            BackendPreference.SHIZUKU -> if (shizukuReady) ShizukuBackend else null
        }
    }

    private fun shizukuInstalled(): Boolean =
        runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess
}
