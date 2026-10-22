package dev.jpedro.toolkit.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RootReport(
    val granted: Boolean,
    val managers: List<String>,
    val traces: List<String>
)

data class KnoxReport(
    val tripped: Boolean?,
    val warrantyBit: String?,
    val emStatus: String?,
    val bootloaderLocked: String?,
    val verifiedBoot: String?,
    val knoxVersion: String?,
    val samsung: Boolean
)

private val managerPackages = mapOf(
    "com.topjohnwu.magisk" to "Magisk",
    "io.github.huskydg.magisk" to "Magisk Delta",
    "io.github.vvb2060.magisk" to "Magisk Alpha",
    "me.weishu.kernelsu" to "KernelSU",
    "com.rifsxd.ksunext" to "KernelSU Next",
    "com.sukisu.ultra" to "SukiSU Ultra",
    "me.bmax.apatch" to "APatch"
)

private val propKeys = listOf(
    "ro.product.manufacturer",
    "ro.product.model",
    "ro.build.version.release",
    "ro.build.version.security_patch",
    "ro.bootloader",
    "ro.boot.warranty_bit",
    "ro.warranty_bit",
    "ro.boot.em.status",
    "ro.boot.flash.locked",
    "ro.boot.verifiedbootstate",
    "ro.boot.vbmeta.device_state",
    "ro.config.knox"
)

class Checks(private val toolbox: Toolbox, private val access: AccessManager) {

    suspend fun root(target: Target): RootReport {
        val granted = if (target.isLocal) {
            access.requestRoot()
        } else {
            exec(target, "su -c id").contains("uid=0")
        }
        val installed = exec(target, "pm list packages")
            .lineSequence()
            .map { it.removePrefix("package:").trim() }
            .toSet()
        val managers = managerPackages.filterKeys { it in installed }.values.toList()
        val traces = if (target.isLocal && granted) {
            exec(target, "for f in /data/adb/ksud /data/adb/magisk /data/adb/ap; do [ -e \$f ] && echo \$f; done")
                .lines()
                .filter { it.startsWith("/") }
        } else {
            emptyList()
        }
        return RootReport(granted, managers, traces)
    }

    suspend fun props(target: Target): Map<String, String> {
        val script = propKeys.joinToString("; ") { "echo \"$it=\$(getprop $it)\"" }
        return exec(target, script)
            .lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
            }
            .toMap()
    }

    fun knox(props: Map<String, String>): KnoxReport {
        val bit = props["ro.boot.warranty_bit"].orEmpty().ifEmpty { props["ro.warranty_bit"].orEmpty() }
        val em = props["ro.boot.em.status"].orEmpty()
        val tripped = when {
            bit.isNotEmpty() -> bit != "0"
            em.isNotEmpty() -> em.lowercase() !in setOf("0x0", "0")
            else -> null
        }
        val locked = props["ro.boot.flash.locked"].orEmpty().ifEmpty { props["ro.boot.vbmeta.device_state"].orEmpty() }
        return KnoxReport(
            tripped = tripped,
            warrantyBit = bit.ifEmpty { null },
            emStatus = em.ifEmpty { null },
            bootloaderLocked = locked.ifEmpty { null },
            verifiedBoot = props["ro.boot.verifiedbootstate"]?.ifEmpty { null },
            knoxVersion = props["ro.config.knox"]?.ifEmpty { null },
            samsung = props["ro.product.manufacturer"].orEmpty().equals("samsung", ignoreCase = true)
        )
    }

    private suspend fun exec(target: Target, command: String): String =
        if (target.isLocal && toolbox.kind() == null) {
            localShell(command)
        } else {
            toolbox.run(if (target.isLocal) command else target.shell(command)).output
        }

    private suspend fun localShell(command: String): String = withContext(Dispatchers.IO) {
        val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor()
        text
    }
}
