package dev.jpedro.toolkit.core

data class Target(val serial: String?, val label: String) {

    val isLocal: Boolean get() = serial == null

    fun shell(command: String): String =
        if (serial == null) command else "adb -s ${quote(serial)} shell ${quote(command)}"

    companion object {
        val Local = Target(null, "This phone")
    }
}

data class ConnectedDevice(val serial: String, val state: String, val model: String?) {
    val ready: Boolean get() = state == "device"
    val label: String get() = model?.replace('_', ' ') ?: serial
}

fun parseAdbDevices(output: String): List<ConnectedDevice> =
    output.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("*") && !it.startsWith("List of") }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) {
                null
            } else {
                ConnectedDevice(
                    serial = parts[0],
                    state = parts[1],
                    model = parts.firstOrNull { it.startsWith("model:") }?.removePrefix("model:")
                )
            }
        }
        .toList()

enum class PowerAction(
    val label: String,
    val reason: String?,
    val description: String
) {
    REBOOT("Reboot", null, "Restart normally"),
    RECOVERY("Recovery", "recovery", "Restart into recovery"),
    BOOTLOADER("Bootloader", "bootloader", "Restart into the bootloader or fastboot screen"),
    DOWNLOAD("Download mode", "download", "Restart into Samsung Download mode for odin"),
    FASTBOOTD("Fastbootd", "fastboot", "Restart into userspace fastboot"),
    EDL("EDL", "edl", "Restart into Qualcomm emergency download mode"),
    SOFT_REBOOT("Soft reboot", null, "Restart the Android framework only (this phone, root only)"),
    SHUTDOWN("Shut down", null, "Power the phone off")
}

fun PowerAction.command(target: Target, kind: BackendKind?): String? {
    val suffix = reason?.let { " $it" }.orEmpty()
    return when {
        this == PowerAction.SOFT_REBOOT ->
            if (target.isLocal && kind == BackendKind.ROOT) "setprop ctl.restart zygote" else null
        !target.isLocal -> {
            val serial = quote(target.serial.orEmpty())
            if (this == PowerAction.SHUTDOWN) "adb -s $serial shell reboot -p" else "adb -s $serial reboot$suffix"
        }
        kind == BackendKind.ROOT ->
            if (this == PowerAction.SHUTDOWN) "reboot -p" else "reboot$suffix"
        kind == BackendKind.SHIZUKU ->
            if (this == PowerAction.SHUTDOWN) "svc power shutdown" else "svc power reboot$suffix"
        else -> null
    }
}
