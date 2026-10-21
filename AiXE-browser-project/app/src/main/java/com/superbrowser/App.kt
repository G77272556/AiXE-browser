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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
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
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val writer = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(writer))
                getFileStreamPath("last_crash.txt").writeText(writer.toString())
            } catch (e: Exception) {
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
    val faviconBase64: String? = null
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long,
    val folderId: Long? = null
)

@Entity(tableName = "bookmark_folders")
data class BookmarkFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

data class FrequentSite(
    val url: String,
    val title: String,
    val visitCount: Int,
    val faviconBase64: String? = null
)

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

private fun factOfTheDay(): String {
    val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    return dailyFacts[dayOfYear % dailyFacts.size]
}

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
    @Query("UPDATE history SET faviconBase64 = :faviconBase64 WHERE url = :url")
    suspend fun updateFavicon(url: String, faviconBase64: String)
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
    @Query("UPDATE bookmarks SET title = :title, folderId = :folderId WHERE id = :id")
    suspend fun update(id: Long, title: String, folderId: Long?)
    @Query("UPDATE bookmarks SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolderReference(folderId: Long)
}

@Dao
interface BookmarkFolderDao {
    @Insert
    suspend fun insert(folder: BookmarkFolderEntity): Long
    @Query("SELECT * FROM bookmark_folders ORDER BY name ASC")
    fun getAll(): Flow<List<BookmarkFolderEntity>>
    @Query("DELETE FROM bookmark_folders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [HistoryEntity::class, BookmarkEntity::class, BookmarkFolderEntity::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun bookmarkFolderDao(): BookmarkFolderDao
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
    private val adBlockProKey = booleanPreferencesKey("ad_block_pro_enabled")
    private val downloadFolderUriKey = stringPreferencesKey("download_folder_uri")
    private val newTabGradientKey = stringPreferencesKey("new_tab_gradient_id")
    private val pinnedSitesKey = stringPreferencesKey("pinned_sites")
    private val dataSaverKey = booleanPreferencesKey("data_saver_enabled")
    val theme: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { AppTheme.valueOf(it) } ?: AppTheme.SYSTEM
    }
    val searchEngineUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[searchEngineKey] ?: "https://www.google.com/search?q={query}"
    }
    val adBlockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[adBlockKey] ?: true
    }
    val adBlockProEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[adBlockProKey] ?: true
    }
    val downloadFolderUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[downloadFolderUriKey]
    }
    val newTabGradientId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[newTabGradientKey] ?: "none"
    }
    val pinnedSites: Flow<List<PinnedSite>> = context.dataStore.data.map { prefs ->
        prefs[pinnedSitesKey]?.let { parsePinnedSites(it) } ?: emptyList()
    }
    val dataSaverEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[dataSaverKey] ?: false
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
    suspend fun setAdBlockProEnabled(value: Boolean) {
        context.dataStore.edit { it[adBlockProKey] = value }
    }
    suspend fun setDownloadFolderUri(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(downloadFolderUriKey) else prefs[downloadFolderUriKey] = value
        }
    }
    suspend fun setNewTabGradientId(value: String) {
        context.dataStore.edit { it[newTabGradientKey] = value }
    }
    suspend fun setPinnedSites(sites: List<PinnedSite>) {
        context.dataStore.edit { it[pinnedSitesKey] = serializePinnedSites(sites) }
    }
    suspend fun setDataSaverEnabled(value: Boolean) {
        context.dataStore.edit { it[dataSaverKey] = value }
    }
}

data class PinnedSite(val url: String, val title: String)

private fun serializePinnedSites(sites: List<PinnedSite>): String {
    val arr = org.json.JSONArray()
    sites.forEach { site ->
        val obj = org.json.JSONObject()
        obj.put("url", site.url)
        obj.put("title", site.title)
        arr.put(obj)
    }
    return arr.toString()
}

private fun parsePinnedSites(raw: String): List<PinnedSite> {
    return try {
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            PinnedSite(url = obj.getString("url"), title = obj.optString("title", obj.getString("url")))
        }
    } catch (e: Exception) {
        emptyList()
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
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        val isDebugBuild = (webView.context.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(isDebugBuild)
    }

    fun applyPrivateModeSettings(webView: WebView) {
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
    }
}

object AdBlockRuleParser {

    data class ParsedRules(
        val exactDomains: Set<String>,
        val wildcardPatterns: List<Regex>,
        val exceptions: Set<String>,
        val cosmeticSelectors: Set<String>
    )

    fun parse(lines: Sequence<String>): ParsedRules {
        val exact = mutableSetOf<String>()
        val wildcards = mutableListOf<Regex>()
        val exceptions = mutableSetOf<String>()
        val cosmetic = mutableSetOf<String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("!")) continue
            when {
                line.contains("##") -> {
                    val selector = line.substringAfter("##").trim()
                    if (selector.isNotBlank()) cosmetic.add(selector)
                }
                line.contains("#@#") -> {
                }
                line.startsWith("@@") -> {
                    val body = line.removePrefix("@@")
                    if (body.startsWith("||")) {
                        var domain = body.removePrefix("||")
                        domain = domain.substringBefore('^').substringBefore('/')
                        if (domain.isNotBlank()) exceptions.add(domain.lowercase())
                    }
                }
                line.startsWith("||") -> {
                    var domain = line.removePrefix("||")
                    val caretIdx = domain.indexOf('^')
                    if (caretIdx >= 0) domain = domain.substring(0, caretIdx)
                    val slashIdx = domain.indexOf('/')
                    if (slashIdx >= 0) domain = domain.substring(0, slashIdx)
                    if (domain.isBlank()) continue
                    if (domain.contains("*")) {
                        val escaped = Regex.escape(domain.lowercase()).replace("\\*", ".*")
                        wildcards.add(Regex("^$escaped$"))
                    } else {
                        exact.add(domain.lowercase())
                    }
                }
                else -> continue
            }
        }
        return ParsedRules(exact, wildcards, exceptions, cosmetic)
    }

    fun loadFromAssets(context: Context, fileName: String = "adblock_list.txt"): ParsedRules {
        return try {
            context.assets.open(fileName).bufferedReader().useLines { lines -> parse(lines) }
        } catch (e: Exception) {
            ParsedRules(emptySet(), emptyList(), emptySet(), emptySet())
        }
    }
}

object ExternalUriHandler {

    private val externalSchemes = setOf("mailto", "sms", "tel")

    fun tryHandle(context: Context, url: String): Boolean {
        val scheme = runCatching { Uri.parse(url).scheme }.getOrNull()?.lowercase() ?: return false
        if (scheme !in externalSchemes) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
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
    var isNewTabPage = mutableStateOf(false)
    var lastNavigationUrl: String? = null
    var adsBlockedCount = mutableStateOf(0)
    var isToolbarVisible = mutableStateOf(true)
    var blockedPopupUrl = mutableStateOf<String?>(null)
}

class DownloadRecord(
    val downloadManagerId: Long,
    val fileName: String,
    val mimeType: String?,
    val startedAt: Long = System.currentTimeMillis()
) {
    var progress = mutableStateOf(0)
    var status = mutableStateOf("Pobieranie...")
    var totalBytes = mutableStateOf(0L)
    var openUri = mutableStateOf<Uri?>(null)
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<SuperBrowserApp>()
    private val historyDao = app.database.historyDao()
    private val bookmarkDao = app.database.bookmarkDao()
    private val bookmarkFolderDao = app.database.bookmarkFolderDao()
    private val settings = app.settings
    val tabs = mutableStateListOf<BrowserTab>()
    val activeTabId = mutableStateOf<String?>(null)
    val downloads = mutableStateListOf<DownloadRecord>()
    var pendingFileChooserCallback: ValueCallback<Array<Uri>>? = null
    val fileChooserIntent = mutableStateOf<Intent?>(null)
    val pendingWebPermissionRequest = mutableStateOf<PermissionRequest?>(null)
    val pendingGeolocationRequest = mutableStateOf<GeolocationRequestInfo?>(null)
    val justCompletedDownload = mutableStateOf<DownloadRecord?>(null)
    val requestNotificationPermission = mutableStateOf(false)
    val theme: Flow<AppTheme> = settings.theme
    val searchEngineUrlFlow: Flow<String> = settings.searchEngineUrl
    val adBlockEnabledFlow: Flow<Boolean> = settings.adBlockEnabled
    val adBlockProEnabledFlow: Flow<Boolean> = settings.adBlockProEnabled
    val dataSaverEnabledFlow: Flow<Boolean> = settings.dataSaverEnabled
    val downloadFolderUriFlow: Flow<String?> = settings.downloadFolderUri
    val newTabGradientIdFlow: Flow<String> = settings.newTabGradientId
    val historyFlow: Flow<List<HistoryEntity>> = historyDao.getAll()
    val bookmarksFlow: Flow<List<BookmarkEntity>> = bookmarkDao.getAll()
    val bookmarkFoldersFlow: Flow<List<BookmarkFolderEntity>> = bookmarkFolderDao.getAll()
    val pinnedSitesFlow: Flow<List<PinnedSite>> = settings.pinnedSites
    val frequentSitesFlow: Flow<List<FrequentSite>> = historyDao.getMostVisited()
    val dailyFact = factOfTheDay()
    @Volatile private var adBlockEnabledCache = true
    @Volatile private var adBlockProEnabledCache = true
    @Volatile private var dataSaverEnabledCache = false
    init {
        viewModelScope.launch { adBlockEnabledFlow.collect { adBlockEnabledCache = it } }
        viewModelScope.launch { adBlockProEnabledFlow.collect { adBlockProEnabledCache = it } }
        viewModelScope.launch { dataSaverEnabledFlow.collect { dataSaverEnabledCache = it } }
        viewModelScope.launch {
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                AdBlockRuleParser.loadFromAssets(app)
            }
            adBlockRules.value = if (loaded.exactDomains.isEmpty() && loaded.wildcardPatterns.isEmpty()) {
                AdBlockRuleParser.ParsedRules(fallbackAdBlockDomains, emptyList(), emptySet(), fallbackCosmeticSelectors)
            } else {
                loaded
            }
        }
    }
    private val closedTabsStack = ArrayDeque<String>()
    fun activeTab(): BrowserTab? = tabs.find { it.id == activeTabId.value }

    private fun applyDataSaverToSettings(settings: WebSettings, enabled: Boolean) {
        settings.cacheMode = if (enabled) WebSettings.LOAD_CACHE_ELSE_NETWORK else WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = enabled
    }

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
            applyDataSaverToSettings(settings, dataSaverEnabledCache)
        }
        WebViewHardening.applyHardenedSettings(webView)
        if (isPrivate) WebViewHardening.applyPrivateModeSettings(webView)
        val tab = BrowserTab(webView = webView, isPrivate = isPrivate)
        tab.isNewTabPage.value = showStartPage
        tabs.add(tab)
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            when {
                scrollY <= 0 -> tab.isToolbarVisible.value = true
                dy > 12 -> tab.isToolbarVisible.value = false
                dy < -12 -> tab.isToolbarVisible.value = true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) {
                tab.isLoading.value = true
                tab.url.value = u
                tab.securityWarning.value = null
                tab.pageError.value = null
                tab.isNewTabPage.value = false
                tab.adsBlockedCount.value = 0
                tab.isToolbarVisible.value = true
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url.toString()
                if (ExternalUriHandler.tryHandle(view.context, target)) {
                    return true
                }
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
                tab.lastNavigationUrl = target
                return false
            }
            override fun onPageFinished(view: WebView, u: String) {
                tab.isLoading.value = false
                tab.url.value = u
                tab.canGoBack.value = view.canGoBack()
                tab.canGoForward.value = view.canGoForward()
                refreshBookmarkState(tab)
                if (adBlockEnabledCache && adBlockProEnabledCache) {
                    injectCosmeticHiding(view)
                }
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
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) { tab.adsBlockedCount.value++ }
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
                if (!isUserGesture) {
                    val probeWebView = WebView(app)
                    probeWebView.webViewClient = object : WebViewClient() {
                        override fun onPageStarted(pv: WebView, u: String, favicon: Bitmap?) {
                            pv.stopLoading()
                            tab.blockedPopupUrl.value = u
                            probeWebView.destroy()
                        }
                    }
                    val transport = resultMsg.obj as WebView.WebViewTransport
                    transport.webView = probeWebView
                    resultMsg.sendToTarget()
                    return true
                }
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
            tab.isNewTabPage.value = false
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
    fun openBlockedPopup(tab: BrowserTab) {
        val url = tab.blockedPopupUrl.value ?: return
        tab.blockedPopupUrl.value = null
        createTab(url, select = true, isPrivate = tab.isPrivate)
    }
    fun dismissBlockedPopup(tab: BrowserTab) {
        tab.blockedPopupUrl.value = null
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
    fun restoreBookmark(entry: BookmarkEntity) {
        viewModelScope.launch { bookmarkDao.insert(entry) }
    }
    fun updateBookmark(id: Long, title: String, folderId: Long?) {
        viewModelScope.launch { bookmarkDao.update(id, title.ifBlank { "Bez nazwy" }, folderId) }
    }
    fun createBookmarkFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            bookmarkFolderDao.insert(BookmarkFolderEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
        }
    }
    fun deleteBookmarkFolder(id: Long) {
        viewModelScope.launch {
            bookmarkDao.clearFolderReference(id)
            bookmarkFolderDao.delete(id)
        }
    }
    fun togglePinnedSite(url: String, title: String) {
        viewModelScope.launch {
            val current = settings.pinnedSites.first()
            val updated = if (current.any { it.url == url }) {
                current.filterNot { it.url == url }
            } else {
                current + PinnedSite(url, title.ifBlank { url })
            }
            settings.setPinnedSites(updated)
        }
    }
    fun clearHistory() {
        viewModelScope.launch { historyDao.clear() }
    }
    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch { historyDao.deleteById(id) }
    }
    fun restoreHistoryEntry(entry: HistoryEntity) {
        viewModelScope.launch { historyDao.insert(entry) }
    }
    fun restoreHistoryEntries(entries: List<HistoryEntity>) {
        viewModelScope.launch { entries.forEach { historyDao.insert(it) } }
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
    fun setAdBlockEnabled(value: Boolean) = viewModelScope.launch { settings.setAdBlockEnabled(value) }
    fun setAdBlockProEnabled(value: Boolean) = viewModelScope.launch { settings.setAdBlockProEnabled(value) }
    fun setDownloadFolderUri(value: String?) = viewModelScope.launch { settings.setDownloadFolderUri(value) }
    fun setNewTabGradientId(value: String) = viewModelScope.launch { settings.setNewTabGradientId(value) }
    fun setDataSaverEnabled(value: Boolean) = viewModelScope.launch {
        settings.setDataSaverEnabled(value)
        tabs.forEach { t ->
            applyDataSaverToSettings(t.webView.settings, value)
            t.webView.reload()
        }
    }
    private val adBlockRules = mutableStateOf(
        AdBlockRuleParser.ParsedRules(emptySet(), emptyList(), emptySet(), emptySet())
    )
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
        "popads.net", "propellerads.com", "adcolony.com",
        "googletagmanager.com", "googletagservices.com", "google-analytics.com",
        "facebook.net", "connect.facebook.net", "adjust.com", "appsflyer.com",
        "branch.io", "onesignal.com", "hotjar.com", "mouseflow.com",
        "yandex.ru/metrika", "mathtag.com", "adroll.com", "bluekai.com",
        "krxd.net", "turn.com", "exponea.com", "chartbeat.com"
    )
    private val fallbackCosmeticSelectors = setOf(
        "[id*=\"google_ads\"]", "[class*=\"adsbygoogle\"]", "[id*=\"div-gpt-ad\"]",
        "[class*=\"ad-banner\"]", "[class*=\"ad-container\"]", "[class*=\"advertisement\"]"
    )
    private fun isAdHost(host: String?): Boolean {
        if (host == null) return false
        val rules = adBlockRules.value
        val isException = rules.exceptions.any { host == it || host.endsWith(".$it") }
        if (isException) return false
        val exactMatch = rules.exactDomains.any { host == it || host.endsWith(".$it") }
        if (exactMatch) return true
        if (!adBlockProEnabledCache) return false
        return rules.wildcardPatterns.any { it.matches(host) }
    }
    private fun injectCosmeticHiding(view: WebView) {
        val selectors = adBlockRules.value.cosmeticSelectors
        if (selectors.isEmpty()) return
        val css = selectors.joinToString(",") { it.replace("\\", "\\\\").replace("`", "") } + "{display:none!important;}"
        val js = """
            (function() {
                try {
                    var s = document.createElement('style');
                    s.setAttribute('data-aixe-adblock', '1');
                    s.textContent = `$css`;
                    document.documentElement.appendChild(s);
                } catch (e) {}
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
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
                                    record.openUri.value = try {
                                        manager.getUriForDownloadedFile(record.downloadManagerId)
                                    } catch (e: Exception) {
                                        localUriString?.let { s -> runCatching { Uri.parse(s) }.getOrNull() }
                                    }
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
    val frequentSites by viewModel.frequentSitesFlow.collectAsState(initial = emptyList())
    val newTabGradientId by viewModel.newTabGradientIdFlow.collectAsState(initial = "none")
    val pinnedSites by viewModel.pinnedSitesFlow.collectAsState(initial = emptyList())
    val dataSaverEnabled by viewModel.dataSaverEnabledFlow.collectAsState(initial = false)
    val appTheme by viewModel.theme.collectAsState(initial = AppTheme.SYSTEM)
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (appTheme) {
        AppTheme.SYSTEM -> isSystemDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val onTogglePin: (String, String) -> Unit = { url, title ->
        val wasPinned = pinnedSites.any { it.url == url }
        viewModel.togglePinnedSite(url, title)
        scope.launch {
            snackbarHostState.showSnackbar(
                if (wasPinned) "Odpięto stronę" else "Przypięto stronę",
                duration = SnackbarDuration.Short
            )
        }
    }
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
        AnimatedVisibility(
            visible = activeTab?.isToolbarVisible?.value != false,
            enter = slideInVertically(tween(200)) { -it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(150))
        ) {
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
                    // 1. Zmiana wysokości paska adresu na 48.dp
                    Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp, vertical = 5.dp),
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
                            // 1. Zmiana RoundedCornerShape na 24.dp
                            .clip(RoundedCornerShape(24.dp))
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
                    CompactIcon(
                        icon = Icons.Default.MoreVert,
                        desc = "Menu",
                        tint = if (isActivePrivate) Color.White else LocalContentColor.current,
                        onClick = { menuOpen = true }
                    )
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
                val loadingPreset = remember(newTabGradientId) {
                    gradientPresets.find { it.id == newTabGradientId } ?: gradientPresets.first()
                }
                val loadingGradientColors = if (isActivePrivate) loadingPreset.privateColors else loadingPreset.normalColors
                val loadingBarBrush = if (loadingGradientColors.isNotEmpty()) {
                    Brush.horizontalGradient(loadingGradientColors)
                } else {
                    val solidColor = if (isDarkTheme) Color.White else Color.Black
                    Brush.horizontalGradient(listOf(solidColor, solidColor))
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progress)
                            .background(loadingBarBrush)
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
            activeTab?.blockedPopupUrl?.value?.let {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Strona chciała otworzyć wyskakujące okienko",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.openBlockedPopup(activeTab) }) { Text("Otwórz") }
                        IconButton(
                            onClick = { viewModel.dismissBlockedPopup(activeTab) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Zamknij", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            }
        }
        }
        Box(Modifier.fillMaxSize().weight(1f)) {
            activeTab?.let { tab ->
              if (tab.isNewTabPage.value) {
                StartPageContent(
                    isPrivate = tab.isPrivate,
                    isDarkTheme = isDarkTheme,
                    frequentSites = frequentSites,
                    pinnedSites = pinnedSites,
                    gradientId = newTabGradientId,
                    fact = viewModel.dailyFact,
                    onSiteClick = { clickedUrl ->
                        addressText = clickedUrl
                        viewModel.navigate(clickedUrl)
                    },
                    onOpenPrivateTab = {
                        viewModel.createTab("", select = true, isPrivate = true, showStartPage = true)
                    },
                    onTogglePin = onTogglePin
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
                            onClose = {
                                val canUndo = tab.url.value.isNotBlank() && !tab.isPrivate
                                viewModel.closeTab(tab.id)
                                if (canUndo) {
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Zamknięto kartę",
                                            actionLabel = "Cofnij",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) viewModel.restoreLastClosedTab()
                                    }
                                }
                            }
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
        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { menuOpen = false }
            )
        }
        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn(tween(180)) + expandVertically(tween(220), expandFrom = Alignment.Top),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(180), shrinkTowards = Alignment.Top),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
        ) {
            val currentUrl = activeTab?.url?.value.orEmpty()
            val isCurrentPinned = currentUrl.isNotBlank() && pinnedSites.any { it.url == currentUrl }
            TopSlideMenu(
                isDesktopMode = activeTab?.isDesktopMode?.value == true,
                isCurrentPinned = isCurrentPinned,
                hasCurrentUrl = currentUrl.isNotBlank(),
                tabCount = viewModel.tabs.size,
                blockedAdsCount = activeTab?.adsBlockedCount?.value ?: 0,
                dataSaverEnabled = dataSaverEnabled,
                onShare = {
                    if (currentUrl.isNotBlank()) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Udostępnij przez"))
                    }
                },
                onCopyLink = {
                    if (currentUrl.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(currentUrl))
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                            scope.launch { snackbarHostState.showSnackbar("Skopiowano link") }
                        }
                    }
                },
                onFind = { activeTab?.let { viewModel.startFindInPage(it) } },
                onToggleDesktop = { activeTab?.let { viewModel.toggleDesktopMode(it) } },
                onTogglePinClick = { onTogglePin(currentUrl, activeTab?.title?.value ?: currentUrl) },
                onTranslate = { viewModel.translatePage() },
                onNewPrivateTab = { viewModel.createTab("", select = true, isPrivate = true, showStartPage = true) },
                onRestoreClosedTab = { viewModel.restoreLastClosedTab() },
                onToggleDataSaver = { viewModel.setDataSaverEnabled(!dataSaverEnabled) },
                onShowTabs = {
                    scope.launch {
                        val idx = viewModel.tabs.indexOfFirst { it.id == viewModel.activeTabId.value }
                        if (idx >= 0) tabListState.animateScrollToItem(idx)
                    }
                },
                onOpenHistory = onOpenHistory,
                onOpenBookmarks = onOpenBookmarks,
                onOpenDownloads = onOpenDownloads,
                onOpenSettings = onOpenSettings,
                onDismiss = { menuOpen = false }
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
private fun TopSlideMenu(
    isDesktopMode: Boolean,
    isCurrentPinned: Boolean,
    hasCurrentUrl: Boolean,
    tabCount: Int,
    blockedAdsCount: Int,
    dataSaverEnabled: Boolean,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onFind: () -> Unit,
    onToggleDesktop: () -> Unit,
    onTogglePinClick: () -> Unit,
    onTranslate: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onRestoreClosedTab: () -> Unit,
    onToggleDataSaver: () -> Unit,
    onShowTabs: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    fun act(action: () -> Unit): () -> Unit = { action(); onDismiss() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(top = 6.dp),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                QuickActionButton(Icons.Default.Share, "Udostępnij", onClick = act(onShare))
                QuickActionButton(Icons.Default.ContentCopy, "Kopiuj link", onClick = act(onCopyLink))
                QuickActionButton(Icons.Default.Search, "Znajdź", onClick = act(onFind))
                if (hasCurrentUrl) {
                    QuickActionButton(
                        icon = if (isCurrentPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        label = if (isCurrentPinned) "Odepnij" else "Przypnij",
                        onClick = act(onTogglePinClick)
                    )
                }
                QuickActionButton(
                    icon = Icons.Default.Computer,
                    label = if (isDesktopMode) "Mobilna" else "Komputer",
                    onClick = act(onToggleDesktop)
                )
                QuickActionButton(
                    icon = if (dataSaverEnabled) Icons.Default.DataSaverOn else Icons.Default.DataSaverOff,
                    label = if (dataSaverEnabled) "Oszcz. wł." else "Oszczędzanie",
                    onClick = act(onToggleDataSaver)
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            MenuRow(Icons.Default.Tab, "Karty ($tabCount)", onClick = act(onShowTabs))
            if (blockedAdsCount > 0) {
                MenuRow(Icons.Default.Shield, "Zablokowane reklamy: $blockedAdsCount", onClick = onDismiss)
            }
            MenuRow(Icons.Default.Translate, "Przetłumacz stronę", onClick = act(onTranslate))
            MenuRow(Icons.Default.VisibilityOff, "Nowa karta prywatna", onClick = act(onNewPrivateTab))
            MenuRow(Icons.Default.Restore, "Przywróć zamkniętą kartę", onClick = act(onRestoreClosedTab))
            HorizontalDivider()
            MenuRow(Icons.Default.History, "Historia", onClick = act(onOpenHistory))
            MenuRow(Icons.Default.Star, "Zakładki", onClick = act(onOpenBookmarks))
            MenuRow(Icons.Default.Download, "Pobrane", onClick = act(onOpenDownloads))
            HorizontalDivider()
            MenuRow(Icons.Default.Settings, "Ustawienia", onClick = act(onOpenSettings))
        }
    }
}

@Composable
private fun QuickActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun QuickShortcutChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    hasGradient: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (hasGradient) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (hasGradient) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (hasGradient) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StartPageContent(
    isPrivate: Boolean,
    isDarkTheme: Boolean,
    frequentSites: List<FrequentSite>,
    pinnedSites: List<PinnedSite>,
    gradientId: String,
    fact: String,
    onSiteClick: (String) -> Unit,
    onOpenPrivateTab: () -> Unit,
    onTogglePin: (url: String, title: String) -> Unit
) {
    val preset = remember(gradientId) { gradientPresets.find { it.id == gradientId } ?: gradientPresets.first() }
    val gradientColors = if (isPrivate) preset.privateColors else preset.normalColors
    val hasGradient = gradientColors.isNotEmpty()
    val textColor = if (hasGradient) Color.White else if (isPrivate) Color.White else MaterialTheme.colorScheme.onSurface
    val subtleColor = if (hasGradient) Color.White.copy(alpha = 0.75f)
        else if (isPrivate) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
    val tileBackground = if (hasGradient) Color.White.copy(alpha = 0.18f)
        else if (isPrivate) Color(0xFF3A1750) else MaterialTheme.colorScheme.surfaceVariant
    val darkNoGradientBackground = Color(0xFF0B1530)
    val backgroundModifier = when {
        hasGradient -> Modifier.background(Brush.verticalGradient(gradientColors))
        isPrivate -> Modifier.background(Color(0xFF1A0B26))
        isDarkTheme -> Modifier.background(darkNoGradientBackground)
        else -> Modifier.background(MaterialTheme.colorScheme.background)
    }
    Column(
        Modifier
            .fillMaxSize()
            .then(backgroundModifier)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 4. Zmiana rozmiarów Logo na stronie startowej
        Row(verticalAlignment = Alignment.Bottom) {
            if (isPrivate) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp).padding(bottom = 3.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "AiXE",
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = if (hasGradient || isPrivate) Color.White else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "browser",
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                color = (if (hasGradient || isPrivate) Color.White else MaterialTheme.colorScheme.primary).copy(alpha = 0.75f)
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isPrivate) {
                QuickShortcutChip(
                    icon = Icons.Default.VisibilityOff,
                    label = "Prywatne",
                    hasGradient = hasGradient,
                    onClick = onOpenPrivateTab
                )
            }
            QuickShortcutChip(
                icon = Icons.Default.Newspaper,
                label = "Wiadomości",
                hasGradient = hasGradient,
                onClick = { onSiteClick("https://news.google.com/?hl=pl&gl=PL&ceid=PL:pl") }
            )
        }
        Spacer(Modifier.height(28.dp))
        var searchText by remember { mutableStateOf("") }
        val keyboardController = LocalSoftwareKeyboardController.current
        fun submitSearch() {
            if (searchText.isNotBlank()) {
                onSiteClick(searchText)
                keyboardController?.hide()
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(if (hasGradient) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { submitSearch() }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Szukaj",
                    tint = if (hasGradient) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(2.dp))
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
                    keyboardActions = KeyboardActions(onDone = { submitSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (searchText.isNotEmpty()) {
                IconButton(onClick = { searchText = "" }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Wyczyść",
                        tint = if (hasGradient) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        
        // 6. Sekcja przypiętych stron
        val pinnedAsTiles = remember(pinnedSites, frequentSites) {
            pinnedSites.map { pinned ->
                FrequentSite(
                    url = pinned.url,
                    title = pinned.title,
                    visitCount = 0,
                    faviconBase64 = frequentSites.find { it.url == pinned.url }?.faviconBase64
                )
            }
        }
        if (pinnedAsTiles.isNotEmpty()) {
            Text(
                "Przypięte",
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(14.dp))
            TileGrid(
                sites = pinnedAsTiles,
                isPinnedSection = true,
                background = tileBackground,
                textColor = textColor,
                onClick = onSiteClick,
                onLongClick = { url, title -> onTogglePin(url, title) }
            )
            Spacer(Modifier.height(14.dp))
        }

        if (frequentSites.isEmpty()) {
            if (pinnedAsTiles.isEmpty()) {
                Text(
                    "Ostatnio odwiedzane strony pojawią się tutaj",
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtleColor,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                "Ostatnio odwiedzane",
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(14.dp))
            TileGrid(
                sites = frequentSites,
                isPinnedSection = false,
                background = tileBackground,
                textColor = textColor,
                onClick = onSiteClick,
                onLongClick = { url, title -> onTogglePin(url, title) }
            )
        }
        Spacer(Modifier.height(24.dp))
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
private fun TileGrid(
    sites: List<FrequentSite>,
    isPinnedSection: Boolean,
    background: Color,
    textColor: Color,
    onClick: (String) -> Unit,
    onLongClick: (url: String, title: String) -> Unit
) {
    sites.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { site ->
                FrequentSiteTile(
                    site = site,
                    background = background,
                    textColor = textColor,
                    isPinned = isPinnedSection,
                    onClick = { onClick(site.url) },
                    onLongClick = { onLongClick(site.url, site.title) },
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrequentSiteTile(
    site: FrequentSite,
    background: Color,
    textColor: Color,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val host = remember(site.url) { runCatching { Uri.parse(site.url).host }.getOrNull() ?: site.url }
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                // 3. Zmiana rozmiarów kafelek na 72.dp i RoundedCornerShape na 20.dp
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            // 5. Obsługa favicon zamiast liter
            if (site.faviconBase64 != null) {
                val bitmap = remember(site.faviconBase64) {
                    try {
                        val bytes = android.util.Base64.decode(
                            site.faviconBase64,
                            android.util.Base64.DEFAULT
                        )
                        android.graphics.BitmapFactory
                            .decodeByteArray(
                                bytes,
                                0,
                                bytes.size
                            )
                    } catch (e: Exception) {
                        null
                    }
                }
                bitmap?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                Text(
                    host.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = textColor
                )
            }
            if (isPinned) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Przypięte",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
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
        // 2. Zmiana RoundedCornerShape na 18.dp
        shape = RoundedCornerShape(18.dp),
        // 2. Zmiana Modifier.height na 44.dp oraz widthIn na min = 130.dp, max = 200.dp
        modifier = Modifier.height(44.dp).widthIn(min = 130.dp, max = 200.dp)
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmingClearAll by remember { mutableStateOf(false) }
    val sections = remember(history) {
        history.groupBy { dayBucketLabel(it.visitedAt) }
            .toList()
            .sortedBy { (label, _) -> when (label) { "Dziś" -> 0; "Wczoraj" -> 1; else -> 2 } }
    }
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            LazyColumn(
                Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                sections.forEach { (label, entries) ->
                    item(key = "header_$label") {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                    items(entries, key = { it.id }) { entry ->
                        HistoryEntryCard(
                            entry = entry,
                            onClick = { onNavigate(entry.url); onBack() },
                            onDelete = {
                                viewModel.deleteHistoryEntry(entry.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Usunięto z historii",
                                        actionLabel = "Cofnij",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreHistoryEntry(entry)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    if (confirmingClearAll) {
        AlertDialog(
            onDismissRequest = { confirmingClearAll = false },
            title = { Text("Wyczyścić całą historię?") },
            text = { Text("Będzie można to cofnąć przez chwilę po wyczyszczeniu.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingClearAll = false
                    val backup = history
                    viewModel.clearHistory()
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Wyczyszczono historię",
                            actionLabel = "Cofnij",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restoreHistoryEntries(backup)
                    }
                }) { Text("Wyczyść", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearAll = false }) { Text("Anuluj") }
            }
        )
    }
}

private fun dayBucketLabel(timestamp: Long): String {
    fun startOfDay(millis: Long): Long {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = millis
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
    val diffDays = (startOfDay(System.currentTimeMillis()) - startOfDay(timestamp)) / (24 * 60 * 60 * 1000L)
    return when (diffDays) {
        0L -> "Dziś"
        1L -> "Wczoraj"
        else -> "Wcześniej"
    }
}

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

private enum class SimpleSortOption(val label: String) {
    NEWEST("Najnowsze"), OLDEST("Najstarsze"), NAME_ASC("Nazwa A-Z")
}

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
    val folders by viewModel.bookmarkFoldersFlow.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SimpleSortOption.NEWEST) }
    var selectedFolder by remember { mutableStateOf<FolderFilter>(FolderFilter.All) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(folders) {
        val f = selectedFolder
        if (f is FolderFilter.Specific && folders.none { it.id == f.id }) selectedFolder = FolderFilter.All
    }
    val folderFiltered = remember(bookmarks, selectedFolder) {
        when (val f = selectedFolder) {
            FolderFilter.All -> bookmarks
            FolderFilter.Unfiled -> bookmarks.filter { it.folderId == null }
            is FolderFilter.Specific -> bookmarks.filter { it.folderId == f.id }
        }
    }
    val filteredSorted = remember(folderFiltered, query, sortOption) {
        val filtered = if (query.isBlank()) folderFiltered
            else folderFiltered.filter { it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) }
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (bookmarks.isNotEmpty() || folders.isNotEmpty()) {
                FolderChipsRow(
                    folders = folders,
                    selected = selectedFolder,
                    onSelect = { selectedFolder = it },
                    onCreateFolder = { name -> viewModel.createBookmarkFolder(name) },
                    onDeleteFolder = { id ->
                        viewModel.deleteBookmarkFolder(id)
                        selectedFolder = FolderFilter.All
                    }
                )
            }
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
                filteredSorted.isEmpty() -> EmptyStateMessage(icon = Icons.Default.SearchOff, message = "Brak wyników")
                else -> LazyColumn(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(filteredSorted, key = { it.id }) { entry ->
                        BookmarkCard(
                            entry = entry,
                            folders = folders,
                            onClick = { onNavigate(entry.url); onBack() },
                            onDelete = {
                                viewModel.removeBookmark(entry.url)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Usunięto zakładkę",
                                        actionLabel = "Cofnij",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreBookmark(entry)
                                }
                            },
                            onEdit = { title, folderId -> viewModel.updateBookmark(entry.id, title, folderId) }
                        )
                    }
                }
            }
        }
    }
}

private sealed class FolderFilter {
    object All : FolderFilter()
    object Unfiled : FolderFilter()
    data class Specific(val id: Long, val name: String) : FolderFilter()
}

@Composable
private fun FolderChipsRow(
    folders: List<BookmarkFolderEntity>,
    selected: FolderFilter,
    onSelect: (FolderFilter) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (Long) -> Unit
) {
    var creatingFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(selected = selected is FolderFilter.All, onClick = { onSelect(FolderFilter.All) }, label = { Text("Wszystkie") })
        FilterChip(selected = selected is FolderFilter.Unfiled, onClick = { onSelect(FolderFilter.Unfiled) }, label = { Text("Bez folderu") })
        folders.forEach { folder ->
            FilterChip(
                selected = selected is FolderFilter.Specific && selected.id == folder.id,
                onClick = { onSelect(FolderFilter.Specific(folder.id, folder.name)) },
                label = { Text(folder.name) }
            )
        }
        AssistChip(
            onClick = { creatingFolder = true },
            label = { Text("Nowy folder") },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        val selectedSpecific = selected as? FolderFilter.Specific
        if (selectedSpecific != null) {
            IconButton(onClick = { onDeleteFolder(selectedSpecific.id) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Usuń folder \"${selectedSpecific.name}\"", modifier = Modifier.size(18.dp))
            }
        }
    }
    if (creatingFolder) {
        AlertDialog(
            onDismissRequest = { creatingFolder = false; newFolderName = "" },
            title = { Text("Nowy folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text("Nazwa folderu") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateFolder(newFolderName)
                        creatingFolder = false
                        newFolderName = ""
                    },
                    enabled = newFolderName.isNotBlank()
                ) { Text("Utwórz") }
            },
            dismissButton = {
                TextButton(onClick = { creatingFolder = false; newFolderName = "" }) { Text("Anuluj") }
            }
        )
    }
}

@Composable
private fun BookmarkCard(
    entry: BookmarkEntity,
    folders: List<BookmarkFolderEntity>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (title: String, folderId: Long?) -> Unit
) {
    val host = remember(entry.url) { runCatching { Uri.parse(entry.url).host }.getOrNull()?.removePrefix("www.") ?: entry.url }
    var editing by remember { mutableStateOf(false) }
    val folderName = remember(entry.folderId, folders) { folders.find { it.id == entry.folderId }?.name }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        host,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (folderName != null) {
                        Text(
                            " · $folderName",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = { editing = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edytuj zakładkę",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
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
    if (editing) {
        BookmarkEditDialog(
            initialTitle = entry.title,
            initialFolderId = entry.folderId,
            folders = folders,
            onDismiss = { editing = false },
            onSave = { title, folderId ->
                onEdit(title, folderId)
                editing = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkEditDialog(
    initialTitle: String,
    initialFolderId: Long?,
    folders: List<BookmarkFolderEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var folderId by remember { mutableStateOf(initialFolderId) }
    var folderMenuOpen by remember { mutableStateOf(false) }
    val folderLabel = folders.find { it.id == folderId }?.name ?: "Bez folderu"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edytuj zakładkę") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("Nazwa") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Box {
                    OutlinedButton(onClick = { folderMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(folderLabel)
                    }
                    DropdownMenu(expanded = folderMenuOpen, onDismissRequest = { folderMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Bez folderu") }, onClick = { folderId = null; folderMenuOpen = false })
                        folders.forEach { f ->
                            DropdownMenuItem(text = { Text(f.name) }, onClick = { folderId = f.id; folderMenuOpen = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, folderId) }, enabled = title.isNotBlank()) { Text("Zapisz") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

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

private val searchEnginePresets: List<Pair<String, String?>> = listOf(
    "Google" to "https://www.google.com/search?q={query}",
    "Bing" to "https://www.bing.com/search?q={query}",
    "DuckDuckGo" to "https://duckduckgo.com/?q={query}",
    "Brave Search" to "https://search.brave.com/search?q={query}",
    "Ecosia" to "https://www.ecosia.org/search?q={query}",
    "Startpage" to "https://www.startpage.com/sp/search?query={query}",
    "Własny adres" to null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetDropdown(
    options: List<Pair<String, String?>>,
    selectedValue: String,
    onOptionSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.second == selectedValue }?.first
        ?: options.last().first
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
    val adBlockProEnabled by viewModel.adBlockProEnabledFlow.collectAsState(initial = true)
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
    fun saveSearchEngine(url: String) {
        val saved = url.ifBlank { "https://www.google.com/search?q={query}" }
        viewModel.setSearchEngineUrl(saved)
        searchEngineText = saved
        scope.launch { snackbarHostState.showSnackbar("Zapisano wyszukiwarkę") }
    }
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
                    "Gradient na ekranie skrótów nowej karty (używany też jako kolor paska ładowania). Bez gradientu, w ciemnym motywie tło jest ciemnoniebieskie, a pasek ładowania biały; w jasnym motywie pasek jest czarny. Tryb prywatny używa własnego, ciemniejszego wariantu.",
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
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Blokowanie reklam PRO", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "PRO",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "Reguły wildcard i wyjątki + ukrywanie pustych miejsc po reklamach na stronie",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = adBlockProEnabled,
                        onCheckedChange = { viewModel.setAdBlockProEnabled(it) },
                        enabled = adBlockEnabled
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text("Domyślna wyszukiwarka", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                PresetDropdown(
                    options = searchEnginePresets,
                    selectedValue = searchEngineText,
                    onOptionSelected = { picked ->
                        if (picked != null) {
                            saveSearchEngine(picked)
                        } else {
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
