package dev.jpedro.toolkit.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.InputStream

object Paths {
    const val DIR = "/data/local/tmp/jpedrotoolkit"
    const val HOME = "$DIR/home"
    const val STAGED = "$DIR/staged"
}

enum class Tool(val binary: String, val label: String, val purpose: String) {
    ODIN("odin4", "odin4", "Flashes Samsung firmware to phones in Download mode"),
    ADB("adb", "adb", "Talks to connected phones: install, reboot, shell"),
    FASTBOOT("fastboot", "fastboot", "Optional, for phones in bootloader mode")
}

class Toolbox(private val provider: () -> Backend?) {

    private val env = "export PATH=${Paths.DIR}:\$PATH HOME=${Paths.HOME} TMPDIR=${Paths.DIR};"

    fun kind(): BackendKind? = provider()?.kind

    private fun backend(): Backend =
        provider() ?: throw IllegalStateException("Grant root or Shizuku access first")

    suspend fun run(command: String): CommandResult = backend().run("$env $command")

    fun stream(command: String): Flow<String> = flow { emitAll(backend().stream("$env $command")) }

    suspend fun isInstalled(tool: Tool): Boolean =
        run("[ -x ${Paths.DIR}/${tool.binary} ] && echo yes").output.contains("yes")

    suspend fun installTool(tool: Tool, input: InputStream) {
        val path = "${Paths.DIR}/${tool.binary}"
        run("mkdir -p ${Paths.HOME}/.android && chmod -R 777 ${Paths.DIR}")
        backend().write(path, input)
        run("chmod 755 ${quote(path)}")
    }

    suspend fun removeTool(tool: Tool) {
        run("rm -f ${Paths.DIR}/${tool.binary}")
    }

    suspend fun stage(name: String, input: InputStream): String {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val path = "${Paths.STAGED}/$safe"
        run("mkdir -p ${Paths.STAGED} ${Paths.HOME}/.android && chmod -R 777 ${Paths.DIR}")
        backend().write(path, input)
        run("chmod 644 ${quote(path)}")
        return path
    }
}

object UriPaths {

    fun resolve(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val parts = id.split(":", limit = 2)
        if (parts.size != 2) return null
        val base = if (parts[0].equals("primary", ignoreCase = true)) {
            "/storage/emulated/0"
        } else {
            "/storage/${parts[0]}"
        }
        return "$base/${parts[1]}"
    }

    fun displayName(context: Context, uri: Uri): String =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment
            ?: "file"
}

class Stager(private val context: Context, private val toolbox: Toolbox) {

    suspend fun pathFor(uri: Uri): String {
        UriPaths.resolve(uri)?.let { return it }
        val name = UriPaths.displayName(context, uri)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open $name")
        return input.use { toolbox.stage(name, it) }
    }
}
