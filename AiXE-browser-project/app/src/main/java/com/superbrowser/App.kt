package com.superbrowser

import android.annotation.SuppressLint
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.*
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SuperBrowserApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    override fun onCreate() {
        super.onCreate()
        // NOWE: globalny łapacz nieobsłużonych wyjątków — zapisuje pełny stack trace do pliku w pamięci
        // wewnętrznej appki. Bez tego przy crashu na starcie (zamknięcie appki natychmiast po otwarciu)
        // nie ma żadnego sposobu, żeby zobaczyć PRZYCZYNĘ bez podłączenia telefonu kablem i adb logcat.
        // MainActivity przy kolejnym uruchomieniu odczytuje ten plik i pokazuje go w dialogu na ekranie.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val writer = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(writer))
                getFileStreamPath("last_crash.txt").writeText(writer.toString())
            } catch (e: Exception) {
                // Jeśli nawet zapis loga się nie uda, nie chcemy przez to zapętlić crasha — po prostu lecimy dalej.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long,
    // NOWE: favicon strony zakodowany jako Base64 PNG — zapisywany osobno, gdy WebView go dostarczy
    // (patrz onReceivedIcon w BrowserViewModel.createTab), bo w momencie zapisu wpisu historii
    // (onPageFinished) favicon zwykle jeszcze nie dotarł.
    val faviconBase64: String? = null
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long
)

// NOWE: wynik zapytania grupującego historię po adresie — zasila kafelki "często odwiedzane" na ekranie nowej karty.
data class FrequentSite(
    val url: String,
    val title: String,
    val visitCount: Int,
    val faviconBase64: String? = null
)

// NOWE: gotowy preset gradientu tła dla ekranu nowej karty. normalColors używane w zwykłej karcie,
// privateColors to osobny, ciemniejszy zestaw automatycznie stosowany w trybie prywatnym —
// użytkownik wybiera tylko "motyw" (id), a odpowiedni wariant kolorystyczny dobiera się sam
// zależnie od tego, czy aktywna karta jest prywatna.
data class GradientPreset(
    val id: String,
    val label: String,
    val normalColors: List<androidx.compose.ui.graphics.Color>,
    val privateColors: List<androidx.compose.ui.graphics.Color>
)

val gradientPresets: List<GradientPreset> = listOf(
    GradientPreset(
        id = "none",
        label = "Brak",
        normalColors = emptyList(),
        privateColors = emptyList()
    ),
    GradientPreset(
        id = "sunset",
        label = "Zachód słońca",
        normalColors = listOf(androidx.compose.ui.graphics.Color(0xFFFF9A5C), androidx.compose.ui.graphics.Color(0xFFFF6F91)),
        privateColors = listOf(androidx.compose.ui.graphics.Color(0xFF4A1942), androidx.compose.ui.graphics.Color(0xFF2D0A2E))
    ),
    GradientPreset(
        id = "ocean",
        label = "Ocean",
        normalColors = listOf(androidx.compose.ui.graphics.Color(0xFF2193B0), androidx.compose.ui.graphics.Color(0xFF6DD5ED)),
        privateColors = listOf(androidx.compose.ui.graphics.Color(0xFF0B2545), androidx.compose.ui.graphics.Color(0xFF13315C))
    ),
    GradientPreset(
        id = "forest",
        label = "Las",
        normalColors = listOf(androidx.compose.ui.graphics.Color(0xFF11998E), androidx.compose.ui.graphics.Color(0xFF38EF7D)),
        privateColors = listOf(androidx.compose.ui.graphics.Color(0xFF0B3D2E), androidx.compose.ui.graphics.Color(0xFF145C43))
    ),
    GradientPreset(
        id = "purple",
        label = "Fiolet",
        normalColors = listOf(androidx.compose.ui.graphics.Color(0xFF667EEA), androidx.compose.ui.graphics.Color(0xFF764BA2)),
        privateColors = listOf(androidx.compose.ui.graphics.Color(0xFF2D1B4E), androidx.compose.ui.graphics.Color(0xFF1A0B2E))
    )
)

// ===== NOWE: mała, autorska "ciekawostka dnia" na ekranie nowej karty =====
// Pomysł: zamiast pustego miejsca nad kafelkami, jedno krótkie, kulturalno-naukowe zdanie, które zmienia
// się RAZ DZIENNIE (deterministycznie po dacie — bez żadnego zapytania sieciowego, więc nowa karta nadal
// otwiera się natychmiast). Ten sam dzień = ta sama ciekawostka na wszystkich kartach, żeby nie migało
// przy każdym "+". Świadomie nietechniczne, różnorodne tematy — ma być mała przyjemność, nie kolejna reklama.
private val dailyFacts = listOf(
    "Miód znaleziony w egipskich grobowcach sprzed 3000 lat wciąż nadaje się do jedzenia — praktycznie się nie psuje.",
    "Ośmiornice mają trzy serca i niebieską krew.",
    "Banany są jagodami botanicznie rzecz biorąc, a truskawki — nie.",
    "Wenus to jedyna planeta Układu Słonecznego, która obraca się w przeciwną stronę niż większość pozostałych.",
    "Pierwsza wiadomość e-mail została wysłana w 1971 roku, na rok przed powstaniem symbolu \"@\" w tym kontekście.",
    "Serce wieloryba błękitnego jest tak duże, że mały człowiek zmieściłby się w jego tętnicy głównej.",
    "Islandia nie ma komarów — to jedno z niewielu miejsc na Ziemi, gdzie ich nie znaleziono.",
    "Chmura burzowa może ważyć więcej niż milion kilogramów, a mimo to unosi się w powietrzu.",
    "Słowo \"OK\" jest jednym z najczęściej rozpoznawanych wyrazów na świecie, niezależnie od języka.",
    "Kotom brakuje w genach receptora smaku słodkiego — dlatego go nie czują.",
    "Najkrótsza wojna w historii (Anglo-Zanzibarska) trwała około 38 minut.",
    "Jeden dzień na Wenus (obrót wokół własnej osi) trwa dłużej niż jej rok (obieg wokół Słońca).",
    "Ludzki nos potrafi rozróżnić ponad bilion różnych zapachów.",
    "Pierwszy komputer o wadze prawie 30 ton (ENIAC) miał moc obliczeniową mniejszą niż dzisiejszy kalkulator.",
    "Rekin grenlandzki może żyć nawet ponad 300 lat — to najdłużej żyjący znany kręgowiec.",
)

// Deterministyczny wybór po numerze dnia w roku — bez żadnego stanu ani zapytań, ta sama ciekawostka
// przez cały dzień, inna następnego dnia (z zawijaniem po długości listy).
private fun factOfTheDay(): String {
    val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    return dailyFacts[dayOfYear % dailyFacts.size]
}

// NOWE: koduje Bitmap (favicon strony) do Base64 PNG do zapisu w Room (kolumna faviconBase64).
// Skalujemy do max 64x64 — favicony i tak zwykle są małe (16-48px), ale to zabezpieczenie na wypadek,
// gdyby jakaś strona zwróciła coś większego; trzyma bazę danych lekką niezależnie od źródła ikony.
private fun bitmapToBase64(bitmap: Bitmap): String? {
    return try {
        val scaled = if (bitmap.width > 64 || bitmap.height > 64) {
            Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        } else {
            bitmap
        }
        val stream = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
        android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntity)
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 300")
    fun getAll(): Flow<List<HistoryEntity>>
    @Query("DELETE FROM history")
    suspend fun clear()
    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)
    // NOWE: zapisuje favicon dla WSZYSTKICH wpisów historii z danym adresem — favicon dociera asynchronicznie
    // (onReceivedIcon), zwykle już PO zapisaniu wpisu w historii, więc aktualizujemy go osobno, po fakcie.
    @Query("UPDATE history SET faviconBase64 = :faviconBase64 WHERE url = :url")
    suspend fun updateFavicon(url: String, faviconBase64: String)
    // NOWE: 12 ostatnio odwiedzonych, UNIKALNYCH adresów — grupowanie po url usuwa duplikaty, sortowanie
    // po najnowszej wizycie (nie po liczbie wizyt) sprawia, że kafelki na ekranie nowej karty odświeżają się
    // same przy każdej nowej wizycie: najstarszy z 12 wypada, najnowszy wchodzi na górę (Flow z Room emituje
    // nową listę automatycznie, bez żadnego dodatkowego kodu odświeżającego).
    // MAX(faviconBase64) w grupowaniu: skoro updateFavicon nadpisuje favicon we WSZYSTKICH wierszach z tym
    // url, każda grupa ma co najwyżej jedną niepustą wartość — MAX ją wybiera i ignoruje NULL-e.
    @Query(
        """
        SELECT url, title, COUNT(*) as visitCount, MAX(faviconBase64) as faviconBase64
        FROM history
        GROUP BY url
        ORDER BY MAX(visitedAt) DESC
        LIMIT 12
        """
    )
    fun getMostVisited(): Flow<List<FrequentSite>>
}

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: BookmarkEntity)
    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAll(): Flow<List<BookmarkEntity>>
    @Query("SELECT COUNT(*) FROM bookmarks WHERE url = :url")
    suspend fun countByUrl(url: String): Int
}

// NOWE: version 2 (dodano kolumnę faviconBase64 w history) — fallbackToDestructiveMigration() oznacza,
// że przy tej jednej aktualizacji stara historia/zakładki zostaną wyczyszczone zamiast crashować appkę
// przez brak zdefiniowanej migracji. Do zaakceptowania na tym etapie (projekt osobisty, brak zewnętrznych
// użytkowników z danymi do zachowania) — gdyby to była apka z produkcyjnymi userami, trzeba by napisać
// prawdziwą migrację (ALTER TABLE history ADD COLUMN faviconBase64 TEXT).
@Database(entities = [HistoryEntity::class, BookmarkEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "browser.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class AppTheme { SYSTEM, LIGHT, DARK }

class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme")
    private val searchEngineKey = stringPreferencesKey("search_engine")
    private val adBlockKey = booleanPreferencesKey("ad_block_enabled")
    private val downloadFolderUriKey = stringPreferencesKey("download_folder_uri")
    // NOWE: wybrany preset gradientu tła dla ekranu nowej karty (patrz gradientPresets w kodzie UI).
    private val newTabGradientKey = stringPreferencesKey("new_tab_gradient_id")
    val theme: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { AppTheme.valueOf(it) } ?: AppTheme.SYSTEM
    }
    val searchEngineUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[searchEngineKey] ?: "https://www.google.com/search?q={query}"
    }
    val adBlockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[adBlockKey] ?: true
    }
    val downloadFolderUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[downloadFolderUriKey]
    }
    val newTabGradientId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[newTabGradientKey] ?: "none"
    }
    suspend fun setTheme(value: AppTheme) {
        context.dataStore.edit { it[themeKey] = value.name }
    }
    suspend fun setSearchEngineUrl(value: String) {
        context.dataStore.edit { it[searchEngineKey] = value }
    }
    suspend fun setAdBlockEnabled(value: Boolean) {
        context.dataStore.edit { it[adBlockKey] = value }
    }
    suspend fun setDownloadFolderUri(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(downloadFolderUriKey) else prefs[downloadFolderUriKey] = value
        }
    }
    suspend fun setNewTabGradientId(value: String) {
        context.dataStore.edit { it[newTabGradientKey] = value }
    }
}

object WebViewHardening {

    @SuppressLint("SetJavaScriptEnabled")
    fun applyHardenedSettings(webView: WebView) {
        val settings = webView.settings
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.savePassword = false
        settings.safeBrowsingEnabled = true
        // POPRAWKA: NEVER_ALLOW blokował całe strony, które ładują choć jeden zasób po http (dość częste
        // na starszych/mniejszych stronach) — to była jedna z przyczyn "niektóre strony się nie wczytują".
        // COMPATIBILITY_MODE to zachowanie zbliżone do współczesnego Chrome: nadal ostrożne, ale nie zabija
        // całej strony przez jeden nie-krytyczny zasób mixed-content.
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        val isDebugBuild = (webView.context.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(isDebugBuild)
    }

    // NOWE: dodatkowe twardnienie dla kart prywatnych — bez cache na dysku i bez zapisu formularzy,
    // żeby jak najmniej śladów sesji prywatnej trafiało poza samą kartę.
    fun applyPrivateModeSettings(webView: WebView) {
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
    }
}

// NOWE: parser prostego podzbioru składni list adblockowych typu EasyList/EasyPrivacy.
// Obsługiwane: reguły blokowania hosta w formacie „||domena.com^” (najczęstszy i najbardziej uniwersalny typ
// reguły w tych listach — blokuje domenę i wszystkie jej subdomeny). Pomijane celowo: komentarze („!”), wyjątki
// („@@...”) i reguły kosmetyczne / CSS-owe (zawierające „##” albo „#@#”) — to nie jest pełny silnik adblocka,
// tylko blokowanie po nazwie hosta w shouldInterceptRequest, ale to pokrywa zdecydowaną większość realnych
// domen reklamowych/trackingowych z prawdziwych list.
object AdBlockRuleParser {
    fun parseDomainRules(lines: Sequence<String>): Set<String> {
        val domains = mutableSetOf<String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("!")) continue
            if (line.startsWith("@@")) continue
            if (line.contains("##") || line.contains("#@#")) continue
            if (!line.startsWith("||")) continue
            var domain = line.removePrefix("||")
            val caretIdx = domain.indexOf('^')
            if (caretIdx >= 0) domain = domain.substring(0, caretIdx)
            val slashIdx = domain.indexOf('/')
            if (slashIdx >= 0) domain = domain.substring(0, slashIdx)
            if (domain.isNotBlank() && !domain.contains("*")) domains.add(domain.lowercase())
        }
        return domains
    }

    // Wczytuje reguły z pliku w app/src/main/assets/<fileName>. Żeby użyć pełnej, aktualnej listy (np. prawdziwego
    // EasyList z https://easylist.to/easylist/easylist.txt), wystarczy podmienić TREŚĆ tego pliku w assets —
    // bez żadnych zmian w kodzie, bo parser rozumie ten sam format co oryginalne listy (w zakresie reguł ||...^).
    fun loadFromAssets(context: Context, fileName: String = "adblock_list.txt"): Set<String> {
        return try {
            context.assets.open(fileName).bufferedReader().useLines { lines -> parseDomainRules(lines) }
        } catch (e: Exception) {
            emptySet()
        }
    }
}

object DangerousUrlGuard {

    private val ipHostPattern = Pattern.compile(
        "^https?://(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$"
    )
    private val userInfoTrickPattern = Pattern.compile("^https?://[^/]*@")
    private val suspiciousBrandSubdomain = Pattern.compile(
        "(paypal|apple|google|microsoft|bank|allegro|inpost|dhl)\\.[a-z0-9-]+\\.(xyz|top|club|work|gq|tk|zip|mov)",
        Pattern.CASE_INSENSITIVE
    )
    private val punycodePattern = Pattern.compile("://xn--", Pattern.CASE_INSENSITIVE)
    enum class RiskLevel { SAFE, SUSPICIOUS, BLOCK }
    data class Verdict(val risk: RiskLevel, val reason: String?)
    fun evaluate(url: String): Verdict {
        if (userInfoTrickPattern.matcher(url).find()) {
            return Verdict(RiskLevel.BLOCK, "Adres próbuje ukryć prawdziwy host za znakiem @")
        }
        if (suspiciousBrandSubdomain.matcher(url).find()) {
            return Verdict(RiskLevel.BLOCK, "Znana marka użyta jako subdomena podejrzanej domeny")
        }
        if (ipHostPattern.matcher(url).find()) {
            return Verdict(RiskLevel.SUSPICIOUS, "Adres to bezpośrednie IP zamiast nazwy domeny")
        }
        if (punycodePattern.matcher(url).find()) {
            return Verdict(RiskLevel.SUSPICIOUS, "Adres zawiera zakodowane znaki Unicode (możliwy atak homograficzny)")
        }
        return Verdict(RiskLevel.SAFE, null)
    }
}

object SafeBrowsingCallback {

    fun handle(threatType: Int, callback: SafeBrowsingResponse, onBlocked: (String) -> Unit) {
        val reason = when (threatType) {
            WebViewClient.SAFE_BROWSING_THREAT_MALWARE -> "Wykryto złośliwe oprogramowanie na tej stronie"
            WebViewClient.SAFE_BROWSING_THREAT_PHISHING -> "Wykryto próbę phishingu na tej stronie"
            WebViewClient.SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE -> "Wykryto niechciane oprogramowanie"
            WebViewClient.SAFE_BROWSING_THREAT_BILLING -> "Wykryto podejrzane praktyki rozliczeniowe"
            else -> "Wykryto zagrożenie bezpieczeństwa na tej stronie"
        }
        onBlocked(reason)
        callback.backToSafety(true)
    }
}

object CertificateInspector {

    data class CertWarning(
        val errorDescription: String,
        val issuedTo: String,
        val issuedBy: String,
        val validFrom: String,
        val validUntil: String,
        val url: String,
        val handler: SslErrorHandler
    )
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    fun describe(error: SslError, handler: SslErrorHandler): CertWarning {
        val cert: SslCertificate = error.certificate
        val description = when (error.primaryError) {
            SslError.SSL_UNTRUSTED -> "Certyfikat pochodzi od niezaufanego wystawcy"
            SslError.SSL_EXPIRED -> "Certyfikat wygasł"
            SslError.SSL_IDMISMATCH -> "Certyfikat nie pasuje do adresu strony"
            SslError.SSL_NOTYETVALID -> "Certyfikat jeszcze nie jest ważny"
            SslError.SSL_DATE_INVALID -> "Nieprawidłowa data certyfikatu"
            SslError.SSL_INVALID -> "Certyfikat jest nieprawidłowy"
            else -> "Nierozpoznany problem z certyfikatem"
        }
        return CertWarning(
            errorDescription = description,
            issuedTo = cert.issuedTo?.cName ?: "nieznany",
            issuedBy = cert.issuedBy?.cName ?: "nieznany",
            validFrom = cert.validNotBeforeDate?.let { dateFormat.format(it) } ?: "?",
            validUntil = cert.validNotAfterDate?.let { dateFormat.format(it) } ?: "?",
            url = error.url ?: "",
            handler = handler
        )
    }
    fun proceedDespiteWarning(handler: SslErrorHandler) = handler.proceed()
    fun cancelConnection(handler: SslErrorHandler) = handler.cancel()
}

data class PageLoadError(
    val title: String,
    val message: String,
    val isHttpError: Boolean = false
)

data class GeolocationRequestInfo(
    val origin: String,
    val callback: GeolocationPermissions.Callback
)

class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val webView: WebView,
    val isPrivate: Boolean = false
) {
    var title = mutableStateOf("Nowa karta")
    var url = mutableStateOf("")
    var favicon = mutableStateOf<Bitmap?>(null)
    var canGoBack = mutableStateOf(false)
    var canGoForward = mutableStateOf(false)
    var isBookmarked = mutableStateOf(false)
    var isLoading = mutableStateOf(false)
    var loadProgress = mutableStateOf(0f)
    var securityWarning = mutableStateOf<String?>(null)
    var pendingCertError = mutableStateOf<CertificateInspector.CertWarning?>(null)
    var pageError = mutableStateOf<PageLoadError?>(null)
    var customView = mutableStateOf<View?>(null)
    var customViewCallback: WebChromeClient.CustomViewCallback? = null
    var findInPageActive = mutableStateOf(false)
    var findInPageQuery = mutableStateOf("")
    var findInPageMatchCount = mutableStateOf(0)
    var findInPageActiveIndex = mutableStateOf(0)
    var isDesktopMode = mutableStateOf(false)
    // NOWE: true dla świeżo utworzonej karty, która jeszcze nie zaczęła nawigacji — pokazuje ekran skrótów
    // zamiast WebView. Ustawiane na false przy pierwszej realnej nawigacji (patrz navigate() i onPageStarted).
    var isNewTabPage = mutableStateOf(false)
    // POPRAWKA: zwykły var (nie stan Compose) — zapamiętuje adres, który WŁAŚNIE nawigujemy/klikamy,
    // ustawiany PRZED faktycznym załadowaniem (patrz navigate() i shouldOverrideUrlLoading). Potrzebny,
    // bo onReceivedSslError porównywał błąd do view.url, które w momencie błędu certyfikatu często jest
    // jeszcze poprzednią stroną (albo puste na świeżej karcie) — przez co ostrzeżenie o certyfikacie dla
    // GŁÓWNEJ strony było mylnie traktowane jako błąd podzasobu i po cichu anulowane (strona po prostu
    // nigdy się nie wczytywała, bez żadnego komunikatu). lastNavigationUrl śledzi to poprawnie.
    var lastNavigationUrl: String? = null
}

class DownloadRecord(
    val downloadManagerId: Long,
    val fileName: String,
    val mimeType: String?,
    // NOWE: moment rozpoczęcia pobierania — potrzebny do sortowania "Najnowsze/Najstarsze" na liście.
    val startedAt: Long = System.currentTimeMillis()
) {
    var progress = mutableStateOf(0)
    var status = mutableStateOf("Pobieranie...")
    // NOWE: rozmiar pliku w bajtach (znany po pierwszej odpowiedzi z DownloadManagera) — do wyświetlenia na karcie.
    var totalBytes = mutableStateOf(0L)
    // NOWE: gotowy do otwarcia URI ukończonego pliku (patrz pollDownloadProgress) — używany zarówno przez
    // dialog "Pobrano — otworzyć?" jak i przycisk otwierania na liście pobranych.
    var openUri = mutableStateOf<Uri?>(null)
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<SuperBrowserApp>()
    private val historyDao = app.database.historyDao()
    private val bookmarkDao = app.database.bookmarkDao()
    private val settings = app.settings
    val tabs = mutableStateListOf<BrowserTab>()
    val activeTabId = mutableStateOf<String?>(null)
    val downloads = mutableStateListOf<DownloadRecord>()
    var pendingFileChooserCallback: ValueCallback<Array<Uri>>? = null
    val fileChooserIntent = mutableStateOf<Intent?>(null)
    val pendingWebPermissionRequest = mutableStateOf<PermissionRequest?>(null)
    val pendingGeolocationRequest = mutableStateOf<GeolocationRequestInfo?>(null)
    // NOWE: gdy pobieranie się kończy, ustawiamy tu jego rekord — BrowserScreen pokazuje wtedy globalny
    // dialog "Pobrano — otworzyć plik?", niezależnie od tego, na którym ekranie akurat jest użytkownik.
    val justCompletedDownload = mutableStateOf<DownloadRecord?>(null)
    val requestNotificationPermission = mutableStateOf(false)
    val theme: Flow<AppTheme> = settings.theme
    val searchEngineUrlFlow: Flow<String> = settings.searchEngineUrl
    // (usunięto: homeUrlFlow — koncept "strony startowej" nie jest już potrzebny, nowa karta zawsze
    // pokazuje ekran skrótów, patrz showStartPage=true w createTab)
    val adBlockEnabledFlow: Flow<Boolean> = settings.adBlockEnabled
    val downloadFolderUriFlow: Flow<String?> = settings.downloadFolderUri
    // NOWE: wybrany gradient tła ekranu nowej karty.
    val newTabGradientIdFlow: Flow<String> = settings.newTabGradientId
    val historyFlow: Flow<List<HistoryEntity>> = historyDao.getAll()
    val bookmarksFlow: Flow<List<BookmarkEntity>> = bookmarkDao.getAll()
    // NOWE: najczęściej odwiedzane strony — zasila kafelki na ekranie nowej karty.
    val frequentSitesFlow: Flow<List<FrequentSite>> = historyDao.getMostVisited()
    // "Ciekawostka dnia" pokazywana na ekranie nowej karty — z lokalnej listy (dailyFacts), bez sieci.
    val dailyFact = factOfTheDay()
    @Volatile private var adBlockEnabledCache = true
    init {
        viewModelScope.launch { adBlockEnabledFlow.collect { adBlockEnabledCache = it } }
        // NOWE: wczytanie reguł adblock z pliku assets przy starcie. Samo parsowanie pliku (I/O) robimy
        // na Dispatchers.IO przez withContext, ale WRACAMY na domyślny dispatcher viewModelScope (Main),
        // zanim zapiszemy wynik do mutableStateOf — Compose Snapshot System wymaga, żeby stan był
        // modyfikowany na wątku głównym; zapis bezpośrednio z Dispatchers.IO powodował crash na starcie
        // (IllegalStateException: Reading a state that was created after the snapshot was taken).
        viewModelScope.launch {
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                AdBlockRuleParser.loadFromAssets(app)
            }
            adBlockDomains.value = loaded.ifEmpty { fallbackAdBlockDomains }
        }
    }
    private val closedTabsStack = ArrayDeque<String>()
    fun activeTab(): BrowserTab? = tabs.find { it.id == activeTabId.value }

    // NOWE: showStartPage=true tworzy kartę bez ładowania żadnego adresu — BrowserScreen pokaże wtedy ekran
    // skrótów (kafelki najczęściej odwiedzanych stron) zamiast WebView, dopóki użytkownik nie zacznie nawigacji.
    @SuppressLint("SetJavaScriptEnabled")
    fun createTab(url: String, select: Boolean = true, isPrivate: Boolean = false, showStartPage: Boolean = false): BrowserTab {
        val webView = WebView(app).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
        }
        WebViewHardening.applyHardenedSettings(webView)
        if (isPrivate) WebViewHardening.applyPrivateModeSettings(webView)
        val tab = BrowserTab(webView = webView, isPrivate = isPrivate)
        tab.isNewTabPage.value = showStartPage
        tabs.add(tab)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) {
                tab.isLoading.value = true
                tab.url.value = u
                tab.securityWarning.value = null
                tab.pageError.value = null
                tab.isNewTabPage.value = false // zabezpieczenie: każda realna nawigacja chowa ekran skrótów
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url.toString()
                val verdict = DangerousUrlGuard.evaluate(target)
                when (verdict.risk) {
                    DangerousUrlGuard.RiskLevel.BLOCK -> {
                        tab.securityWarning.value = verdict.reason
                        return true
                    }
                    DangerousUrlGuard.RiskLevel.SUSPICIOUS -> {
                        tab.securityWarning.value = verdict.reason
                    }
                    DangerousUrlGuard.RiskLevel.SAFE -> Unit
                }
                // POPRAWKA: zapamiętujemy adres, który faktycznie zaraz zacznie się ładować (np. klik w link
                // na stronie), żeby onReceivedSslError mógł go poprawnie rozpoznać jako nawigację głównej ramki.
                tab.lastNavigationUrl = target
                return false
            }
            override fun onPageFinished(view: WebView, u: String) {
                tab.isLoading.value = false
                tab.url.value = u
                tab.canGoBack.value = view.canGoBack()
                tab.canGoForward.value = view.canGoForward()
                refreshBookmarkState(tab)
                if (!isPrivate) {
                    viewModelScope.launch {
                        historyDao.insert(
                            HistoryEntity(url = u, title = view.title ?: u, visitedAt = System.currentTimeMillis())
                        )
                    }
                }
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (adBlockEnabledCache && isAdHost(request.url.host)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }
            override fun onSafeBrowsingHit(
                view: WebView,
                request: WebResourceRequest,
                threatType: Int,
                callback: SafeBrowsingResponse
            ) {
                SafeBrowsingCallback.handle(threatType, callback) { reason ->
                    tab.securityWarning.value = reason
                }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                // POPRAWKA: porównanie było do view.url, który w chwili błędu certyfikatu zwykle jeszcze NIE
                // zdążył się zaktualizować (ustawia się dopiero w onPageStarted, który leci po tym callbacku,
                // albo jest wciąż poprzednią stroną / pusty na świeżej karcie). Efekt: błąd certyfikatu dla
                // GŁÓWNEJ strony prawie zawsze wypadał jako "nie main frame" -> handler.cancel() bez żadnego
                // komunikatu -> strona po prostu nie wczytywała się, wyglądając jak losowy błąd. Teraz
                // porównujemy do adresu, który sami zainicjowaliśmy jako nawigację (lastNavigationUrl),
                // po hoście (żeby przetrwać ewentualne przekierowania w obrębie tej samej domeny).
                val requestedHost = tab.lastNavigationUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                val errorHost = runCatching { Uri.parse(error.url).host }.getOrNull()
                val isMainPageRequest = requestedHost != null && requestedHost == errorHost
                if (!isMainPageRequest) {
                    handler.cancel()
                    return
                }
                tab.pendingCertError.value = CertificateInspector.describe(error, handler)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    tab.isLoading.value = false
                    tab.pageError.value = PageLoadError(
                        title = "Nie można wczytać strony",
                        message = "Sprawdź połączenie z internetem i spróbuj ponownie.\n\n${error.description}"
                    )
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    tab.isLoading.value = false
                    tab.pageError.value = PageLoadError(
                        title = "Błąd ${errorResponse.statusCode}",
                        message = "Serwer zwrócił błąd podczas wczytywania tej strony.",
                        isHttpError = true
                    )
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                tab.title.value = title?.takeIf { it.isNotBlank() } ?: "Nowa karta"
            }
            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                tab.favicon.value = icon
                // NOWE: zapis favicony do historii (żeby kafelki na ekranie nowej karty mogły pokazać
                // prawdziwe logo strony, nie tylko jedną literę). Pomijamy karty prywatne — te i tak
                // nie zapisują historii (patrz onPageFinished), więc nie ma czego aktualizować.
                if (icon != null && !isPrivate) {
                    val urlAtIconTime = tab.url.value
                    if (urlAtIconTime.isNotBlank()) {
                        viewModelScope.launch {
                            val encoded = bitmapToBase64(icon)
                            if (encoded != null) historyDao.updateFavicon(urlAtIconTime, encoded)
                        }
                    }
                }
            }
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                tab.loadProgress.value = newProgress / 100f
            }
            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                pendingFileChooserCallback?.onReceiveValue(null)
                pendingFileChooserCallback = filePathCallback
                fileChooserIntent.value = fileChooserParams.createIntent()
                return true
            }
            override fun onPermissionRequest(request: PermissionRequest) {
                pendingWebPermissionRequest.value = request
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                pendingGeolocationRequest.value = GeolocationRequestInfo(origin, callback)
            }
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                // POPRAWKA (crash): "New WebView for popup window must not have been previously navigated."
                // createTab(url = "about:blank", ...) wywoływało w środku webView.loadUrl("about:blank") ZANIM
                // ten WebView trafił do transport.webView — Chromium wymaga, żeby WebView przekazywany jako
                // cel okna popup (window.open() / target="_blank") był kompletnie "dziewiczy", nienawigowany
                // nawet do about:blank. Stąd crash przy KAŻDEJ stronie, która otwiera link w nowym oknie/karcie
                // (bardzo częste — np. wyniki wyszukiwania, artykuły z linkami target="_blank").
                // Naprawa: tworzymy kartę z pustym url i showStartPage=false, żeby createTab NIE wołało loadUrl —
                // sam WebView dostanie nawigację od silnika Chromium w momencie podpięcia transportu.
                val newTab = createTab(url = "", select = true, isPrivate = isPrivate, showStartPage = false)
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = newTab.webView
                resultMsg.sendToTarget()
                return true
            }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                tab.customViewCallback?.onCustomViewHidden()
                tab.customView.value = view
                tab.customViewCallback = callback
            }
            override fun onHideCustomView() {
                tab.customViewCallback?.onCustomViewHidden()
                tab.customView.value = null
                tab.customViewCallback = null
            }
        }
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                tab.findInPageMatchCount.value = numberOfMatches
                tab.findInPageActiveIndex.value = activeMatchOrdinal
            }
        }
        webView.setDownloadListener { downloadUrl, _, contentDisposition, mimeType, _ ->
            requestNotificationPermission.value = true
            startDownload(downloadUrl, contentDisposition, mimeType)
        }
        if (!showStartPage && url.isNotBlank()) {
            tab.lastNavigationUrl = url
            webView.loadUrl(url)
        }
        if (select) activeTabId.value = tab.id
        return tab
    }
    fun closeTab(tabId: String) {
        val idx = tabs.indexOfFirst { it.id == tabId }
        if (idx == -1) return
        val closedTab = tabs[idx]
        val closedUrl = closedTab.url.value
        if (closedUrl.isNotBlank() && !closedTab.isPrivate) {
            closedTabsStack.addLast(closedUrl)
            if (closedTabsStack.size > 15) closedTabsStack.removeFirst()
        }
        val wasPrivate = closedTab.isPrivate
        if (wasPrivate) {
            closedTab.webView.apply {
                clearHistory()
                clearCache(true)
                clearFormData()
            }
        }
        closedTab.webView.destroy()
        tabs.removeAt(idx)
        // POPRAWKA (prywatność): CookieManager w WebView jest GLOBALNY — dzielony przez wszystkie karty,
        // prywatne i zwykłe. Dopóki chociaż jedna karta prywatna jest jeszcze otwarta, nie kasujemy ciasteczek
        // (żeby nie wylogować z niej użytkownika w trakcie sesji). Ale gdy zamykamy WŁAŚNIE OSTATNIĄ kartę
        // prywatną, czyścimy cookies/localStorage od razu — to gwarantuje, że żadna sesja/logowanie z trybu
        // prywatnego nie przetrwa poza tę sesję i nie "wycieknie" do zwykłych kart otwartych później.
        // Zastrzeżenie: pełna izolacja (żeby prywatna i zwykła karta mogły być zalogowane RÓWNOCZEŚNIE na tej
        // samej stronie jako różne konta) wymagałaby osobnego katalogu danych WebView na proces
        // (WebView.setDataDirectorySuffix) i w praktyce osobnego procesu dla trybu prywatnego — to bigger
        // architektoniczna zmiana, do rozważenia osobno, jeśli to ma być priorytet.
        if (wasPrivate && tabs.none { it.isPrivate }) {
            clearCookiesAndSiteData()
        }
        if (activeTabId.value == tabId) {
            activeTabId.value = tabs.getOrNull(idx.coerceAtMost(tabs.lastIndex))?.id
        }
        if (tabs.isEmpty()) {
            createTab("", showStartPage = true)
        }
    }
    fun restoreLastClosedTab() {
        if (closedTabsStack.isEmpty()) return
        val url = closedTabsStack.removeLast()
        createTab(url, select = true)
    }
    fun selectTab(tabId: String) {
        activeTabId.value = tabId
        activeTab()?.let { refreshBookmarkState(it) }
    }
    fun navigate(input: String) {
        val tab = activeTab() ?: return
        if (input.isBlank()) return
        viewModelScope.launch {
            val template = searchEngineUrlFlow.first()
            val target = normalizeUrl(input, template)
            val verdict = DangerousUrlGuard.evaluate(target)
            when (verdict.risk) {
                DangerousUrlGuard.RiskLevel.BLOCK -> {
                    tab.securityWarning.value = verdict.reason
                    return@launch
                }
                DangerousUrlGuard.RiskLevel.SUSPICIOUS -> {
                    tab.securityWarning.value = verdict.reason
                }
                DangerousUrlGuard.RiskLevel.SAFE -> Unit
            }
            tab.isNewTabPage.value = false // wychodzimy z ekranu skrótów, gdy tylko użytkownik faktycznie nawiguje
            // POPRAWKA: zapamiętaj docelowy adres PRZED loadUrl, żeby ewentualny błąd certyfikatu dla tej
            // nawigacji dało się poprawnie skojarzyć z główną ramką (patrz onReceivedSslError i komentarz
            // przy BrowserTab.lastNavigationUrl).
            tab.lastNavigationUrl = target
            tab.webView.loadUrl(target)
        }
    }
    private fun normalizeUrl(input: String, searchTemplate: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val afterScheme = trimmed.substringAfter("://")
            return if (afterScheme.isNotBlank()) trimmed else searchUrl(trimmed, searchTemplate)
        }
        val domainPattern = Regex("^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+(:[0-9]+)?(/.*)?$")
        val looksLikeUrl = !trimmed.contains(" ") && domainPattern.matches(trimmed)
        if (looksLikeUrl) return "https://$trimmed"
        return searchUrl(trimmed, searchTemplate)
    }
    fun translatePage(targetLang: String = "pl") {
        val tab = activeTab() ?: return
        val currentUrl = tab.webView.url ?: tab.url.value
        if (currentUrl.isBlank()) return
        val translated = buildTranslateUrl(currentUrl, targetLang) ?: return
        tab.lastNavigationUrl = translated
        tab.webView.loadUrl(translated)
    }
    private fun buildTranslateUrl(original: String, targetLang: String): String? {
        return try {
            val uri = Uri.parse(original)
            val host = uri.host ?: return null
            if (host.endsWith(".translate.goog")) return original
            val proxyHost = host.replace(".", "-") + ".translate.goog"
            uri.buildUpon()
                .authority(proxyHost)
                .appendQueryParameter("_x_tr_sl", "auto")
                .appendQueryParameter("_x_tr_tl", targetLang)
                .appendQueryParameter("_x_tr_hl", targetLang)
                .appendQueryParameter("_x_tr_pto", "wapp")
                .build()
                .toString()
        } catch (e: Exception) {
            null
        }
    }
    private fun searchUrl(rawInput: String, searchTemplate: String): String {
        val query = Uri.encode(rawInput.trim())
        return if (searchTemplate.contains("{query}")) {
            searchTemplate.replace("{query}", query)
        } else {
            searchTemplate + query
        }
    }
    fun goBack() {
        val tab = activeTab() ?: return
        if (tab.webView.canGoBack()) tab.webView.goBack()
    }
    fun goForward() {
        val tab = activeTab() ?: return
        if (tab.webView.canGoForward()) tab.webView.goForward()
    }
    fun reload() {
        activeTab()?.webView?.reload()
    }
    fun cancelCertificateWarning(tab: BrowserTab) {
        tab.pendingCertError.value?.handler?.let { CertificateInspector.cancelConnection(it) }
        tab.pendingCertError.value = null
    }
    fun proceedDespiteCertificateWarning(tab: BrowserTab) {
        tab.pendingCertError.value?.handler?.let { CertificateInspector.proceedDespiteWarning(it) }
        tab.pendingCertError.value = null
    }
    fun dismissSecurityWarning(tab: BrowserTab) {
        tab.securityWarning.value = null
    }
    fun exitFullscreenVideo(tab: BrowserTab) {
        tab.customViewCallback?.onCustomViewHidden()
        tab.customView.value = null
        tab.customViewCallback = null
    }
    fun startFindInPage(tab: BrowserTab) {
        tab.findInPageActive.value = true
    }
    fun stopFindInPage(tab: BrowserTab) {
        tab.findInPageActive.value = false
        tab.findInPageQuery.value = ""
        tab.findInPageMatchCount.value = 0
        tab.webView.clearMatches()
    }
    fun updateFindInPageQuery(tab: BrowserTab, query: String) {
        tab.findInPageQuery.value = query
        if (query.isNotEmpty()) tab.webView.findAllAsync(query) else tab.webView.clearMatches()
    }
    fun findNext(tab: BrowserTab) = tab.webView.findNext(true)
    fun findPrevious(tab: BrowserTab) = tab.webView.findNext(false)
    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    fun toggleDesktopMode(tab: BrowserTab) {
        val enabled = !tab.isDesktopMode.value
        tab.isDesktopMode.value = enabled
        tab.webView.settings.apply {
            userAgentString = if (enabled) desktopUserAgent else null
            useWideViewPort = enabled
            loadWithOverviewMode = enabled
        }
        tab.webView.reload()
    }
    private fun refreshBookmarkState(tab: BrowserTab) {
        viewModelScope.launch {
            tab.isBookmarked.value = bookmarkDao.countByUrl(tab.url.value) > 0
        }
    }
    fun toggleBookmark() {
        val tab = activeTab() ?: return
        viewModelScope.launch {
            if (bookmarkDao.countByUrl(tab.url.value) > 0) {
                bookmarkDao.deleteByUrl(tab.url.value)
                tab.isBookmarked.value = false
            } else {
                bookmarkDao.insert(
                    BookmarkEntity(url = tab.url.value, title = tab.title.value, createdAt = System.currentTimeMillis())
                )
                tab.isBookmarked.value = true
            }
        }
    }
    fun removeBookmark(url: String) {
        viewModelScope.launch {
            bookmarkDao.deleteByUrl(url)
            activeTab()?.let { if (it.url.value == url) it.isBookmarked.value = false }
        }
    }
    fun clearHistory() {
        viewModelScope.launch { historyDao.clear() }
    }
    // NOWE: usunięcie POJEDYNCZEGO wpisu historii — DAO już to umiało (deleteById), tylko nic
    // wcześniej z tego nie korzystało. Zasila przycisk usuwania na karcie w HistoryScreen.
    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch { historyDao.deleteById(id) }
    }
    fun clearCookiesAndSiteData() {
        android.webkit.CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        android.webkit.WebStorage.getInstance().deleteAllData()
    }
    fun clearBrowserCache() {
        tabs.forEach { it.webView.clearCache(true) }
    }
    fun setTheme(value: AppTheme) = viewModelScope.launch { settings.setTheme(value) }
    fun setSearchEngineUrl(value: String) = viewModelScope.launch { settings.setSearchEngineUrl(value) }
    // (usunięto: setHomeUrl — patrz komentarz przy homeUrlFlow)
    fun setAdBlockEnabled(value: Boolean) = viewModelScope.launch { settings.setAdBlockEnabled(value) }
    fun setDownloadFolderUri(value: String?) = viewModelScope.launch { settings.setDownloadFolderUri(value) }
    fun setNewTabGradientId(value: String) = viewModelScope.launch { settings.setNewTabGradientId(value) }
    // NOWE: lista reguł wczytywana asynchronicznie z assets/adblock_list.txt (patrz init powyżej i AdBlockRuleParser).
    // mutableStateOf, bo isAdHost jest odpytywane z shouldInterceptRequest (wątek roboczy WebView) — czytanie
    // .value spoza Compose działa bez problemu, po prostu bez subskrypcji reaktywnej, co tu wystarcza.
    private val adBlockDomains = mutableStateOf<Set<String>>(emptySet())
    // Awaryjny fallback, gdyby plik assets/adblock_list.txt nie istniał albo był pusty — appka nie zostaje
    // wtedy całkiem bez adblocka.
    private val fallbackAdBlockDomains = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adservice.google.pl", "pagead2.googlesyndication.com",
        "ads.google.com", "adnxs.com", "adsrvr.org", "advertising.com",
        "amazon-adsystem.com", "criteo.com", "criteo.net", "outbrain.com",
        "taboola.com", "scorecardresearch.com", "quantserve.com", "moatads.com",
        "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com",
        "smartadserver.com", "adform.net", "adsafeprotected.com", "media.net",
        "yieldmo.com", "gumgum.com", "bidswitch.net", "3lift.com",
        "spotxchange.com", "teads.tv", "sharethrough.com", "adtechus.com",
        "serving-sys.com", "flashtalking.com", "revcontent.com", "mgid.com",
        "popads.net", "propellerads.com", "adcolony.com"
    )
    private fun isAdHost(host: String?): Boolean {
        if (host == null) return false
        val domains = adBlockDomains.value
        return domains.any { host == it || host.endsWith(".$it") }
    }
    private fun startDownload(url: String, contentDisposition: String?, mimeType: String?) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setMimeType(mimeType)
        val manager = app.getSystemService(DownloadManager::class.java)
        val id = manager.enqueue(request)
        val record = DownloadRecord(downloadManagerId = id, fileName = fileName, mimeType = mimeType)
        downloads.add(record)
        pollDownloadProgress(manager, record)
    }
    private fun pollDownloadProgress(manager: DownloadManager, record: DownloadRecord) {
        viewModelScope.launch {
            // POPRAWKA (status "buguje się" / zawiesza na "Pobieranie..."): jeśli wiersz zapytania
            // zniknie z bazy DownloadManagera (np. ktoś usunie pobranie ręcznie z systemowej appki
            // "Pliki"), poprzedni kod pytał w kółko bez końca i status NIGDY się nie zmieniał — to była
            // przyczyna wrażenia, że pobieranie "wisi". missingRowStreak liczy kolejne puste odpowiedzi;
            // po ok. 3 sekundach (5 x 600ms) uznajemy pobieranie za utracone i kończymy pętlę z jasnym statusem.
            var missingRowStreak = 0
            while (true) {
                val query = DownloadManager.Query().setFilterById(record.downloadManagerId)
                val cursor = manager.query(query)
                if (cursor == null) {
                    missingRowStreak++
                } else {
                    cursor.use {
                        if (it.moveToFirst()) {
                            missingRowStreak = 0
                            val bytesIdx = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalIdx = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            val statusIdx = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val total = it.getLong(totalIdx)
                            val downloaded = it.getLong(bytesIdx)
                            if (total > 0) {
                                record.progress.value = ((downloaded * 100) / total).toInt()
                                record.totalBytes.value = total
                            }
                            when (it.getInt(statusIdx)) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    record.status.value = "Ukończono"
                                    record.progress.value = 100
                                    val localUriIdx = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                    val localUriString = if (localUriIdx >= 0) it.getString(localUriIdx) else null
                                    // NOWE: kanoniczny, "otwieralny" content:// URI do pobranego pliku —
                                    // potrzebny zarówno dla dialogu "Pobrano — otworzyć?", jak i dla
                                    // przycisku otwierania bezpośrednio z listy pobranych.
                                    record.openUri.value = try {
                                        manager.getUriForDownloadedFile(record.downloadManagerId)
                                    } catch (e: Exception) {
                                        localUriString?.let { s -> runCatching { Uri.parse(s) }.getOrNull() }
                                    }
                                    // NOWE: pokazuje globalny dialog "Pobrano plik — otworzyć?" (patrz BrowserScreen).
                                    justCompletedDownload.value = record
                                    if (localUriString != null) {
                                        val customFolder = downloadFolderUriFlow.first()
                                        if (customFolder != null) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                copyDownloadToCustomFolder(
                                                    sourceUri = Uri.parse(localUriString),
                                                    fileName = record.fileName,
                                                    mimeType = record.mimeType,
                                                    folderUriString = customFolder
                                                )
                                            }
                                        }
                                    }
                                    return@launch
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    record.status.value = "Przerwano"
                                    return@launch
                                }
                                DownloadManager.STATUS_PAUSED -> record.status.value = "Wstrzymano"
                                DownloadManager.STATUS_PENDING -> record.status.value = "Oczekiwanie..."
                                else -> record.status.value = "Pobieranie..."
                            }
                        } else {
                            missingRowStreak++
                        }
                    }
                }
                if (missingRowStreak >= 5) {
                    record.status.value = "Nieznany błąd"
                    return@launch
                }
                delay(600)
            }
        }
    }
    // NOWE: otwiera pobrany plik domyślną aplikacją systemową (np. przeglądarką PDF, galerią) na podstawie
    // zapisanego openUri. Gdy nic nie umie otworzyć tego typu pliku (albo URI jest nieznany), spada do
    // otwarcia systemowego ekranu "Pobrane pliki", żeby użytkownik i tak trafił w okolice pliku.
    fun openDownloadedFile(record: DownloadRecord) {
        val uri = record.openUri.value
        if (uri == null) {
            openSystemDownloadsScreen()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, record.mimeType?.takeIf { it.isNotBlank() } ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        } catch (e: Exception) {
            openSystemDownloadsScreen()
        }
    }
    private fun openSystemDownloadsScreen() {
        try {
            app.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }
    private fun copyDownloadToCustomFolder(
        sourceUri: Uri,
        fileName: String,
        mimeType: String?,
        folderUriString: String
    ) {
        try {
            val treeUri = Uri.parse(folderUriString)
            val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val newFileUri = android.provider.DocumentsContract.createDocument(
                app.contentResolver, parentDocUri, mimeType ?: "application/octet-stream", fileName
            ) ?: return
            app.contentResolver.openInputStream(sourceUri)?.use { input ->
                app.contentResolver.openOutputStream(newFileUri)?.use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
        }
    }
}

private val LightColors = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF0078D4))
private val DarkColors = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF429CE3))

@Composable
fun SuperBrowserTheme(appTheme: AppTheme, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (appTheme) {
        AppTheme.SYSTEM -> systemDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        useDark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

private data class AddressSuggestion(val title: String, val url: String, val isBookmark: Boolean)

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = viewModel(),
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LaunchedEffect(Unit) {
        // NOWE: pierwsza karta też startuje na ekranie skrótów zamiast od razu ładować stronę domową —
        // spójne z zachowaniem przycisku „+” niżej.
        if (viewModel.tabs.isEmpty()) viewModel.createTab("", showStartPage = true)
    }
    var menuOpen by remember { mutableStateOf(false) }
    val activeTab = viewModel.activeTab()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addressText by remember(activeTab?.id) { mutableStateOf(activeTab?.url?.value ?: "") }
    LaunchedEffect(activeTab?.url?.value) { addressText = activeTab?.url?.value ?: "" }
    var addressFocused by remember { mutableStateOf(false) }
    val history by viewModel.historyFlow.collectAsState(initial = emptyList())
    val bookmarks by viewModel.bookmarksFlow.collectAsState(initial = emptyList())
    // NOWE: najczęściej odwiedzane strony na ekran skrótów nowej karty.
    val frequentSites by viewModel.frequentSitesFlow.collectAsState(initial = emptyList())
    // NOWE: wybrany gradient tła nowej karty (Ustawienia -> Tło nowej karty).
    val newTabGradientId by viewModel.newTabGradientIdFlow.collectAsState(initial = "none")
    val addressSuggestions = remember(addressText, addressFocused, history, bookmarks) {
        if (!addressFocused || addressText.isBlank()) {
            emptyList()
        } else {
            val query = addressText.trim().lowercase()
            val fromBookmarks = bookmarks
                .filter { it.url.lowercase().contains(query) || it.title.lowercase().contains(query) }
                .map { AddressSuggestion(it.title.ifBlank { it.url }, it.url, isBookmark = true) }
            val fromHistory = history
                .filter { it.url.lowercase().contains(query) || it.title.lowercase().contains(query) }
                .map { AddressSuggestion(it.title.ifBlank { it.url }, it.url, isBookmark = false) }
            (fromBookmarks + fromHistory).distinctBy { it.url }.take(5)
        }
    }
    val tabListState = rememberLazyListState()
    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = viewModel.pendingFileChooserCallback
        viewModel.pendingFileChooserCallback = null
        val uris = if (result.resultCode == android.app.Activity.RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        } else null
        callback?.onReceiveValue(uris)
    }
    LaunchedEffect(viewModel.fileChooserIntent.value) {
        viewModel.fileChooserIntent.value?.let { intent ->
            fileChooserLauncher.launch(intent)
            viewModel.fileChooserIntent.value = null
        }
    }
    val webPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val request = viewModel.pendingWebPermissionRequest.value
        viewModel.pendingWebPermissionRequest.value = null
        if (request != null) {
            if (results.values.all { it }) request.grant(request.resources) else request.deny()
        }
    }
    LaunchedEffect(viewModel.pendingWebPermissionRequest.value) {
        val request = viewModel.pendingWebPermissionRequest.value ?: return@LaunchedEffect
        val androidPermissions = request.resources.mapNotNull { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> android.Manifest.permission.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> android.Manifest.permission.RECORD_AUDIO
                else -> null
            }
        }.distinct()
        if (androidPermissions.isEmpty()) {
            request.deny()
            viewModel.pendingWebPermissionRequest.value = null
            return@LaunchedEffect
        }
        val alreadyGranted = androidPermissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (alreadyGranted) {
            request.grant(request.resources)
            viewModel.pendingWebPermissionRequest.value = null
        } else {
            webPermissionLauncher.launch(androidPermissions.toTypedArray())
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val geoRequest = viewModel.pendingGeolocationRequest.value
        viewModel.pendingGeolocationRequest.value = null
        geoRequest?.callback?.invoke(geoRequest.origin, granted, false)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(viewModel.requestNotificationPermission.value) {
        if (viewModel.requestNotificationPermission.value) {
            viewModel.requestNotificationPermission.value = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!granted) notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val view = LocalView.current
    val isFullscreenVideo = activeTab?.customView?.value != null
    LaunchedEffect(isFullscreenVideo) {
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (isFullscreenVideo) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        val isActivePrivate = activeTab?.isPrivate == true
        Surface(
            tonalElevation = 1.dp,
            color = if (isActivePrivate) Color(0xFF241033) else MaterialTheme.colorScheme.surface,
            modifier = Modifier.statusBarsPadding()
        ) {
            Column {
                if (isActivePrivate) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Tryb prywatny — historia nie jest zapisywana",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactIcon(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        desc = "Dalej",
                        enabled = activeTab?.canGoForward?.value == true,
                        tint = if (isActivePrivate) Color.White else LocalContentColor.current,
                        onClick = { viewModel.goForward() }
                    )
                    CompactIcon(
                        icon = Icons.Default.Refresh,
                        desc = "Odśwież",
                        tint = if (isActivePrivate) Color.White else LocalContentColor.current,
                        onClick = { viewModel.reload() }
                    )
                    Spacer(Modifier.width(4.dp))
                    AddressSecurityIcon(tab = activeTab)
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(
                                if (isActivePrivate) Color(0xFF3A1750)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (addressText.isEmpty()) {
                            Text(
                                "Wpisz adres lub wyszukaj",
                                fontSize = 13.sp,
                                color = if (isActivePrivate) Color.White.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        BasicTextField(
                            value = addressText,
                            onValueChange = { addressText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                color = if (isActivePrivate) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                viewModel.navigate(addressText)
                                addressFocused = false
                                focusManager.clearFocus()
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { addressFocused = it.isFocused }
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    CompactIcon(
                        icon = if (activeTab?.isBookmarked?.value == true) Icons.Default.Star else Icons.Outlined.StarOutline,
                        desc = "Zakładka",
                        tint = if (activeTab?.isBookmarked?.value == true) Color(0xFFF2B807)
                        else if (isActivePrivate) Color.White else LocalContentColor.current,
                        onClick = { viewModel.toggleBookmark() }
                    )
                    TabCounterButton(
                        count = viewModel.tabs.size,
                        tint = if (isActivePrivate) Color.White else LocalContentColor.current,
                        onClick = {
                            val idx = viewModel.tabs.indexOfFirst { it.id == viewModel.activeTabId.value }
                            if (idx >= 0) {
                                scope.launch { tabListState.animateScrollToItem(idx) }
                            }
                        }
                    )
                    Box {
                    CompactIcon(
                        icon = Icons.Default.MoreVert,
                        desc = "Menu",
                        tint = if (isActivePrivate) Color.White else LocalContentColor.current,
                        onClick = { menuOpen = true }
                    )
                    // ===== POPRAWKA: spójne menu — WSZYSTKIE pozycje mają teraz ikonę (Historia/Zakładki/Pobrane/Ustawienia
                    // wcześniej jej nie miały, przez co menu wyglądało niechlujnie/niespójnie), pogrupowane sekcje
                    // rozdzielone HorizontalDivider, plus modifier na szerokość menu żeby tekst się nie zawijał na małych ekranach.
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier.widthIn(min = 220.dp, max = 300.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Udostępnij stronę") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                val url = activeTab?.url?.value
                                if (!url.isNullOrBlank()) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, url)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Udostępnij przez"))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Kopiuj link") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                val url = activeTab?.url?.value
                                if (!url.isNullOrBlank()) {
                                    clipboardManager.setText(AnnotatedString(url))
                                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                                        scope.launch { snackbarHostState.showSnackbar("Skopiowano link") }
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Przetłumacz stronę") },
                            leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                viewModel.translatePage()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Znajdź na stronie") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                activeTab?.let { viewModel.startFindInPage(it) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (activeTab?.isDesktopMode?.value == true) "Wersja mobilna" else "Wersja na komputer") },
                            leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null) },
                            trailingIcon = {
                                if (activeTab?.isDesktopMode?.value == true) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            },
                            onClick = {
                                menuOpen = false
                                activeTab?.let { viewModel.toggleDesktopMode(it) }
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Nowa karta prywatna") },
                            leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                viewModel.createTab("", select = true, isPrivate = true, showStartPage = true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Przywróć zamkniętą kartę") },
                            leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                viewModel.restoreLastClosedTab()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Historia") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = { menuOpen = false; onOpenHistory() }
                        )
                        DropdownMenuItem(
                            text = { Text("Zakładki") },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                            onClick = { menuOpen = false; onOpenBookmarks() }
                        )
                        DropdownMenuItem(
                            text = { Text("Pobrane") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = { menuOpen = false; onOpenDownloads() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Ustawienia") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = { menuOpen = false; onOpenSettings() }
                        )
                    }
                }
            }
            if (activeTab?.findInPageActive?.value == true) {
                val findFocusRequester = remember { FocusRequester() }
                LaunchedEffect(activeTab.id) { findFocusRequester.requestFocus() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(
                                if (isActivePrivate) Color(0xFF3A1750)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (activeTab.findInPageQuery.value.isEmpty()) {
                            Text(
                                "Znajdź na stronie",
                                fontSize = 13.sp,
                                color = if (isActivePrivate) Color.White.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        BasicTextField(
                            value = activeTab.findInPageQuery.value,
                            onValueChange = { viewModel.updateFindInPageQuery(activeTab, it) },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                color = if (isActivePrivate) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            keyboardActions = KeyboardActions(onDone = { viewModel.findNext(activeTab) }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(findFocusRequester)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    if (activeTab.findInPageQuery.value.isNotEmpty()) {
                        Text(
                            text = if (activeTab.findInPageMatchCount.value > 0)
                                "${activeTab.findInPageActiveIndex.value + 1}/${activeTab.findInPageMatchCount.value}"
                            else "0/0",
                            fontSize = 12.sp,
                            color = if (isActivePrivate) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    CompactIcon(
                        icon = Icons.Default.KeyboardArrowUp,
                        desc = "Poprzednie",
                        tint = if (isActivePrivate) Color.White else LocalContentColor.current,
                        onClick = { viewModel.findPrevious(activeTab) }
                    )
                    CompactIcon(
                        icon = Icons.Default.KeyboardArrowDown,
                        desc = "Następne",
                        tint = if (isActivePrivate) Color.White else LocalContentColor.current,
                        onClick = { viewModel.findNext(activeTab) }
                    )
                    CompactIcon(
                        icon = Icons.Default.Close,
                        desc = "Zamknij wyszukiwanie",
                        tint = if (isActivePrivate) Color.White else LocalContentColor.current,
                        onClick = { viewModel.stopFindInPage(activeTab) }
                    )
                }
            }
            if (addressSuggestions.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isActivePrivate) Color(0xFF2B1338) else MaterialTheme.colorScheme.surface)
                ) {
                    addressSuggestions.forEach { suggestion ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    addressText = suggestion.url
                                    viewModel.navigate(suggestion.url)
                                    addressFocused = false
                                    focusManager.clearFocus()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (suggestion.isBookmark) Icons.Default.Star else Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (suggestion.isBookmark) Color(0xFFF2B807)
                                else if (isActivePrivate) Color.White.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    suggestion.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isActivePrivate) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    suggestion.url,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isActivePrivate) Color.White.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            if (activeTab?.isLoading?.value == true) {
                val progress = activeTab.loadProgress.value.coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progress)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF7EC8F2), Color(0xFF8DE89A))
                                )
                            )
                    )
                }
            }
            activeTab?.securityWarning?.value?.let { warning ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissSecurityWarning(activeTab) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Zamknij", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            }
        }
        Box(Modifier.fillMaxSize().weight(1f)) {
            activeTab?.let { tab ->
              if (tab.isNewTabPage.value) {
                // NOWE: ekran skrótów zamiast pustej strony domowej na nowej karcie.
                StartPageContent(
                    isPrivate = tab.isPrivate,
                    frequentSites = frequentSites,
                    gradientId = newTabGradientId,
                    fact = viewModel.dailyFact,
                    onSiteClick = { clickedUrl ->
                        addressText = clickedUrl
                        viewModel.navigate(clickedUrl)
                    },
                    onOpenPrivateTab = {
                        viewModel.createTab("", select = true, isPrivate = true, showStartPage = true)
                    }
                )
              } else {
                WebViewContainer(webView = tab.webView, modifier = Modifier.fillMaxSize())
                tab.pageError.value?.let { err ->
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (err.isHttpError) Icons.Default.ErrorOutline else Icons.Default.WifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(err.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                err.message,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = {
                                tab.pageError.value = null
                                tab.webView.reload()
                            }) { Text("Spróbuj ponownie") }
                            if (err.isHttpError) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { tab.pageError.value = null }) {
                                    Text("Pokaż stronę mimo to")
                                }
                            }
                        }
                    }
                }
              }
            }
            if (activeTab == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Kliknij + aby dodać kartę",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    Modifier.weight(1f),
                    state = tabListState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(viewModel.tabs, key = { it.id }) { tab ->
                        TabChip(
                            tab = tab,
                            selected = tab.id == viewModel.activeTabId.value,
                            onSelect = { viewModel.selectTab(tab.id) },
                            onClose = { viewModel.closeTab(tab.id) }
                        )
                    }
                }
                IconButton(onClick = { viewModel.createTab("", showStartPage = true) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Nowa karta", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 52.dp)
        )
        activeTab?.customView?.value?.let { customView ->
            AndroidView(
                factory = { customView },
                modifier = Modifier.fillMaxSize().background(Color.Black)
            )
        }
    }
activeTab?.pendingCertError?.value?.let { warning ->
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Niezaufane połączenie") },
            text = {
                Column {
                    Text(warning.errorDescription, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Adres: ${warning.url}", style = MaterialTheme.typography.bodySmall)
                    Text("Wydany dla: ${warning.issuedTo}", style = MaterialTheme.typography.bodySmall)
                    Text("Wystawca: ${warning.issuedBy}", style = MaterialTheme.typography.bodySmall)
                    Text("Ważność: ${warning.validFrom} – ${warning.validUntil}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelCertificateWarning(activeTab) }) {
                    Text("Wróć do bezpiecznej strony")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.proceedDespiteCertificateWarning(activeTab) }) {
                    Text("Kontynuuj mimo to", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
    viewModel.pendingGeolocationRequest.value?.let { geoRequest ->
        AlertDialog(
            onDismissRequest = {
                geoRequest.callback.invoke(geoRequest.origin, false, false)
                viewModel.pendingGeolocationRequest.value = null
            },
            icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            title = { Text("Dostęp do lokalizacji") },
            text = { Text("${geoRequest.origin} prosi o dostęp do Twojej lokalizacji.") },
            confirmButton = {
                TextButton(onClick = {
                    val alreadyGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (alreadyGranted) {
                        geoRequest.callback.invoke(geoRequest.origin, true, false)
                        viewModel.pendingGeolocationRequest.value = null
                    } else {
                        locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }) { Text("Zezwól") }
            },
            dismissButton = {
                TextButton(onClick = {
                    geoRequest.callback.invoke(geoRequest.origin, false, false)
                    viewModel.pendingGeolocationRequest.value = null
                }) { Text("Odmów") }
            }
        )
    }
    // NOWE: globalny dialog "Pobrano plik — otworzyć?" — pokazuje się na dowolnym ekranie (nie tylko
    // na liście Pobranych), bo pobieranie leci w tle i użytkownik mógł już przejść dalej po stronie.
    viewModel.justCompletedDownload.value?.let { record ->
        AlertDialog(
            onDismissRequest = { viewModel.justCompletedDownload.value = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Pobrano plik") },
            text = { Text("${record.fileName} — czy chcesz przejść do lokalizacji pliku?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.openDownloadedFile(record)
                    viewModel.justCompletedDownload.value = null
                }) { Text("Otwórz") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.justCompletedDownload.value = null }) { Text("Zamknij") }
            }
        )
    }
    BackHandler(enabled = activeTab?.canGoBack?.value == true && !isFullscreenVideo) {
        viewModel.goBack()
    }
    BackHandler(enabled = isFullscreenVideo) {
        activeTab?.let { viewModel.exitFullscreenVideo(it) }
    }
}

@Composable
private fun CompactIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = desc, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun TabCounterButton(count: Int, tint: Color = LocalContentColor.current, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}

@Composable
private fun AddressSecurityIcon(tab: BrowserTab?) {
    val url = tab?.url?.value.orEmpty()
    val hasWarning = tab?.securityWarning?.value != null
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    val tint: Color
    val description: String?
    when {
        hasWarning -> {
            icon = Icons.Default.Warning
            tint = MaterialTheme.colorScheme.error
            description = "Ostrzeżenie bezpieczeństwa"
        }
        url.startsWith("https://", ignoreCase = true) -> {
            icon = Icons.Default.Lock
            tint = MaterialTheme.colorScheme.primary
            description = "Połączenie zaszyfrowane"
        }
        url.startsWith("http://", ignoreCase = true) -> {
            icon = Icons.Default.LockOpen
            tint = MaterialTheme.colorScheme.error
            description = "Połączenie niezaszyfrowane"
        }
        else -> {
            icon = Icons.Default.Public
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            description = null
        }
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(16.dp)
    )
}

// NOWE: ekran nowej karty — kafelki najczęściej odwiedzanych stron (z historii) zamiast automatycznego
// ładowania strony domowej. Kliknięcie kafelka nawiguje i chowa ten ekran (patrz onPageStarted w createTab).
// POPRAWKA: cała zawartość jest teraz w Column ze verticalScroll — wcześniej przy większej liczbie kafelków
// (albo mniejszym ekranie) dolna część (ciekawostka dnia) była ucinana bez możliwości przewinięcia.
// Usunięto też Spacer(weight(1f)), którego celem było "przypięcie" ciekawostki do dołu — ten trik NIE działa
// wewnątrz kolumny z verticalScroll (Compose rzuca wyjątkiem, bo scrollowalna kolumna ma nieskończoną wysokość,
// więc weight nie ma się do czego odnieść) — zastąpiony zwykłym stałym odstępem.
@Composable
private fun StartPageContent(
    isPrivate: Boolean,
    frequentSites: List<FrequentSite>,
    gradientId: String,
    fact: String,
    onSiteClick: (String) -> Unit,
    onOpenPrivateTab: () -> Unit
) {
    // NOWE: gradient tła — dobierany po id presetu, z osobnym (ciemniejszym) zestawem kolorów dla trybu prywatnego.
    val preset = remember(gradientId) { gradientPresets.find { it.id == gradientId } ?: gradientPresets.first() }
    val gradientColors = if (isPrivate) preset.privateColors else preset.normalColors
    val hasGradient = gradientColors.isNotEmpty()
    // Gdy gradient jest aktywny, wymuszamy jasny tekst/kafelki niezależnie od jasnego/ciemnego motywu appki —
    // niestandardowe kolory gradientu nie zawsze pasują do domyślnych kolorów tekstu z Material Theme.
    val textColor = if (hasGradient) Color.White else if (isPrivate) Color.White else MaterialTheme.colorScheme.onSurface
    val subtleColor = if (hasGradient) Color.White.copy(alpha = 0.75f)
        else if (isPrivate) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
    val tileBackground = if (hasGradient) Color.White.copy(alpha = 0.18f)
        else if (isPrivate) Color(0xFF3A1750) else MaterialTheme.colorScheme.surfaceVariant
    val backgroundModifier = if (hasGradient) {
        Modifier.background(Brush.verticalGradient(gradientColors))
    } else {
        Modifier.background(if (isPrivate) Color(0xFF1A0B26) else MaterialTheme.colorScheme.background)
    }
    Column(
        Modifier
            .fillMaxSize()
            .then(backgroundModifier)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // NOWE: zamiast ikony globusa — nowoczesny, stylizowany napis z nazwą przeglądarki.
        // W trybie prywatnym dodatkowo mała ikonka oka-przekreślonego jako wizualny sygnał (jak wcześniej).
        Row(verticalAlignment = Alignment.Bottom) {
            if (isPrivate) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp).padding(bottom = 3.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "AiXE",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = if (hasGradient || isPrivate) Color.White else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "browser",
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                color = (if (hasGradient || isPrivate) Color.White else MaterialTheme.colorScheme.primary).copy(alpha = 0.75f)
            )
        }
        // NOWE: przycisk "Prywatne" wprost na ekranie startowym (jak w Operze) — tylko gdy NIE jesteśmy
        // już na karcie prywatnej (na karcie prywatnej nie ma sensu proponować przejścia w tryb, w którym
        // już się jest). Otwiera nową kartę prywatną, która sama pokaże ten sam ekran skrótów, tylko
        // z ciemniejszym wariantem gradientu (patrz logika gradientColors wyżej).
        if (!isPrivate) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (hasGradient) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onOpenPrivateTab)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = if (hasGradient) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Prywatne",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (hasGradient) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        // NOWE: duże pole wyszukiwania na ekranie startowym (jak w Operze/Chrome) — zamiast jedynego
        // sposobu wpisania czegoś przez mały pasek adresu w toolbarze na górze. Działa dokładnie tak samo
        // jak pasek adresu: albo adres URL, albo fraza do wyszukania (ta sama logika w viewModel.navigate).
        var searchText by remember { mutableStateOf("") }
        val keyboardController = LocalSoftwareKeyboardController.current
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(if (hasGradient) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = if (hasGradient) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (searchText.isEmpty()) {
                    Text(
                        "Wyszukaj lub wpisz adres",
                        fontSize = 15.sp,
                        color = if (hasGradient) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = if (hasGradient) Color.White else MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(if (hasGradient) Color.White else MaterialTheme.colorScheme.primary),
                    keyboardActions = KeyboardActions(onDone = {
                        if (searchText.isNotBlank()) {
                            onSiteClick(searchText)
                            keyboardController?.hide()
                        }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        if (frequentSites.isEmpty()) {
            Text(
                "Ostatnio odwiedzane strony pojawią się tutaj",
                style = MaterialTheme.typography.bodyMedium,
                color = subtleColor,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                "Ostatnio odwiedzane",
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(14.dp))
            frequentSites.chunked(4).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { site ->
                        FrequentSiteTile(
                            site = site,
                            background = tileBackground,
                            textColor = textColor,
                            onClick = { onSiteClick(site.url) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Dopełnienie ostatniego, niepełnego rzędu — żeby kafelki nie rozciągały się na całą szerokość.
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        // POPRAWKA: stały odstęp zamiast Spacer(weight(1f)) — patrz komentarz nad funkcją.
        Spacer(Modifier.height(24.dp))
        // "ciekawostka dnia" — ikona systemowa (żarówka) zamiast emoji.
        // Ta sama treść przez cały dzień (patrz factOfTheDay()), inna kolejnego dnia.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (hasGradient || isPrivate) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = null,
                tint = if (hasGradient || isPrivate) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = fact,
                style = MaterialTheme.typography.bodySmall,
                color = if (hasGradient || isPrivate) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FrequentSiteTile(
    site: FrequentSite,
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val host = remember(site.url) { runCatching { Uri.parse(site.url).host }.getOrNull() ?: site.url }
    // NOWE: dekodowanie prawdziwej favicony strony (jeśli została już zapisana — patrz onReceivedIcon
    // w BrowserViewModel.createTab). remember(site.faviconBase64) — dekodujemy tylko raz na wartość,
    // nie przy każdej rekompozycji. Brak favicony (jeszcze nie dotarła / błąd dekodowania) -> null,
    // wtedy niżej pokazujemy starą, zapasową "literę" zamiast pustego miejsca.
    val faviconBitmap = remember(site.faviconBase64) {
        site.faviconBase64?.let { encoded ->
            try {
                val bytes = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Column(
        modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            if (faviconBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = faviconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Text(
                    text = host.removePrefix("www.").take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = textColor
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = host.removePrefix("www."),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = textColor
        )
    }
}

@Composable
private fun WebViewContainer(webView: WebView, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { FrameLayout(it) },
        update = { frame ->
            if (frame.childCount == 0 || frame.getChildAt(0) !== webView) {
                frame.removeAllViews()
                (webView.parent as? ViewGroup)?.removeView(webView)
                frame.addView(
                    webView,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
            }
        }
    )
}

@Composable
private fun TabChip(tab: BrowserTab, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val chipColor = when {
        tab.isPrivate && selected -> Color(0xFF5B2A78)
        tab.isPrivate -> Color(0xFF2B1338)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val chipContentColor = if (tab.isPrivate) Color.White else LocalContentColor.current
    Surface(
        color = chipColor,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.height(36.dp).widthIn(min = 100.dp, max = 150.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onSelect)
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val favicon: Bitmap? = tab.favicon.value
                when {
                    tab.isPrivate -> Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = chipContentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    favicon != null -> androidx.compose.foundation.Image(
                        bitmap = favicon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    else -> Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(14.dp))
                }
                Text(
                    text = tab.title.value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = chipContentColor,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp).padding(end = 2.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Zamknij kartę",
                    tint = chipContentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: BrowserViewModel = viewModel(), onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val history by viewModel.historyFlow.collectAsState(initial = emptyList())
    // POPRAWKA: "Wyczyść" w pasku górnym czyściło całą historię BEZ potwierdzenia — jedno przypadkowe
    // kliknięcie i wszystko znika bezpowrotnie. Teraz, tak jak w Ustawieniach, wymaga potwierdzenia.
    var confirmingClearAll by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historia") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { confirmingClearAll = true }) { Text("Wyczyść") }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Brak historii przeglądania", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            // NOWE: panele-karty zamiast prostej listy — favicon strony (jak na ekranie nowej karty),
            // host + tytuł, godzina odwiedzin, i przycisk usuwania TEGO JEDNEGO wpisu (bez kasowania całości).
            LazyColumn(
                Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(history, key = { it.id }) { entry ->
                    HistoryEntryCard(
                        entry = entry,
                        onClick = { onNavigate(entry.url); onBack() },
                        onDelete = { viewModel.deleteHistoryEntry(entry.id) }
                    )
                }
            }
        }
    }
    if (confirmingClearAll) {
        AlertDialog(
            onDismissRequest = { confirmingClearAll = false },
            title = { Text("Wyczyścić całą historię?") },
            text = { Text("Tej operacji nie można cofnąć.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingClearAll = false
                    viewModel.clearHistory()
                }) { Text("Wyczyść", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearAll = false }) { Text("Anuluj") }
            }
        )
    }
}

// NOWE: pojedynczy panel historii — favicon (dekodowana z Base64, ta sama logika co na kafelkach
// ekranu nowej karty), host + tytuł strony, godzina wizyty, i osobny przycisk usuwania z krótką
// animacją znikania, żeby usunięcie jednego wpisu było wyraźnie widoczne, a nie nagłym skokiem listy.
@Composable
private fun HistoryEntryCard(entry: HistoryEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val formatter = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    val host = remember(entry.url) { runCatching { Uri.parse(entry.url).host }.getOrNull()?.removePrefix("www.") ?: entry.url }
    val faviconBitmap = remember(entry.faviconBase64) {
        entry.faviconBase64?.let { encoded ->
            try {
                val bytes = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (faviconBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = faviconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = host.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { host },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        host,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        " · ${formatter.format(Date(entry.visitedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            // POPRAWKA: przycisk usuwania TEGO wpisu — wcześniej jedyną opcją było wyczyszczenie
            // CAŁEJ historii naraz. Osobny IconButton, żeby kliknięcie w kafelek dalej otwierało
            // stronę, a usuwanie było wyraźnie oddzielną akcją (mniejszy hit-area, na krawędzi karty).
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Usuń z historii",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// NOWE: proste opcje sortowania współdzielone przez Zakładki i Pobrane pliki.
private enum class SimpleSortOption(val label: String) {
    NEWEST("Najnowsze"), OLDEST("Najstarsze"), NAME_ASC("Nazwa A-Z")
}

// NOWE: wspólny pasek "szukaj + sortuj" — używany zarówno na ekranie Zakładek, jak i Pobranych plików,
// żeby oba wyglądały i działały tak samo (styl dopasowany do zaokrąglonego pola z ekranu startowego/paska adresu).
@Composable
private fun <T> SearchAndSortRow(
    query: String,
    onQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    sortOptions: List<Pair<String, T>>,
    selectedSort: T,
    onSortSelected: (T) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        searchPlaceholder,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Wyczyść", modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        var sortMenuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(
                onClick = { sortMenuOpen = true },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Default.Sort, contentDescription = "Sortuj")
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                sortOptions.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = {
                            if (value == selectedSort) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            onSortSelected(value)
                            sortMenuOpen = false
                        }
                    )
                }
            }
        }
    }
}

// NOWE: prosty format rozmiaru pliku (B/KB/MB/GB) do wyświetlenia na karcie pobierania.
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIdx = 0
    while (value >= 1024 && unitIdx < units.lastIndex) {
        value /= 1024
        unitIdx++
    }
    return if (unitIdx == 0) "${value.toInt()} ${units[unitIdx]}" else String.format(Locale.getDefault(), "%.1f %s", value, units[unitIdx])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(viewModel: BrowserViewModel = viewModel(), onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val bookmarks by viewModel.bookmarksFlow.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SimpleSortOption.NEWEST) }
    // NOWE: filtrowanie po tytule/adresie i proste sortowanie — bookmarks to nowa lista przy każdej emisji
    // z Flow, więc remember z nią jako kluczem poprawnie przelicza się przy każdej zmianie danych.
    val filteredSorted = remember(bookmarks, query, sortOption) {
        val filtered = if (query.isBlank()) bookmarks
            else bookmarks.filter { it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) }
        when (sortOption) {
            SimpleSortOption.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SimpleSortOption.OLDEST -> filtered.sortedBy { it.createdAt }
            SimpleSortOption.NAME_ASC -> filtered.sortedBy { it.title.ifBlank { it.url }.lowercase() }
        }
    }
    val sortOptionsList = remember {
        SimpleSortOption.values().map { it.label to it }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zakładki") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (bookmarks.isNotEmpty()) {
                SearchAndSortRow(
                    query = query,
                    onQueryChange = { query = it },
                    searchPlaceholder = "Szukaj w zakładkach",
                    sortOptions = sortOptionsList,
                    selectedSort = sortOption,
                    onSortSelected = { sortOption = it }
                )
            }
            when {
                bookmarks.isEmpty() -> EmptyStateMessage(icon = Icons.Default.Star, message = "Brak zakładek")
                filteredSorted.isEmpty() -> EmptyStateMessage(icon = Icons.Default.SearchOff, message = "Brak wyników dla „$query”")
                else -> LazyColumn(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(filteredSorted, key = { it.id }) { entry ->
                        BookmarkCard(
                            entry = entry,
                            onClick = { onNavigate(entry.url); onBack() },
                            onDelete = { viewModel.removeBookmark(entry.url) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(entry: BookmarkEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val host = remember(entry.url) { runCatching { Uri.parse(entry.url).host }.getOrNull()?.removePrefix("www.") ?: entry.url }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF2B807), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { host },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    host,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Usuń zakładkę",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// NOWE: pusty stan (dla Zakładek i Pobranych) — ikona + komunikat, zamiast gołego tekstu na środku ekranu.
@Composable
private fun EmptyStateMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: BrowserViewModel = viewModel(), onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SimpleSortOption.NEWEST) }
    // POPRAWKA: viewModel.downloads to SnapshotStateList mutowana w miejscu (ta sama referencja przy każdej
    // zmianie) — owinięcie tego w remember(viewModel.downloads, ...) NIE odświeżałoby się poprawnie przy
    // dodaniu/zmianie pobrania, bo klucz "wygląda" tak samo. Dlatego liczymy filtrowanie/sortowanie wprost
    // przy każdej kompozycji (listy pobrań są małe, więc to tanie) — to jest bezpieczniejsze niż pozorne
    // przyspieszenie przez remember, które tu skutkowałoby "zamrożoną" listą.
    val filteredSorted = (if (query.isBlank()) viewModel.downloads.toList()
        else viewModel.downloads.filter { it.fileName.contains(query, ignoreCase = true) })
        .let { list ->
            when (sortOption) {
                SimpleSortOption.NEWEST -> list.sortedByDescending { it.startedAt }
                SimpleSortOption.OLDEST -> list.sortedBy { it.startedAt }
                SimpleSortOption.NAME_ASC -> list.sortedBy { it.fileName.lowercase() }
            }
        }
    val sortOptionsList = remember {
        SimpleSortOption.values().map { it.label to it }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pobrane pliki") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (viewModel.downloads.isNotEmpty()) {
                SearchAndSortRow(
                    query = query,
                    onQueryChange = { query = it },
                    searchPlaceholder = "Szukaj pobranych plików",
                    sortOptions = sortOptionsList,
                    selectedSort = sortOption,
                    onSortSelected = { sortOption = it }
                )
            }
            when {
                viewModel.downloads.isEmpty() -> EmptyStateMessage(icon = Icons.Default.Download, message = "Brak pobrań w tej sesji")
                filteredSorted.isEmpty() -> EmptyStateMessage(icon = Icons.Default.SearchOff, message = "Brak wyników dla „$query”")
                else -> LazyColumn(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(filteredSorted, key = { it.downloadManagerId }) { record ->
                        DownloadCard(record = record, onOpen = { viewModel.openDownloadedFile(record) })
                    }
                }
            }
        }
    }
}

// NOWE: karta pojedynczego pobrania — ikonka statusu (w trakcie / gotowe / błąd), pasek postępu tylko
// podczas pobierania, rozmiar pliku gdy znany, i przycisk otwierania pliku gdy pobieranie się skończyło.
@Composable
private fun DownloadCard(record: DownloadRecord, onOpen: () -> Unit) {
    val progress by record.progress
    val status by record.status
    val totalBytes by record.totalBytes
    val openUri by record.openUri
    val isDone = status == "Ukończono"
    val isFailed = status == "Przerwano" || status == "Nieznany błąd"
    val isActive = !isDone && !isFailed
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            isFailed -> MaterialTheme.colorScheme.errorContainer
                            isDone -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isFailed -> Icons.Default.ErrorOutline
                        isDone -> Icons.Default.InsertDriveFile
                        else -> Icons.Default.Download
                    },
                    contentDescription = null,
                    tint = when {
                        isFailed -> MaterialTheme.colorScheme.error
                        isDone -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                if (isActive) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    text = buildString {
                        append(status)
                        if (isActive) append(" · $progress%")
                        val sizeLabel = formatBytes(totalBytes)
                        if (sizeLabel.isNotEmpty()) append(" · $sizeLabel")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isDone && openUri != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Otwórz plik", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// POPRAWKA: Google jest teraz pierwszą (domyślną) pozycją na liście — zgodnie z prośbą, żeby domyślną
// wyszukiwarką był Google. Reszta listy zaktualizowana/uporządkowana wg popularności. "Własny adres"
// (wartość null) zawsze zostaje na końcu — jej wybranie odsłania pole tekstowe do wpisania czegokolwiek innego.
private val searchEnginePresets: List<Pair<String, String?>> = listOf(
    "Google" to "https://www.google.com/search?q={query}",
    "Bing" to "https://www.bing.com/search?q={query}",
    "DuckDuckGo" to "https://duckduckgo.com/?q={query}",
    "Brave Search" to "https://search.brave.com/search?q={query}",
    "Ecosia" to "https://www.ecosia.org/search?q={query}",
    "Startpage" to "https://www.startpage.com/sp/search?query={query}",
    "Własny adres" to null
)

// NOWE: generyczny combobox "wybierz z listy gotowych opcji". Etykieta wybranej pozycji jest wyliczana
// z aktualnej wartości (selectedValue) — jeśli nie pasuje do żadnego presetu, pokazuje "Własny adres",
// co pozwala wywołującemu (SettingsScreen) warunkowo odsłonić pole do ręcznego wpisania.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetDropdown(
    options: List<Pair<String, String?>>,
    selectedValue: String,
    onOptionSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.second == selectedValue }?.first
        ?: options.last().first // ostatnia pozycja to zawsze "Własny adres"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, value) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onOptionSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

// NOWE: prosty nagłówek sekcji w Ustawieniach — spójny styl dla wszystkich grup (Wygląd / Przeglądanie /
// Pobieranie / Prywatność), zamiast dotychczasowego jednego długiego ciągu bez wyraźnych podziałów.
@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: BrowserViewModel = viewModel(), onBack: () -> Unit) {
    val context = LocalContext.current
    val currentTheme by viewModel.theme.collectAsState(initial = AppTheme.SYSTEM)
    val currentSearchEngine by viewModel.searchEngineUrlFlow.collectAsState(initial = "https://www.google.com/search?q={query}")
    val adBlockEnabled by viewModel.adBlockEnabledFlow.collectAsState(initial = true)
    val downloadFolderUri by viewModel.downloadFolderUriFlow.collectAsState(initial = null)
    val newTabGradientId by viewModel.newTabGradientIdFlow.collectAsState(initial = "none")
    var searchEngineText by remember(currentSearchEngine) { mutableStateOf(currentSearchEngine) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setDownloadFolderUri(uri.toString())
        }
    }
    // POPRAWKA: zapisujemy wyszukiwarkę OD RAZU po wyborze presetu z listy (bez osobnego przycisku "Zapisz")
    // — dokładnie tak, jak działa to w prawdziwych przeglądarkach. Przycisk "Zapisz" zostaje tylko dla
    // przypadku "Własny adres", gdzie trzeba dokończyć wpisywanie tekstu, zanim zapis ma sens.
    fun saveSearchEngine(url: String) {
        val saved = url.ifBlank { "https://www.google.com/search?q={query}" }
        viewModel.setSearchEngineUrl(saved)
        searchEngineText = saved
        scope.launch { snackbarHostState.showSnackbar("Zapisano wyszukiwarkę") }
    }
    // POPRAWKA: cały ekran Ustawień przełożony z jednej długiej listy pól na pogrupowane karty (Wygląd,
    // Wyszukiwanie i przeglądanie, Pobieranie, Prywatność) — łatwiej się w tym połapać i wygląda mniej
    // chaotycznie niż wcześniejszy jeden ciągły blok bez podziałów.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSectionCard(title = "Wygląd", icon = Icons.Default.Palette) {
                Text("Motyw aplikacji", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOption("Systemowy", currentTheme == AppTheme.SYSTEM) { viewModel.setTheme(AppTheme.SYSTEM) }
                    ThemeOption("Jasny", currentTheme == AppTheme.LIGHT) { viewModel.setTheme(AppTheme.LIGHT) }
                    ThemeOption("Ciemny", currentTheme == AppTheme.DARK) { viewModel.setTheme(AppTheme.DARK) }
                }
                Spacer(Modifier.height(18.dp))
                Text("Tło nowej karty", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Gradient na ekranie skrótów nowej karty. Tryb prywatny używa ciemniejszego wariantu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    gradientPresets.forEach { preset ->
                        GradientSwatch(
                            preset = preset,
                            selected = preset.id == newTabGradientId,
                            onClick = { viewModel.setNewTabGradientId(preset.id) }
                        )
                    }
                }
            }

            SettingsSectionCard(title = "Wyszukiwanie i przeglądanie", icon = Icons.Default.Search) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Blokowanie reklam", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Podstawowe filtrowanie znanych domen reklamowych",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = adBlockEnabled, onCheckedChange = { viewModel.setAdBlockEnabled(it) })
                }
                Spacer(Modifier.height(18.dp))
                Text("Domyślna wyszukiwarka", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                PresetDropdown(
                    options = searchEnginePresets,
                    selectedValue = searchEngineText,
                    onOptionSelected = { picked ->
                        if (picked != null) {
                            // Preset ma gotowy adres -> zapisz od razu, bez czekania na przycisk.
                            saveSearchEngine(picked)
                        } else {
                            // "Własny adres" wybrany -> tylko odsłoń pole tekstowe, zapis dopiero na "Zapisz".
                            searchEngineText = ""
                        }
                    }
                )
                if (searchEnginePresets.none { it.second == searchEngineText }) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Użyj {query} w miejscu, gdzie ma trafić wpisana fraza.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = searchEngineText,
                        onValueChange = { searchEngineText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Własny adres wyszukiwarki") },
                        isError = searchEngineText.isBlank()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { saveSearchEngine(searchEngineText) },
                        enabled = searchEngineText.isNotBlank()
                    ) {
                        Text("Zapisz")
                    }
                }
            }

            SettingsSectionCard(title = "Pobieranie", icon = Icons.Default.Download) {
                Text(
                    if (downloadFolderUri != null) "Wybrany folder: ${friendlyFolderName(downloadFolderUri!!)}"
                    else "Domyślny — systemowy folder Pobrane",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Text("Wybierz folder")
                    }
                    if (downloadFolderUri != null) {
                        TextButton(onClick = { viewModel.setDownloadFolderUri(null) }) {
                            Text("Przywróć domyślny")
                        }
                    }
                }
            }

            SettingsSectionCard(title = "Prywatność i dane", icon = Icons.Default.PrivacyTip) {
                ClearDataRow(
                    title = "Historia",
                    description = "Lista odwiedzonych stron zapisana w tej aplikacji",
                    onClear = {
                        viewModel.clearHistory()
                        scope.launch { snackbarHostState.showSnackbar("Historia wyczyszczona") }
                    }
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ClearDataRow(
                    title = "Cookie i dane witryn",
                    description = "Wylogujesz się ze wszystkich odwiedzanych stron",
                    onClear = {
                        viewModel.clearCookiesAndSiteData()
                        scope.launch { snackbarHostState.showSnackbar("Cookie i dane witryn wyczyszczone") }
                    }
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ClearDataRow(
                    title = "Bufor (cache)",
                    description = "Strony mogą wczytywać się chwilę wolniej za pierwszym razem",
                    onClear = {
                        viewModel.clearBrowserCache()
                        scope.launch { snackbarHostState.showSnackbar("Bufor wyczyszczony") }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun friendlyFolderName(uriString: String): String {
    return try {
        val uri = Uri.parse(uriString)
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        docId.substringAfter(':', docId)
    } catch (e: Exception) {
        "wybrany folder"
    }
}

@Composable
private fun ClearDataRow(title: String, description: String, onClear: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { confirming = true }) { Text("Wyczyść") }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Wyczyścić: $title?") },
            text = { Text("Tej operacji nie można cofnąć.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onClear()
                }) { Text("Wyczyść", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Anuluj") }
            }
        )
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

// NOWE: kafelek-podgląd jednego presetu gradientu w Ustawieniach — kółko z gradientem (albo szare "Brak"),
// obwódka + ikona check gdy wybrany, etykieta pod spodem.
@Composable
private fun GradientSwatch(preset: GradientPreset, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .then(
                    if (preset.normalColors.isNotEmpty())
                        Modifier.background(Brush.linearGradient(preset.normalColors))
                    else
                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                )
                .then(
                    if (selected)
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (preset.normalColors.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            preset.label,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// NOWE: ekran pokazywany zamiast normalnej appki, jeśli poprzednie uruchomienie zakończyło się crashem —
// pozwala odczytać pełny stack trace bezpośrednio na telefonie (bez adb/kabla) i skopiować go do wklejenia.
@Composable
private fun CrashLogScreen(log: String, onContinue: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF1B1B1B)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                Text(
                    "Aplikacja zamknęła się nieoczekiwanie przy poprzednim uruchomieniu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Poniżej dokładna przyczyna (stack trace). Skopiuj i wklej, żeby to naprawić.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(log, fontSize = 11.sp, color = Color(0xFF7EE787))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { clipboardManager.setText(AnnotatedString(log)) }) {
                        Text("Kopiuj")
                    }
                    OutlinedButton(onClick = onContinue) {
                        Text("Kontynuuj do aplikacji")
                    }
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // NOWE: sprawdzamy, czy poprzednie uruchomienie zapisało crash log (patrz SuperBrowserApp.onCreate).
        // Jeśli tak — NIE dotykamy w ogóle BrowserViewModel/reszty appki, tylko pokazujemy sam log, żeby
        // ewentualny kolejny crash (np. w samym viewmodelu) nie nadpisał/nie zgubił tej informacji.
        val crashFile = getFileStreamPath("last_crash.txt")
        var crashLog by mutableStateOf<String?>(
            if (crashFile.exists()) crashFile.readText() else null
        )
        setContent {
            if (crashLog != null) {
                CrashLogScreen(
                    log = crashLog!!,
                    onContinue = {
                        crashFile.delete()
                        crashLog = null
                        recreate()
                    }
                )
            } else {
            val viewModel: BrowserViewModel = viewModel()
            val theme by viewModel.theme.collectAsState(initial = AppTheme.SYSTEM)
            SuperBrowserTheme(appTheme = theme) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "browser") {
                    composable("browser") {
                        BrowserScreen(
                            viewModel = viewModel,
                            onOpenHistory = { navController.navigate("history") },
                            onOpenBookmarks = { navController.navigate("bookmarks") },
                            onOpenDownloads = { navController.navigate("downloads") },
                            onOpenSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigate = { url -> viewModel.navigate(url) }
                        )
                    }
                    composable("bookmarks") {
                        BookmarksScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigate = { url -> viewModel.navigate(url) }
                        )
                    }
                    composable("downloads") {
                        DownloadsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                }
            }
            }
        }
    }
}
