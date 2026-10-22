package dev.jpedro.toolkit

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.FileProvider
import java.io.File
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jpedro.toolkit.core.AccessManager
import dev.jpedro.toolkit.core.BackendKind
import dev.jpedro.toolkit.core.Checks
import dev.jpedro.toolkit.ui.isValidImei
import dev.jpedro.toolkit.core.ConnectedDevice
import dev.jpedro.toolkit.core.KnoxReport
import dev.jpedro.toolkit.core.PowerAction
import dev.jpedro.toolkit.core.RootReport
import dev.jpedro.toolkit.core.Stager
import dev.jpedro.toolkit.core.Target
import dev.jpedro.toolkit.core.Tool
import dev.jpedro.toolkit.core.Toolbox
import dev.jpedro.toolkit.core.UriPaths
import dev.jpedro.toolkit.core.command
import dev.jpedro.toolkit.core.parseAdbDevices
import dev.jpedro.toolkit.core.quote
import dev.jpedro.toolkit.data.AppSettings
import dev.jpedro.toolkit.data.Keys
import dev.jpedro.toolkit.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OdinSlot(val flag: String, val label: String) {
    BL("-b", "BL"),
    AP("-a", "AP"),
    CP("-c", "CP"),
    CSC("-s", "CSC"),
    USERDATA("-u", "USERDATA")
}

enum class TerminalMode(val label: String) {
    SHELL("Phone shell"),
    ADB("adb")
}

data class PickedFile(val uri: Uri, val name: String)

data class TargetFrpReport(
    val googleAccountPresent: Boolean?,
    val frpActive: Boolean?,
    val oemUnlockAllowed: Boolean?,
    val bootloaderLocked: Boolean?,
    val targetLabel: String
)

data class AppPackage(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

fun SnapshotStateList<String>.push(line: String, cap: Int = 3000) {
    add(line)
    repeat((size - cap).coerceAtLeast(0)) { removeAt(0) }
}

class ToolkitViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)
    val access = AccessManager(application)

    val settings: StateFlow<AppSettings> =
        store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val toolbox = Toolbox { access.backend(settings.value.backend) }
    private val stager = Stager(application, toolbox)
    private val checks = Checks(toolbox, access)

    val activeBackend: StateFlow<BackendKind?> =
        combine(access.shizuku, access.root, settings) { _, _, current -> access.backend(current.backend)?.kind }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var target by mutableStateOf(Target.Local)
        private set
    var devices by mutableStateOf<List<ConnectedDevice>>(emptyList())
        private set
    var devicesBusy by mutableStateOf(false)
        private set
    var deviceNote by mutableStateOf<String?>(null)
        private set

    var toolStatus by mutableStateOf<Map<Tool, Boolean>>(emptyMap())
        private set
    var toolBusy by mutableStateOf(false)
        private set

    var rootReport by mutableStateOf<RootReport?>(null)
        private set
    var knoxReport by mutableStateOf<KnoxReport?>(null)
        private set
    var deviceProps by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var checking by mutableStateOf(false)
        private set
    var checkError by mutableStateOf<String?>(null)
        private set
    var deviceImeis by mutableStateOf<List<String>>(emptyList())
        private set
    var imeiStatus by mutableStateOf<String>("Loading...")
        private set
    var frpReport by mutableStateOf<TargetFrpReport?>(null)
        private set
    var checkingFrp by mutableStateOf(false)
        private set

    fun checkTargetFrp() {
        viewModelScope.launch {
            checkingFrp = true
            val currentTarget = target
            val result = runCatching {
                val accountsDump = if (currentTarget.isLocal && toolbox.kind() == null) {
                    ""
                } else {
                    toolbox.run(currentTarget.shell("dumpsys account")).output
                }
                val hasGoogle = if (accountsDump.isNotBlank()) accountsDump.contains("com.google", ignoreCase = true) else null

                val oemUnlockProp = runCatching {
                    toolbox.run(currentTarget.shell("getprop sys.oem_unlock_allowed")).output.trim()
                }.getOrNull().orEmpty()

                val flashLockedProp = runCatching {
                    toolbox.run(currentTarget.shell("getprop ro.boot.flash.locked")).output.trim()
                }.getOrNull().orEmpty()

                val oemUnlock = when (oemUnlockProp) {
                    "1" -> true
                    "0" -> false
                    else -> null
                }
                val blLocked = when (flashLockedProp) {
                    "1" -> true
                    "0" -> false
                    else -> null
                }

                TargetFrpReport(
                    googleAccountPresent = hasGoogle,
                    frpActive = hasGoogle,
                    oemUnlockAllowed = oemUnlock,
                    bootloaderLocked = blLocked,
                    targetLabel = currentTarget.label
                )
            }.getOrNull()

            frpReport = result
            checkingFrp = false
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    fun loadImei() {
        viewModelScope.launch {
            val found = mutableSetOf<String>()

            // 1. TelephonyManager API (Official Android API)
            runCatching {
                val tm = getApplication<Application>().getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (tm != null) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        @Suppress("DEPRECATION")
                        val count = try { if (Build.VERSION.SDK_INT >= 30) tm.activeModemCount else tm.phoneCount } catch (_: Exception) { 2 }
                        for (slot in 0 until count.coerceAtLeast(1)) {
                            runCatching { tm.getImei(slot) }
                                .getOrNull()
                                ?.filter { it.isDigit() }
                                ?.takeIf { it.length >= 15 && isValidImei(it.take(15)) }
                                ?.let { found.add(it.take(15)) }
                        }
                    }
                    if (found.isEmpty()) {
                        runCatching { tm.imei ?: tm.deviceId }
                            .getOrNull()
                            ?.filter { it.isDigit() }
                            ?.takeIf { it.length >= 15 && isValidImei(it.take(15)) }
                            ?.let { found.add(it.take(15)) }
                    }
                }
            }

            // 2. Strict getprop property checks
            val propCommands = listOf(
                "getprop ril.gsm.imei",
                "getprop gsm.imei",
                "getprop gsm.imei1",
                "getprop gsm.imei2",
                "getprop gsm.m_imei1",
                "getprop gsm.m_imei2",
                "getprop ril.imei",
                "getprop ro.ril.gprs_imei",
                "getprop persist.radio.imei",
                "getprop persist.radio.imei1",
                "getprop persist.radio.imei2"
            )
            for (cmd in propCommands) {
                val output = runCatching { toolbox.run(cmd) }.getOrNull()?.output.orEmpty()
                parseImeisFromProps(output, found)
            }

            // 3. Strict dumpsys checks (only lines mentioning IMEI/DeviceId)
            val dumpsysCommands = listOf(
                "dumpsys iphonesubinfo",
                "dumpsys telephony.registry"
            )
            for (cmd in dumpsysCommands) {
                val output = runCatching { toolbox.run(cmd) }.getOrNull()?.output.orEmpty()
                parseImeisFromDumpsys(output, found)
            }

            // 4. Strict service call parcel decoding
            val serviceCommands = listOf(
                "service call iphonesubinfo 1 s16 \"com.android.shell\"",
                "service call iphonesubinfo 2 s16 \"com.android.shell\"",
                "service call iphonesubinfo 3 s16 \"com.android.shell\"",
                "service call iphonesubinfo 1 i32 0 s16 \"com.android.shell\"",
                "service call iphonesubinfo 1 i32 1 s16 \"com.android.shell\""
            )
            for (cmd in serviceCommands) {
                val output = runCatching { toolbox.run(cmd) }.getOrNull()?.output.orEmpty()
                parseImeisFromParcel(output, found)
            }

            // 5. Fallback su commands
            if (found.isEmpty()) {
                val suDump = runCatching {
                    val process = ProcessBuilder("sh", "-c", "su -c dumpsys iphonesubinfo").redirectErrorStream(true).start()
                    val text = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    text
                }.getOrNull().orEmpty()
                parseImeisFromDumpsys(suDump, found)

                val suParcel = runCatching {
                    val process = ProcessBuilder("sh", "-c", "su -c 'service call iphonesubinfo 1 s16 com.android.shell'").redirectErrorStream(true).start()
                    val text = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    text
                }.getOrNull().orEmpty()
                parseImeisFromParcel(suParcel, found)
            }

            if (found.isNotEmpty()) {
                deviceImeis = found.toList()
                imeiStatus = "Available"
            } else {
                deviceImeis = emptyList()
                imeiStatus = "Unavailable"
            }
        }
    }

    private fun parseImeisFromProps(text: String, out: MutableSet<String>) {
        if (text.isBlank()) return
        val candidates = text.split(Regex("[,\\s;:]+"))
        for (candidate in candidates) {
            val digits = candidate.filter { it.isDigit() }
            if (digits.length == 15 && isValidImei(digits)) {
                out.add(digits)
            }
        }
    }

    private fun parseImeisFromDumpsys(text: String, out: MutableSet<String>) {
        if (text.isBlank()) return
        val imeiLines = text.lines().filter {
            it.contains("imei", ignoreCase = true) || it.contains("deviceid", ignoreCase = true)
        }
        for (line in imeiLines) {
            val matches = Regex("\\b\\d{15}\\b").findAll(line)
            for (m in matches) {
                if (isValidImei(m.value)) {
                    out.add(m.value)
                }
            }
        }
    }

    private fun parseImeisFromParcel(text: String, out: MutableSet<String>) {
        if (text.isBlank()) return
        val quotes = Regex("'([^']*)'").findAll(text)
        val extractedString = quotes.map { it.groupValues[1] }.joinToString("")
        val digits = extractedString.filter { it.isDigit() }
        if (digits.length >= 15) {
            val candidate = digits.take(15)
            if (isValidImei(candidate)) {
                out.add(candidate)
            }
        }
    }

    val slots = mutableStateMapOf<OdinSlot, PickedFile>()
    var odinDevices by mutableStateOf<List<String>>(emptyList())
        private set
    var odinDevice by mutableStateOf<String?>(null)
    var eraseUserdata by mutableStateOf(false)
    var scanning by mutableStateOf(false)
        private set
    var flashRunning by mutableStateOf(false)
        private set
    val flashLog = mutableStateListOf<String>()
    private var flashJob: Job? = null

    val apks = mutableStateListOf<PickedFile>()
    var installSplit by mutableStateOf(false)
    var installDowngrade by mutableStateOf(false)
    var installGrant by mutableStateOf(false)
    var installRunning by mutableStateOf(false)
        private set
    val installLog = mutableStateListOf<String>()

    var powerBusy by mutableStateOf(false)
        private set
    val powerLog = mutableStateListOf<String>()

    var terminalMode by mutableStateOf(TerminalMode.SHELL)
    var terminalRunning by mutableStateOf(false)
        private set
    val terminal = mutableStateListOf<String>()
    val terminalHistory = mutableStateListOf<String>()
    private var terminalJob: Job? = null

    init {
        access.start()
        viewModelScope.launch {
            access.requestShizuku()
            access.requestRoot()
        }
    }

    override fun onCleared() {
        access.stop()
    }

    fun dismissFirstRun() {
        viewModelScope.launch {
            store.set(Keys.FIRST_RUN, false)
        }
    }

    var showChangelogDialog by mutableStateOf(false)
        private set

    fun openChangelog() {
        showChangelogDialog = true
    }

    fun dismissChangelog(version: String) {
        showChangelogDialog = false
        updateLastVersion(version)
    }

    fun updateLastVersion(version: String) {
        viewModelScope.launch {
            store.set(Keys.LAST_VERSION, version)
        }
    }

    var installedApps by mutableStateOf<List<AppPackage>>(emptyList())
        private set
    var loadingApps by mutableStateOf(false)
        private set
    var debloatRunning by mutableStateOf(false)
        private set
    val debloatLog = mutableStateListOf<String>()

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            loadingApps = true
            val currentTarget = target
            val list = mutableListOf<AppPackage>()
            try {
                if (currentTarget.isLocal) {
                    val pm = getApplication<Application>().packageManager
                    val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                    for (pkg in packages) {
                        val appInfo = pkg.applicationInfo
                        val isSys = appInfo != null && (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        val label = appInfo?.loadLabel(pm)?.toString() ?: pkg.packageName
                        list.add(AppPackage(pkg.packageName, label.ifBlank { pkg.packageName }, isSys))
                    }
                } else {
                    val output = toolbox.run(currentTarget.shell("pm list packages -f")).output
                    for (line in output.lines()) {
                        if (line.startsWith("package:")) {
                            val pkg = line.substringAfterLast("=").trim()
                            val isSys = line.contains("/system/") || line.contains("/product/") || line.contains("/priv-app/")
                            if (pkg.isNotBlank()) {
                                list.add(AppPackage(pkg, pkg, isSys))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                debloatLog.push("Error loading apps: ${e.message}")
            }
            installedApps = list.sortedBy { it.label.lowercase() }
            loadingApps = false
        }
    }

    fun uninstallApps(packages: List<String>) {
        if (debloatRunning || packages.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            debloatRunning = true
            val currentTarget = target
            try {
                for (pkg in packages) {
                    debloatLog.push("Uninstalling $pkg on ${currentTarget.label}...")
                    val cmd = if (currentTarget.isLocal) {
                        "pm uninstall --user 0 ${quote(pkg)}"
                    } else {
                        "adb -s ${quote(currentTarget.serial.orEmpty())} shell pm uninstall --user 0 ${quote(pkg)}"
                    }
                    val res = toolbox.run(cmd)
                    debloatLog.push(res.output.ifBlank { "Uninstalled $pkg" })
                }
                debloatLog.push("Finished debloat batch.")
                loadInstalledApps()
            } catch (e: Exception) {
                debloatLog.push("Uninstall error: ${e.message}")
            } finally {
                debloatRunning = false
            }
        }
    }

    var updateChecking by mutableStateOf(false)
        private set
    var updateResultNote by mutableStateOf<String?>(null)
        private set
    var updateDownloadUrl by mutableStateOf<String?>(null)
        private set

    fun checkForUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            updateChecking = true
            updateResultNote = null
            updateDownloadUrl = null
            try {
                val url = java.net.URL("https://api.github.com/repos/jpedrothejo/jpedrotoolkit/releases/latest")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "JpedroToolkitApp")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val tagNameMatch = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(response)
                    val htmlUrlMatch = Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"").find(response)
                    val latestTag = tagNameMatch?.groupValues?.get(1)?.trim().orEmpty()
                    val downloadUrl = htmlUrlMatch?.groupValues?.get(1)?.trim() ?: "https://github.com/jpedrothejo/jpedrotoolkit/releases"

                    if (latestTag.isNotEmpty() && latestTag != "b1.2" && latestTag != "v1.2") {
                        updateResultNote = "New update available: $latestTag!"
                        updateDownloadUrl = downloadUrl
                    } else {
                        updateResultNote = "Your software is up to date (b1.2)"
                    }
                } else {
                    updateResultNote = "Your software is up to date (b1.2)"
                }
            } catch (_: Exception) {
                updateResultNote = "Your software is up to date (b1.2)"
            } finally {
                updateChecking = false
            }
        }
    }

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { store.set(key, value) }
    }

    fun clearWallpaper() {
        viewModelScope.launch { store.remove(Keys.WALLPAPER) }
    }

    fun resetSettings() {
        viewModelScope.launch { store.reset() }
    }

    fun requestRoot() {
        viewModelScope.launch { access.requestRoot() }
    }

    fun selectTarget(value: Target) {
        target = value
    }

    fun refreshDevices() {
        viewModelScope.launch {
            devicesBusy = true
            deviceNote = null
            val output = runCatching { toolbox.run("adb devices -l") }.getOrNull()
            when {
                output == null -> deviceNote = "Grant root or Shizuku access first"
                output.output.contains("not found") || output.output.contains("inaccessible") ->
                    deviceNote = "adb is not imported yet. Add it in Settings under Binaries"
                else -> {
                    devices = parseAdbDevices(output.output)
                    if (target.serial != null && devices.none { it.serial == target.serial }) {
                        target = Target.Local
                    }
                    if (devices.isEmpty()) {
                        if (toolbox.kind() == BackendKind.SHIZUKU) {
                            deviceNote = "No phones detected. Note: Shizuku runs as shell and cannot access USB devices (/dev/bus/usb). Use Root backend for USB adb, or connect via Wi-Fi."
                        } else {
                            deviceNote = "No phones detected over adb"
                        }
                    }
                }
            }
            devicesBusy = false
        }
    }

    fun refreshTools() {
        viewModelScope.launch {
            toolStatus = runCatching { Tool.values().associateWith { toolbox.isInstalled(it) } }
                .getOrDefault(emptyMap())
        }
    }

    fun importTool(tool: Tool, uri: Uri) {
        viewModelScope.launch {
            toolBusy = true
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    toolbox.installTool(tool, it)
                }
            }
            toolBusy = false
            refreshTools()
        }
    }

    fun removeTool(tool: Tool) {
        viewModelScope.launch {
            runCatching { toolbox.removeTool(tool) }
            refreshTools()
        }
    }

    fun runChecks() {
        viewModelScope.launch {
            checking = true
            checkError = null
            val current = target
            runCatching {
                rootReport = checks.root(current)
                val props = checks.props(current)
                deviceProps = props
                knoxReport = checks.knox(props)
            }.onFailure { checkError = it.message ?: "Check failed" }
            checking = false
        }
    }

    fun setSlot(slot: OdinSlot, uri: Uri) {
        slots[slot] = PickedFile(uri, UriPaths.displayName(getApplication<Application>(), uri))
    }

    fun clearSlot(slot: OdinSlot) {
        slots.remove(slot)
    }

    fun scanOdin() {
        viewModelScope.launch {
            scanning = true
            val result = runCatching { toolbox.run("odin4 -l") }
            val text = result.getOrNull()?.output ?: result.exceptionOrNull()?.message.orEmpty()
            odinDevices = text.lines().map { it.trim() }.filter { it.startsWith("/dev/") }
            odinDevice = odinDevices.firstOrNull()
            if (odinDevices.isEmpty()) {
                flashLog.push("No Download mode phone found")
                if (text.isNotBlank()) flashLog.push(text)
            } else {
                flashLog.push("Found ${odinDevices.size} phone(s) in Download mode")
            }
            scanning = false
        }
    }

    fun flash() {
        if (flashRunning || slots.isEmpty()) return
        flashJob = viewModelScope.launch {
            flashRunning = true
            val current = settings.value
            try {
                flashLog.push("Preparing files")
                val args = StringBuilder()
                for ((slot, file) in slots.entries.sortedBy { it.key.ordinal }) {
                    val path = stager.pathFor(file.uri)
                    flashLog.push("${slot.label}: ${file.name}")
                    args.append(" ${slot.flag} ${quote(path)}")
                }
                if (current.rebootAfterFlash) args.append(" --reboot")
                if (current.validateBinaries) args.append(" -V")
                if (eraseUserdata) args.append(" -e")
                odinDevice?.let { args.append(" -d ${quote(it)}") }
                toolbox.stream("odin4$args").collect { flashLog.push(it) }
                flashLog.push("Finished")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                flashLog.push("Error: ${e.message}")
            } finally {
                flashRunning = false
            }
        }
    }

    fun addApks(uris: List<Uri>) {
        uris.forEach { uri ->
            if (apks.none { it.uri == uri }) {
                apks.add(PickedFile(uri, UriPaths.displayName(getApplication<Application>(), uri)))
            }
        }
    }

    fun removeApk(file: PickedFile) {
        apks.remove(file)
    }

    fun install() {
        if (installRunning || apks.isEmpty()) return
        val current = target
        if (current.isLocal) {
            if (installSplit) {
                installLog.push("Split-set installs on this phone use Android's PackageInstaller and aren't available in this simple installer flow. Turn off split-set and install a single APK, or use root/Shizuku for advanced installs.")
                return
            }
            viewModelScope.launch {
                installRunning = true
                try {
                    val file = apks.first()
                    val context = getApplication<Application>()
                    if (android.os.Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        installLog.push("Allow Toolkit to install unknown apps in Android Settings, then tap Install again.")
                        return@launch
                    }
                    val installerDir = File(context.cacheDir, "installer").apply { mkdirs() }
                    val safeName = file.name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val apkFile = File(installerDir, safeName.ifBlank { "selected.apk" })
                    context.contentResolver.openInputStream(file.uri)?.use { input ->
                        apkFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Couldn't read the selected APK")
                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.files", apkFile)
                    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                        data = contentUri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    apks.remove(file)
                    installLog.push("Opened Android's installer for ${file.name}. Confirm the installation in Android.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    installLog.push("Installer error: ${e.message}")
                } finally {
                    installRunning = false
                }
            }
            return
        }

        viewModelScope.launch {
            installRunning = true
            try {
                val flags = buildString {
                    append("-r")
                    if (installDowngrade) append(" -d")
                    if (installGrant) append(" -g")
                }
                val files = apks.toList()
                val paths = files.map { stager.pathFor(it.uri) }
                val jobs = if (installSplit) {
                    listOf("${files.size} APKs as one split set" to installCommand(current, "install-multiple", flags, paths))
                } else {
                    files.indices.map { files[it].name to installCommand(current, "install", flags, listOf(paths[it])) }
                }
                for ((name, command) in jobs) {
                    installLog.push("Installing $name on ${current.label}")
                    toolbox.stream(command).collect { installLog.push(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                installLog.push("Error: ${e.message}")
            } finally {
                installRunning = false
            }
        }
    }

    private fun installCommand(target: Target, sub: String, flags: String, paths: List<String>): String {
        val files = paths.joinToString(" ") { quote(it) }
        return if (target.isLocal) {
            "pm $sub $flags $files"
        } else {
            "adb -s ${quote(target.serial.orEmpty())} $sub $flags $files"
        }
    }

    fun powerCommandFor(action: PowerAction): String? = action.command(target, toolbox.kind())

    fun power(action: PowerAction) {
        val command = powerCommandFor(action) ?: return
        viewModelScope.launch {
            powerBusy = true
            powerLog.push("${action.label} on ${target.label}")
            val result = runCatching { toolbox.run(command) }
            result.onSuccess { powerLog.push(it.output.ifBlank { "Command sent" }) }
            result.onFailure { powerLog.push("Error: ${it.message}") }
            powerBusy = false
        }
    }

    fun runTerminal(input: String) {
        val line = input.trim()
        if (line.isEmpty() || terminalRunning) return
        if (line == "clear") {
            terminal.clear()
            return
        }
        terminalHistory.remove(line)
        terminalHistory.add(0, line)
        repeat((terminalHistory.size - 12).coerceAtLeast(0)) { terminalHistory.removeAt(terminalHistory.lastIndex) }
        val cap = settings.value.terminalMaxLines
        val command = when (terminalMode) {
            TerminalMode.SHELL -> target.shell(line)
            TerminalMode.ADB -> "adb $line"
        }
        terminal.push(if (terminalMode == TerminalMode.ADB) "adb> $line" else "\$ $line", cap)
        terminalJob = viewModelScope.launch {
            terminalRunning = true
            try {
                toolbox.stream(command).collect { terminal.push(it, cap) }
            } catch (e: CancellationException) {
                terminal.push("^C", cap)
                throw e
            } catch (e: Exception) {
                terminal.push("Error: ${e.message}", cap)
            } finally {
                terminalRunning = false
            }
        }
    }

    fun stopTerminal() {
        terminalJob?.cancel()
    }
}
