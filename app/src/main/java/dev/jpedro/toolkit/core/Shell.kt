package dev.jpedro.toolkit.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.InputStream

data class CommandResult(val code: Int, val output: String) {
    val ok: Boolean get() = code == 0
}

enum class BackendKind(val label: String) {
    ROOT("Root"),
    SHIZUKU("Shizuku")
}

fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

interface Backend {
    val kind: BackendKind
    suspend fun run(command: String): CommandResult
    fun stream(command: String): Flow<String>
    suspend fun write(path: String, input: InputStream)
}

abstract class ProcessBackend : Backend {

    protected abstract fun spawn(command: String): Process

    private fun merged(command: String) = "( $command ) 2>&1"

    override suspend fun run(command: String): CommandResult = withContext(Dispatchers.IO) {
        val process = spawn(merged(command))
        val text = process.inputStream.bufferedReader().readText()
        CommandResult(process.waitFor(), text.trimEnd())
    }

    override fun stream(command: String): Flow<String> = callbackFlow {
        val process = withContext(Dispatchers.IO) { spawn(merged(command)) }
        val reader = launch(Dispatchers.IO) {
            process.inputStream.bufferedReader().forEachLine { trySend(it) }
            close()
        }
        awaitClose {
            reader.cancel()
            process.destroy()
        }
    }

    override suspend fun write(path: String, input: InputStream) {
        withContext(Dispatchers.IO) {
            val process = spawn("cat > ${quote(path)} 2>/dev/null")
            process.outputStream.use { input.copyTo(it) }
            process.waitFor()
        }
    }
}

object RootBackend : ProcessBackend() {
    override val kind = BackendKind.ROOT

    override fun spawn(command: String): Process =
        ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
}

object ShizukuBackend : ProcessBackend() {
    override val kind = BackendKind.SHIZUKU

    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
    }

    override fun spawn(command: String): Process =
        newProcess.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
}
