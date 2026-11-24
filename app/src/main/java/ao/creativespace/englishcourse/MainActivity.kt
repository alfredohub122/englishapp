package ao.creativespace.englishcourse

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import ao.creativespace.englishcourse.data.CourseBundle
import ao.creativespace.englishcourse.data.CourseData
import ao.creativespace.englishcourse.data.DictionaryDirection
import ao.creativespace.englishcourse.data.DictionaryEntry
import ao.creativespace.englishcourse.data.DictionaryRepository
import ao.creativespace.englishcourse.data.Lesson
import ao.creativespace.englishcourse.data.ProgressStore
import ao.creativespace.englishcourse.data.QuizItem
import ao.creativespace.englishcourse.data.Reading
import ao.creativespace.englishcourse.data.VocabularyItem
import ao.creativespace.englishcourse.ui.IonIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val AppBlue = Color(0xFF2F80ED)
private val AppText = Color(0xFF1F1F1F)
private val AppMuted = Color(0xFF858B94)
private val AppBorder = Color(0xFFECECEC)
private val AppSurface = Color(0xFFF7F7F7)
private val AppPaleBlue = Color(0xFFEDF4FF)
private val AppGreen = Color(0xFF28794F)
private val AppPaleGreen = Color(0xFFEEF7F1)

private enum class AppTab(val label: String, val icon: String) {
    HOME("Início", "home-outline"),
    LESSONS("Aulas", "book-outline"),
    DICTIONARY("Dicionário", "search-outline"),
    READINGS("Leituras", "reader-outline"),
    PROGRESS("Progresso", "stats-chart-outline")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        CrashDiagnostics.install(applicationContext)
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        setContent {
            var diagnosticReport by rememberSaveable {
                mutableStateOf(CrashDiagnostics.readReport(applicationContext))
            }
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = AppBlue,
                    onPrimary = Color.White,
                    background = Color.White,
                    onBackground = AppText,
                    surface = Color.White,
                    onSurface = AppText,
                    secondary = AppMuted,
                    outline = AppBorder
                )
            ) {
                if (diagnosticReport != null) {
                    CrashDiagnosticScreen(
                        report = diagnosticReport.orEmpty(),
                        onRetry = {
                            CrashDiagnostics.clearReport(applicationContext)
                            diagnosticReport = null
                            recreate()
                        }
                    )
                } else {
                    try {
                        EnglishCourseApp()
                    } catch (error: Throwable) {
                        val report = CrashDiagnostics.buildReport(applicationContext, error, Thread.currentThread().name)
                        CrashDiagnostics.saveReport(applicationContext, report)
                        CrashDiagnosticScreen(
                            report = report,
                            onRetry = {
                                CrashDiagnostics.clearReport(applicationContext)
                                recreate()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnglishCourseApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bundle = remember { CourseData.load(context) }
    val progress = remember { ProgressStore(context) }
    val dictionary = remember { DictionaryRepository(context) }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME.name) }
    var selectedLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedReadingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val selectedLesson = bundle.lessons.firstOrNull { it.id == selectedLessonId }
    val selectedReading = bundle.readings.firstOrNull { it.id == selectedReadingId }

    BackHandler(enabled = selectedLesson != null || selectedReading != null || showSettings) {
        when {
            selectedLesson != null -> selectedLessonId = null
            selectedReading != null -> selectedReadingId = null
            showSettings -> showSettings = false
        }
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            if (selectedLesson == null && selectedReading == null && !showSettings) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp
                ) {
                    AppTab.entries.forEach { tab ->
                        val selected = selectedTab == tab.name
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTab = tab.name },
                            icon = {
                                IonIcon(
                                    tab.icon,
                                    size = 21.dp,
                                    tint = if (selected) AppBlue else AppMuted
                                )
                            },
                            label = {
                                Text(tab.label, fontSize = 9.sp, maxLines = 1)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppBlue,
                                selectedTextColor = AppBlue,
                                unselectedIconColor = AppMuted,
                                unselectedTextColor = AppMuted,
                                indicatorColor = AppPaleBlue
                            )
                        )
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .background(Color.White)
        ) {
            when {
                selectedLesson != null -> LessonDetailScreen(
                    lesson = selectedLesson,
                    progress = progress,
                    onBack = { selectedLessonId = null }
                )
                selectedReading != null -> ReadingDetailScreen(
                    reading = selectedReading,
                    progress = progress,
                    onBack = { selectedReadingId = null }
                )
                showSettings -> SettingsScreen(onBack = { showSettings = false })
                else -> when (AppTab.valueOf(selectedTab)) {
                    AppTab.HOME -> HomeScreen(
                        bundle = bundle,
                        progress = progress,
                        onLesson = { selectedLessonId = it.id },
                        onLessons = { selectedTab = AppTab.LESSONS.name },
                        onDictionary = { selectedTab = AppTab.DICTIONARY.name },
                        onReadings = { selectedTab = AppTab.READINGS.name },
                        onSettings = { showSettings = true }
                    )
                    AppTab.LESSONS -> LessonsScreen(bundle.lessons, progress) { selectedLessonId = it.id }
                    AppTab.DICTIONARY -> DictionaryScreen(bundle, dictionary)
                    AppTab.READINGS -> ReadingsScreen(bundle.readings, progress) { selectedReadingId = it.id }
                    AppTab.PROGRESS -> ProgressScreen(bundle, progress)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    bundle: CourseBundle,
    progress: ProgressStore,
    onLesson: (Lesson) -> Unit,
    onLessons: () -> Unit,
    onDictionary: () -> Unit,
    onReadings: () -> Unit,
    onSettings: () -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    val firstUnfinished = bundle.lessons.firstOrNull { !progress.isLessonComplete(it.id) }
    val filtered = remember(search, bundle.lessons) {
        if (search.isBlank()) emptyList() else bundle.lessons.filter {
            it.title.contains(search, true) || it.description.contains(search, true) || it.category.contains(search, true)
        }.take(5)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = "Bandeira do Reino Unido",
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("English Course", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = AppText)
                    Text("Curso completo de inglês", fontSize = 11.sp, color = AppMuted)
                }
                androidx.compose.material3.IconButton(onClick = onSettings) {
                    IonIcon("settings-outline", tint = AppMuted)
                }
            }

            Spacer(Modifier.height(26.dp))
            Text("Aprende inglês com confiança.", fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = AppText)
            Spacer(Modifier.height(7.dp))
            Text(
                "Aulas guiadas, exemplos reais, histórias, vocabulário e um dicionário bilingue.",
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = AppMuted
            )
            Spacer(Modifier.height(20.dp))

            SearchField(
                value = search,
                onValueChange = { search = it },
                placeholder = "Pesquisar aulas e assuntos..."
            )
            if (search.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text("Nenhuma aula encontrada.", fontSize = 13.sp, color = AppMuted, modifier = Modifier.padding(10.dp))
                } else {
                    filtered.forEach { LessonRow(it, progress.isLessonComplete(it.id), onClick = { onLesson(it) }) }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(18.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = AppPaleBlue, shape = RoundedCornerShape(9.dp)) {
                            Text("PLANO DE ESTUDO", color = AppBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IonIcon("school-outline", tint = AppBlue, size = 23.dp)
                    }
                    Spacer(Modifier.height(13.dp))
                    Text(
                        firstUnfinished?.title ?: "Curso concluído!",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        firstUnfinished?.description ?: "Revisa as aulas e continua a praticar.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = AppMuted
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { firstUnfinished?.let(onLesson) ?: onLessons() },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 17.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppBlue)
                    ) {
                        Text("Continuar a aprender", fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        IonIcon("arrow-forward-outline", tint = Color.White, size = 17.dp)
                    }
                }
            }

            Spacer(Modifier.height(23.dp))
            SectionHeading("O curso em números", action = "Ver aulas", onAction = onLessons)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(bundle.lessons.size.toString(), "Aulas", Modifier.weight(1f))
                StatCard(bundle.readings.size.toString(), "Histórias", Modifier.weight(1f))
                StatCard(bundle.vocabulary.size.toString(), "Termos", Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))
            SectionHeading("Explora o curso", action = "Todas as aulas", onAction = onLessons)
            val categories = bundle.lessons.map { it.category }.distinct()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    Surface(
                        color = AppSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AppBorder),
                        modifier = Modifier.clickable(onClick = onLessons)
                    ) {
                        Text(category, fontSize = 12.sp, color = AppText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeading("A começar por aqui", action = "Ver todas", onAction = onLessons)
            bundle.lessons.take(5).forEach { lesson ->
                LessonRow(lesson, progress.isLessonComplete(lesson.id)) { onLesson(lesson) }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeading("Mais formas de praticar")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Dicionário", "language-outline", Modifier.weight(1f), onDictionary)
                QuickAction("Histórias", "reader-outline", Modifier.weight(1f), onReadings)
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun LessonsScreen(
    lessons: List<Lesson>,
    progress: ProgressStore,
    onLesson: (Lesson) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf("Todos") }
    var category by rememberSaveable { mutableStateOf("Todas") }
    val categories = remember(lessons) { listOf("Todas") + lessons.map { it.category }.distinct() }
    val levels = listOf("Todos", "A1", "A2", "B1", "B2", "C1")
    val filtered = remember(query, level, category, lessons) {
        lessons.filter { lesson ->
            (level == "Todos" || lesson.level == level) &&
                (category == "Todas" || lesson.category == category) &&
                (query.isBlank() || lesson.title.contains(query, true) || lesson.description.contains(query, true))
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenHeader("Aulas", "Aprende por níveis e temas.")
        SearchField(query, { query = it }, "Pesquisar uma aula...")
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(levels) { value ->
                FilterChip(selected = level == value, onClick = { level = value }, label = { Text(value, fontSize = 11.sp) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(categories) { value ->
                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("${filtered.size} aulas", fontSize = 11.sp, color = AppMuted, modifier = Modifier.padding(bottom = 7.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(filtered, key = { it.id }) { lesson ->
                LessonRow(lesson, progress.isLessonComplete(lesson.id)) { onLesson(lesson) }
            }
            if (filtered.isEmpty()) item { EmptyState("Não encontrámos aulas com esses filtros.") }
        }
    }
}

@Composable
private fun LessonDetailScreen(lesson: Lesson, progress: ProgressStore, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isComplete by remember(lesson.id) { mutableStateOf(progress.isLessonComplete(lesson.id)) }
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(lesson.title, onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LabelPill(lesson.level, AppPaleBlue, AppBlue)
                LabelPill(lesson.category, AppSurface, AppMuted)
                LabelPill("${lesson.minutes} min", AppSurface, AppMuted)
            }
            Spacer(Modifier.height(14.dp))
            Text(lesson.title, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp, color = AppText)
            Spacer(Modifier.height(8.dp))
            Text(lesson.description, fontSize = 14.sp, color = AppMuted, lineHeight = 21.sp)

            if (lesson.sections.isNotEmpty()) {
                Spacer(Modifier.height(25.dp))
                SectionHeading("Explicação")
                lesson.sections.forEachIndexed { index, section ->
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = AppPaleBlue, shape = CircleShape, modifier = Modifier.size(25.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${index + 1}", fontSize = 11.sp, color = AppBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(9.dp))
                            Text(section.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppText)
                        }
                        Spacer(Modifier.height(8.dp))
                        HtmlText(section.body, fontSize = 14.sp, color = AppText, lineHeight = 22.sp)
                    }
                }
            }

            if (lesson.examples.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionHeading("Exemplos práticos")
                lesson.examples.forEach { example ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                        border = BorderStroke(1.dp, AppBorder)
                    ) {
                        Column(Modifier.padding(13.dp)) {
                            Text(example.english, fontWeight = FontWeight.SemiBold, color = AppText, fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(example.portuguese, color = AppMuted, fontSize = 13.sp, lineHeight = 19.sp)
                            if (example.note.isNotBlank()) {
                                Spacer(Modifier.height(5.dp))
                                Text(example.note, color = AppBlue, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            if (lesson.tip.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                InfoPanel("Dica importante", lesson.tip, "bulb-outline")
            }

            if (lesson.commonMistakes.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionHeading("Erros frequentes")
                lesson.commonMistakes.forEach { mistake ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8F3)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(13.dp)) {
                            Text("Evita: ${mistake.incorrect}", fontSize = 12.sp, color = Color(0xFFB35A32))
                            Spacer(Modifier.height(4.dp))
                            Text("Usa: ${mistake.correction}", fontSize = 13.sp, color = AppText, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(mistake.explanation, fontSize = 12.sp, color = AppMuted, lineHeight = 18.sp)
                        }
                    }
                }
            }

            if (lesson.vocabulary.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionHeading("Vocabulário desta aula")
                lesson.vocabulary.forEach { item -> VocabularyRow(item) }
            }

            if (lesson.quiz.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionHeading("Testa os teus conhecimentos")
                lesson.quiz.forEachIndexed { index, quiz -> QuizCard(quiz, index + 1) }
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    progress.markLessonComplete(lesson.id)
                    isComplete = true
                    Toast.makeText(context, "Aula marcada como concluída.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isComplete) AppGreen else AppBlue)
            ) {
                IonIcon("checkmark-circle-outline", tint = Color.White, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(if (isComplete) "Aula concluída" else "Marcar aula como concluída")
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ReadingsScreen(readings: List<Reading>, progress: ProgressStore, onReading: (Reading) -> Unit) {
    var level by rememberSaveable { mutableStateOf("Todos") }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(level, query, readings) {
        readings.filter {
            (level == "Todos" || it.level == level) &&
                (query.isBlank() || it.title.contains(query, true) || it.summary.contains(query, true))
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenHeader("Histórias e leituras", "Lê em inglês e compreende cada texto.")
        SearchField(query, { query = it }, "Pesquisar uma história...")
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf("Todos", "A1", "A2", "B1", "B2", "C1")) { item ->
                FilterChip(selected = level == item, onClick = { level = item }, label = { Text(item, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            items(filtered, key = { it.id }) { reading ->
                ReadingRow(reading, progress.isReadingComplete(reading.id)) { onReading(reading) }
            }
            if (filtered.isEmpty()) item { EmptyState("Nenhuma leitura encontrada.") }
        }
    }
}

@Composable
private fun ReadingDetailScreen(reading: Reading, progress: ProgressStore, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isComplete by remember(reading.id) { mutableStateOf(progress.isReadingComplete(reading.id)) }
    var showTranslation by rememberSaveable(reading.id) { mutableStateOf(false) }
    val translations = remember(reading.portugueseText) {
        reading.portugueseText.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotBlank() }
    }
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(reading.title, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LabelPill(reading.level, AppPaleBlue, AppBlue)
                LabelPill(reading.length, AppSurface, AppMuted)
                LabelPill("${reading.minutes} min", AppSurface, AppMuted)
            }
            Spacer(Modifier.height(14.dp))
            Text(reading.title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = AppText, lineHeight = 31.sp)
            Spacer(Modifier.height(7.dp))
            Text(reading.summary, fontSize = 14.sp, color = AppMuted, lineHeight = 21.sp)
            Spacer(Modifier.height(15.dp))
            OutlinedButton(
                onClick = { showTranslation = !showTranslation },
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)
            ) {
                IonIcon("language-outline", tint = AppBlue, size = 17.dp)
                Spacer(Modifier.width(7.dp))
                Text(if (showTranslation) "Ocultar tradução" else "Mostrar tradução", fontSize = 12.sp)
            }
            Spacer(Modifier.height(15.dp))
            reading.paragraphs.forEachIndexed { index, paragraph ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 11.dp),
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, AppBorder),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Text("${index + 1}", color = AppBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(paragraph, fontSize = 14.sp, lineHeight = 22.sp, color = AppText)
                        if (showTranslation && index < translations.size) {
                            Divider(Modifier.padding(vertical = 11.dp), color = AppBorder)
                            Text(translations[index], fontSize = 13.sp, lineHeight = 21.sp, color = AppMuted)
                        }
                    }
                }
            }
            if (reading.vocabulary.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SectionHeading("Vocabulário da história")
                reading.vocabulary.forEach { VocabularyRow(it) }
            }
            if (reading.questions.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionHeading("Compreensão do texto")
                reading.questions.forEachIndexed { index, quiz -> QuizCard(quiz, index + 1) }
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    progress.markReadingComplete(reading.id)
                    isComplete = true
                    Toast.makeText(context, "Leitura marcada como concluída.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isComplete) AppGreen else AppBlue)
            ) {
                IonIcon("checkmark-circle-outline", tint = Color.White, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(if (isComplete) "Leitura concluída" else "Marcar leitura como concluída")
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DictionaryScreen(bundle: CourseBundle, repository: DictionaryRepository) {
    var query by rememberSaveable { mutableStateOf("") }
    var directionName by rememberSaveable { mutableStateOf(DictionaryDirection.ENGLISH_TO_PORTUGUESE.name) }
    val direction = DictionaryDirection.valueOf(directionName)
    var results by remember { mutableStateOf<List<DictionaryEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(query, directionName) {
        val term = query.trim()
        if (term.isEmpty()) {
            results = emptyList()
            loading = false
            searched = false
        } else {
            delay(280)
            loading = true
            results = withContext(Dispatchers.IO) { repository.search(term, direction) }
            loading = false
            searched = true
        }
    }

    val courseMatches = remember(query, directionName, bundle.vocabulary) {
        val term = query.trim()
        if (term.isEmpty()) emptyList() else bundle.vocabulary.mapNotNull { item ->
            if (direction == DictionaryDirection.ENGLISH_TO_PORTUGUESE) {
                if (item.term.contains(term, true)) DictionaryEntry(item.term, item.meaning.split(';').map { it.trim() }.filter { it.isNotBlank() }, item.note, "Vocabulário do curso") else null
            } else {
                if (item.meaning.contains(term, true)) DictionaryEntry(item.meaning, listOf(item.term), item.note, "Vocabulário do curso") else null
            }
        }.take(10)
    }
    val combined = remember(results, courseMatches) {
        (results + courseMatches).distinctBy { (it.term + it.translations.joinToString()).lowercase() }.take(24)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ScreenHeader("Dicionário", "Consulta palavras e expressões nos dois sentidos.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DirectionButton(
                label = "EN → PT",
                selected = direction == DictionaryDirection.ENGLISH_TO_PORTUGUESE,
                modifier = Modifier.weight(1f)
            ) { directionName = DictionaryDirection.ENGLISH_TO_PORTUGUESE.name }
            DirectionButton(
                label = "PT → EN",
                selected = direction == DictionaryDirection.PORTUGUESE_TO_ENGLISH,
                modifier = Modifier.weight(1f)
            ) { directionName = DictionaryDirection.PORTUGUESE_TO_ENGLISH.name }
        }
        Spacer(Modifier.height(11.dp))
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = if (direction == DictionaryDirection.ENGLISH_TO_PORTUGUESE) "Escreve uma palavra em inglês..." else "Escreve uma palavra em português..."
        )
        Spacer(Modifier.height(11.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            border = BorderStroke(1.dp, AppBorder)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IonIcon("globe-outline", tint = AppBlue, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text("Dicionário bilingue offline. Pesquisa palavras e expressões comuns.", fontSize = 11.sp, color = AppMuted, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(13.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppBlue, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            query.isBlank() -> {
                Text("Pesquisas rápidas", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppText)
                Spacer(Modifier.height(8.dp))
                val examples = if (direction == DictionaryDirection.ENGLISH_TO_PORTUGUESE) {
                    listOf("learn", "beautiful", "take care", "bring up", "understand")
                } else listOf("aprender", "bonito", "ter cuidado", "compreender", "chegar")
                examples.forEach { sample ->
                    Row(
                        Modifier.fillMaxWidth().clickable { query = sample }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IonIcon("search-outline", tint = AppMuted, size = 16.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(sample, color = AppText, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        IonIcon("arrow-forward-outline", tint = AppMuted, size = 15.dp)
                    }
                    Divider(color = AppBorder)
                }
            }
            combined.isNotEmpty() -> {
                Text("${combined.size} resultados", fontSize = 11.sp, color = AppMuted, modifier = Modifier.padding(bottom = 7.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(combined, key = { it.term + it.translations.joinToString() }) { entry ->
                        DictionaryResultCard(entry)
                    }
                }
            }
            searched -> EmptyState("Não encontrámos esse termo. Experimenta outra grafia ou pesquisa a forma base do verbo.")
        }
    }
}

@Composable
private fun ProgressScreen(bundle: CourseBundle, progress: ProgressStore) {
    val lessonsDone = progress.completedLessonCount().coerceAtMost(bundle.lessons.size)
    val readingsDone = progress.completedReadingCount().coerceAtMost(bundle.readings.size)
    val percent = if (bundle.lessons.isEmpty()) 0 else (lessonsDone * 100 / bundle.lessons.size)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader("O teu progresso", "Acompanha as aulas e leituras concluídas.")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            border = BorderStroke(1.dp, AppBorder)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Progresso das aulas", fontSize = 14.sp, color = AppMuted)
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$percent%", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = AppText)
                    Spacer(Modifier.width(8.dp))
                    Text("concluído", fontSize = 12.sp, color = AppMuted, modifier = Modifier.padding(bottom = 5.dp))
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFFE4E8EE))) {
                    Box(Modifier.fillMaxWidth(percent / 100f).height(8.dp).clip(CircleShape).background(AppBlue))
                }
                Spacer(Modifier.height(9.dp))
                Text("$lessonsDone de ${bundle.lessons.size} aulas concluídas", fontSize = 11.sp, color = AppMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(readingsDone.toString(), "Leituras concluídas", Modifier.weight(1f))
            StatCard(bundle.vocabulary.size.toString(), "Entradas no curso", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        SectionHeading("Progresso por nível")
        listOf("A1", "A2", "B1", "B2", "C1").forEach { level ->
            val total = bundle.lessons.count { it.level == level }
            val done = bundle.lessons.count { it.level == level && progress.isLessonComplete(it.id) }
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(level, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppText)
                    Spacer(Modifier.weight(1f))
                    Text("$done / $total", fontSize = 11.sp, color = AppMuted)
                }
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color(0xFFE4E8EE))) {
                    val fraction = if (total == 0) 0f else done.toFloat() / total
                    Box(Modifier.fillMaxWidth(fraction).height(7.dp).clip(CircleShape).background(AppBlue))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        InfoPanel("Continua a praticar", "O progresso fica guardado neste dispositivo. Marca cada aula e leitura depois de terminares o estudo.", "trophy-outline")
        Spacer(Modifier.height(25.dp))
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DetailTopBar("Definições e créditos", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("English Course", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppText)
            Spacer(Modifier.height(5.dp))
            Text("Aplicação Android nativa · versão 1.0.0", fontSize = 12.sp, color = AppMuted)
            Spacer(Modifier.height(22.dp))
            SectionHeading("Conteúdo incluído")
            listOf(
                "93 aulas organizadas do A1 ao C1",
                "16 histórias com tradução, glossário e compreensão",
                "Vocabulário, verbos irregulares e phrasal verbs",
                "Questionários e acompanhamento do progresso"
            ).forEach { BulletText(it) }
            Spacer(Modifier.height(20.dp))
            SectionHeading("Dicionário bilingue")
            Text(
                "As bases inglês–português e português–inglês são fornecidas pelo WikDict, com dados derivados do Wiktionary via DBnary. A distribuição segue a licença Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0). A pesquisa funciona offline depois da instalação.",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = AppMuted
            )
            Spacer(Modifier.height(8.dp))
            Text("https://www.wikdict.com/page/download", fontSize = 11.sp, color = AppBlue)
            Spacer(Modifier.height(20.dp))
            SectionHeading("Ícones")
            Text("Ionicons v8.1.0, da Ionic Team, distribuído ao abrigo da licença MIT.", fontSize = 13.sp, lineHeight = 20.sp, color = AppMuted)
            Spacer(Modifier.height(20.dp))
            Text("A aplicação utiliza apenas tema claro.", fontSize = 12.sp, color = AppMuted)
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 15.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppText)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = AppMuted, lineHeight = 18.sp)
    }
}

@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                IonIcon("chevron-back-outline", tint = AppText, size = 22.dp)
            }
            Text(title, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AppText)
            Spacer(Modifier.width(10.dp))
        }
        Divider(color = AppBorder)
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        placeholder = { Text(placeholder, fontSize = 12.sp, color = AppMuted) },
        leadingIcon = { IonIcon("search-outline", tint = AppMuted, size = 19.dp) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                androidx.compose.material3.IconButton(onClick = { onValueChange("") }) {
                    IonIcon("close-outline", tint = AppMuted, size = 18.dp)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun SectionHeading(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppText)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp)) {
                Text(action, fontSize = 11.sp, color = AppBlue)
            }
        }
    }
}

@Composable
private fun StatCard(number: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 11.dp)) {
            Text(number, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppText)
            Spacer(Modifier.height(3.dp))
            Text(label, fontSize = 10.sp, color = AppMuted, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, complete: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = if (complete) AppPaleGreen else AppSurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(39.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    IonIcon(if (complete) "checkmark-circle-outline" else "book-outline", tint = if (complete) AppGreen else AppBlue, size = 20.dp)
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(lesson.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(lesson.description, fontSize = 10.sp, color = AppMuted, maxLines = 2, lineHeight = 14.sp, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text("${lesson.level}  ·  ${lesson.category}  ·  ${lesson.minutes} min", fontSize = 9.sp, color = AppMuted)
            }
            Spacer(Modifier.width(5.dp))
            IonIcon("chevron-forward-outline", tint = AppMuted, size = 17.dp)
        }
    }
}

@Composable
private fun ReadingRow(reading: Reading, complete: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelPill(reading.level, AppPaleBlue, AppBlue)
                Spacer(Modifier.width(6.dp))
                LabelPill(reading.length, AppSurface, AppMuted)
                Spacer(Modifier.weight(1f))
                if (complete) IonIcon("checkmark-circle-outline", tint = AppGreen, size = 18.dp)
            }
            Spacer(Modifier.height(10.dp))
            Text(reading.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppText)
            Spacer(Modifier.height(5.dp))
            Text(reading.summary, fontSize = 11.sp, color = AppMuted, lineHeight = 17.sp)
            Spacer(Modifier.height(8.dp))
            Text("${reading.minutes} min de leitura", fontSize = 10.sp, color = AppBlue)
        }
    }
}

@Composable
private fun LabelPill(text: String, background: Color, foreground: Color) {
    Surface(color = background, shape = RoundedCornerShape(7.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), fontSize = 9.sp, color = foreground, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QuickAction(title: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            IonIcon(icon, tint = AppBlue, size = 19.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppText)
        }
    }
}

@Composable
private fun VocabularyRow(item: VocabularyItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.term, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppText)
            if (item.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(item.note, fontSize = 9.sp, color = AppMuted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(item.meaning, modifier = Modifier.weight(1f), fontSize = 12.sp, color = AppMuted, lineHeight = 17.sp)
    }
    Divider(color = AppBorder)
}

@Composable
private fun QuizCard(quiz: QuizItem, number: Int) {
    var selected by remember(quiz.question) { mutableIntStateOf(-1) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Column(Modifier.padding(13.dp)) {
            Text("PERGUNTA $number", fontSize = 9.sp, color = AppBlue, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(quiz.question, fontSize = 13.sp, color = AppText, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
            Spacer(Modifier.height(9.dp))
            quiz.options.forEachIndexed { index, option ->
                val isSelected = selected == index
                val isCorrect = index == quiz.answerIndex
                val background = when {
                    selected >= 0 && isCorrect -> AppPaleGreen
                    isSelected -> Color(0xFFFFF1EF)
                    else -> Color.White
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                        .background(background).clickable { selected = index }.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = if (isSelected) AppBlue else Color(0xFFE9EDF2), shape = CircleShape, modifier = Modifier.size(19.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", fontSize = 9.sp, color = if (isSelected) Color.White else AppMuted, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(option, fontSize = 12.sp, color = AppText, modifier = Modifier.weight(1f), lineHeight = 17.sp)
                    if (selected >= 0 && isCorrect) IonIcon("checkmark-circle-outline", tint = AppGreen, size = 17.dp)
                }
            }
            if (selected >= 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (selected == quiz.answerIndex) "Correto! ${quiz.explanation}" else "Ainda não. ${quiz.explanation}",
                    color = if (selected == quiz.answerIndex) AppGreen else Color(0xFFB35A32),
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun DictionaryResultCard(entry: DictionaryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.term, fontSize = 15.sp, color = AppText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                LabelPill(entry.source, AppSurface, AppMuted)
            }
            Spacer(Modifier.height(7.dp))
            entry.translations.forEachIndexed { index, translation ->
                if (index > 0) Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", color = AppBlue, fontSize = 14.sp)
                    Spacer(Modifier.width(7.dp))
                    Text(translation, fontSize = 13.sp, color = AppText, lineHeight = 19.sp)
                }
            }
            if (entry.sense.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(entry.sense, fontSize = 10.sp, color = AppMuted, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun DirectionButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(43.dp),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, if (selected) AppBlue else AppBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) AppPaleBlue else Color.White,
            contentColor = if (selected) AppBlue else AppMuted
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoPanel(title: String, body: String, icon: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = AppPaleBlue)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            IonIcon(icon, tint = AppBlue, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, color = AppText, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(body, color = AppText, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun BulletText(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        IonIcon("checkmark-outline", tint = AppGreen, size = 17.dp)
        Spacer(Modifier.width(9.dp))
        Text(text, fontSize = 13.sp, color = AppText, lineHeight = 19.sp)
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IonIcon("search-outline", tint = AppMuted, size = 26.dp)
        Spacer(Modifier.height(9.dp))
        Text(message, color = AppMuted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun HtmlText(html: String, fontSize: androidx.compose.ui.unit.TextUnit, color: Color, lineHeight: androidx.compose.ui.unit.TextUnit) {
    val annotated = remember(html) { htmlToAnnotatedString(html) }
    Text(text = annotated, fontSize = fontSize, lineHeight = lineHeight, color = color)
}

private fun htmlToAnnotatedString(html: String): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    return AnnotatedString.Builder().apply {
        append(spanned.toString())
        spanned.getSpans(0, spanned.length, StyleSpan::class.java).forEach { span ->
            val start = spanned.getSpanStart(span).coerceIn(0, spanned.length)
            val end = spanned.getSpanEnd(span).coerceIn(start, spanned.length)
            if (end > start) {
                val style = when {
                    span.style and Typeface.BOLD != 0 -> SpanStyle(fontWeight = FontWeight.SemiBold)
                    span.style and Typeface.ITALIC != 0 -> SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    else -> SpanStyle()
                }
                addStyle(style, start, end)
            }
        }
        spanned.getSpans(0, spanned.length, UnderlineSpan::class.java).forEach { span ->
            val start = spanned.getSpanStart(span).coerceIn(0, spanned.length)
            val end = spanned.getSpanEnd(span).coerceIn(start, spanned.length)
            if (end > start) addStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), start, end)
        }
    }.toAnnotatedString()
}
