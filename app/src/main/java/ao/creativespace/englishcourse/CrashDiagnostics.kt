package ao.creativespace.englishcourse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ao.creativespace.englishcourse.data.CourseData
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object CrashDiagnostics {
    private const val PREFS = "english_course_diagnostics"
    private const val LAST_REPORT = "last_fatal_report"

    fun install(context: Context) {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is PersistingExceptionHandler) return
        Thread.setDefaultUncaughtExceptionHandler(
            PersistingExceptionHandler(context.applicationContext, current)
        )
    }

    fun readReport(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LAST_REPORT, null)
            ?.takeIf { it.isNotBlank() }

    fun clearReport(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(LAST_REPORT).apply()
    }

    fun buildReport(context: Context, error: Throwable, threadName: String): String {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()
        val versionName = packageInfo?.versionName ?: "desconhecida"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo?.longVersionCode else packageInfo?.versionCode?.toLong()
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        val cause = generateSequence(error) { it.cause }
            .take(12)
            .lastOrNull()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        return buildString {
            appendLine("ENGLISH COURSE — RELATÓRIO DE DIAGNÓSTICO")
            appendLine("Data: $timestamp")
            appendLine("Aplicação: ${context.packageName}")
            appendLine("Versão: $versionName ($versionCode)")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: $threadName")
            appendLine("Erro principal: ${cause?.javaClass?.name ?: error.javaClass.name}")
            appendLine("Mensagem: ${cause?.message ?: error.message ?: "Sem mensagem"}")
            appendLine()
            appendLine("STACK TRACE COMPLETO")
            append(writer.toString())
        }.take(45_000)
    }

    fun saveReport(context: Context, report: String) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(LAST_REPORT, report.take(45_000)).commit()
        }
    }

    fun runChecks(context: Context): List<Pair<String, String>> {
        val checks = mutableListOf<Pair<String, String>>()
        val courseAssets = listOf(
            "course/lessons.json",
            "course/readings.json",
            "course/vocabulary.json"
        )
        courseAssets.forEach { path ->
            val status = runCatching {
                context.assets.open(path).use { input ->
                    val buffer = ByteArray(1)
                    check(input.read(buffer) == 1) { "Ficheiro vazio" }
                }
                "OK — ficheiro encontrado"
            }.getOrElse { "FALHA — ${it.javaClass.simpleName}: ${it.message}" }
            checks += path to status
        }

        val courseStatus = runCatching {
            val bundle = CourseData.load(context)
            "OK — ${bundle.lessons.size} aulas, ${bundle.readings.size} leituras, ${bundle.vocabulary.size} termos"
        }.getOrElse { "FALHA — ${it.javaClass.simpleName}: ${it.message}" }
        checks += "Leitura dos conteúdos" to courseStatus

        val iconStatus = runCatching {
            context.assets.open("ionicons/home-outline.svg").use { input ->
                check(input.read() >= 0) { "Ícone vazio" }
            }
            "OK — Ionicons disponível"
        }.getOrElse { "AVISO — ${it.javaClass.simpleName}: ${it.message}" }
        checks += "Recursos Ionicons" to iconStatus

        listOf("dictionary/en-pt.sqlite3", "dictionary/pt-en.sqlite3").forEach { path ->
            val status = runCatching {
                context.assets.open(path).use { input ->
                    val header = ByteArray(16)
                    var offset = 0
                    while (offset < header.size) {
                        val count = input.read(header, offset, header.size - offset)
                        check(count > 0) { "Ficheiro incompleto" }
                        offset += count
                    }
                    check(String(header, Charsets.US_ASCII) == "SQLite format 3\u0000") { "Cabeçalho SQLite inválido" }
                }
                "OK — base SQLite encontrada"
            }.getOrElse { "AVISO — ${it.javaClass.simpleName}: ${it.message}" }
            checks += path to status
        }
        return checks
    }

    private class PersistingExceptionHandler(
        private val context: Context,
        private val previous: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching {
                saveReport(context, buildReport(context, error, thread.name))
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }
}

@Composable
internal fun CrashDiagnosticScreen(
    report: String,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val checks = remember(report) { CrashDiagnostics.runChecks(context) }
    val causeLines = report.lineSequence().filter {
        it.startsWith("Erro principal:") || it.startsWith("Mensagem:") || it.startsWith("Versão:") || it.startsWith("Android:") || it.startsWith("Dispositivo:")
    }.toList()

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("English Course", color = Color(0xFF286FD1), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Ocorreu um erro ao iniciar", fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2430))
            Text(
                "A aplicação guardou o relatório técnico antes de fechar. Esta tela mostra a causa principal e verifica os ficheiros necessários para ajudar a localizar o problema.",
                color = Color(0xFF596273), fontSize = 14.sp, lineHeight = 21.sp
            )

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Resumo do erro", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF202938))
                    causeLines.forEach { line ->
                        Text(line, fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFF404B5B))
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Verificação automática", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF202938))
                    checks.forEach { (name, status) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF293344))
                            Text(status, fontSize = 12.sp, lineHeight = 17.sp, color = if (status.startsWith("FALHA")) Color(0xFFB42318) else if (status.startsWith("AVISO")) Color(0xFF986500) else Color(0xFF28794F))
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151923)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Detalhes técnicos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        report,
                        color = Color(0xFFD6DEEA),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("English Course crash report", report))
                            android.widget.Toast.makeText(context, "Relatório copiado", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copiar relatório completo")
                    }
                }
            }

            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Limpar aviso e tentar abrir novamente")
            }
            Text(
                "Se o erro voltar, copia o relatório completo e envia-o para análise. A informação mais importante é a primeira linha da stack trace que aponta para um ficheiro MainActivity.kt ou para outra classe do projeto.",
                color = Color(0xFF667085), fontSize = 12.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
