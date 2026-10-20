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
import androidx.compose.runtime.*
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
            } catch (e: Exception) {}
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
    val normalColors: List<Color>,
    val privateColors: List<Color>
)

val gradientPresets: List<GradientPreset> = listOf(
    GradientPreset(
        id = "none",
        label = "Brak",
        normalColors = emptyList(),
        privateColors = emptyList()
    ),
    GradientPreset(
        id = "microsoft_fluent",
        label = "Fluent",
        normalColors = listOf(Color(0xFF0078D4), Color(0xFF005A9E)),
        privateColors = listOf(Color(0xFF2D2D2D), Color(0xFF1F1F1F))
    ),
    GradientPreset(
        id = "sunset",
        label = "Zmierzch",
        normalColors = listOf(Color(0xFFFF9A5C), Color(0xFFFF6F91)),
        privateColors = listOf(Color(0xFF33192B), Color(0xFF1F0A1A))
    ),
    GradientPreset(
        id = "ocean",
        label = "Ocean",
        normalColors = listOf(Color(0xFF0078D4), Color(0xFF6DD5ED)),
        privateColors = listOf(Color(0xFF0B2545), Color(0xFF13315C))
    ),
    GradientPreset(
        id = "forest",
        label = "Las",
        normalColors = listOf(Color(0xFF107C41), Color(0xFF38EF7D)),
        privateColors = listOf(Color(0xFF0B3D2E), Color(0xFF145C43))
    )
)

private val dailyFacts = listOf(
    "Miód znaleziony w egipskich grobowcach sprzed 3000 lat wciąż nadaje się do jedzenia.",
    "Ośmiornice mają trzy serca i niebieską krew.",
    "Banany są jagodami z botanicznego punktu widzenia, a truskawki nie.",
    "Wenus to jedyna planeta Układu Słonecznego, która obraca się w przeciwną stronę.",
    "Pierwsza wiadomość e-mail została wysłana w 1971 roku.",
    "Serce wieloryba błękitnego jest wielkości małego samochodu.",
    "Islandia nie ma komarów ze względu na specyficzny klimat.",
    "Słowo 'OK' jest jednym z najczęściej rozpoznawanych wyrazów na świecie.",
    "Najkrótsza wojna w historii trwała około 38 minut.",
    "Ludzki nos potrafi rozróżnić ponad bilion różnych zapachów."
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
        prefs[newTabGradientKey] ?: "microsoft_fluent"
    }
    val pinnedSites: Flow<List<PinnedSite>> = context.dataStore.data.map { prefs ->
        prefs[pinnedSitesKey]?.let { parsePinnedSites(it) } ?: emptyList()
    }
    val dataSaverEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[dataSaverKey] ?: false
    }
    suspend fun setTheme(value: AppTheme) { context.dataStore.edit { it[themeKey] = value.name } }
    suspend fun setSearchEngineUrl(value: String) { context.dataStore.edit { it[searchEngineKey] = value } }
    suspend fun setAdBlockEnabled(value: Boolean) { context.dataStore.edit { it[adBlockKey] = value } }
    suspend fun setAdBlockProEnabled(value: Boolean) { context.dataStore.edit { it[adBlockProKey] = value } }
    suspend fun setDownloadFolderUri(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(downloadFolderUriKey) else prefs[downloadFolderUriKey] = value
        }
    }
    suspend fun setNewTabGradientId(value: String) { context.dataStore.edit { it[newTabGradientKey] = value } }
    suspend fun setPinnedSites(sites: List<PinnedSite>) { context.dataStore.edit { it[pinnedSitesKey] = serializePinnedSites(sites) } }
    suspend fun setDataSaverEnabled(value: Boolean) { context.dataStore.edit { it[dataSaverKey] = value } }
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
        val isDebugBuild = (webView.context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
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
    private val ipHostPattern = Pattern.compile("^https?://(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$")
    private val userInfoTrickPattern = Pattern.compile("^https?://[^/]*@")
    private val suspiciousBrandSubdomain = Pattern.compile(
        "(paypal|apple|google|microsoft|bank|allegro|inpost|dhl)\\.[a-z0-9-]+\\.(xyz|top|club|work|gq|tk|zip|mov)",
        Pattern.CASE_INSENSITIVE
    )
    private val punycodePattern = Pattern.compile("://xn--", Pattern.CASE_INSENSITIVE)
    enum class RiskLevel { SAFE, SUSPICIOUS, BLOCK }
    data class Verdict(val risk: RiskLevel, val reason: String?)
    fun evaluate(url: String): Verdict {
        if (userInfoTrickPattern.matcher(url).find()) return Verdict(RiskLevel.BLOCK, "Adres próbuje ukryć prawdziwy host za znakiem @")
        if (suspiciousBrandSubdomain.matcher(url).find()) return Verdict(RiskLevel.BLOCK, "Znana marka użyta jako subdomena podejrzanej domeny")
        if (ipHostPattern.matcher(url).find()) return Verdict(RiskLevel.SUSPICIOUS, "Adres to bezpośrednie IP")
        if (punycodePattern.matcher(url).find()) return Verdict(RiskLevel.SUSPICIOUS, "Adres zawiera zakodowane znaki Unicode")
        return Verdict(RiskLevel.SAFE, null)
    }
}

object SafeBrowsingCallback {
    fun handle(threatType: Int, callback: SafeBrowsingResponse, onBlocked: (String) -> Unit) {
        val reason = when (threatType) {
            WebViewClient.SAFE_BROWSING_THREAT_MALWARE -> "Wykryto złośliwe oprogramowanie"
            WebViewClient.SAFE_BROWSING_THREAT_PHISHING -> "Wykryto próbę phishingu"
            else -> "Wykryto zagrożenie bezpieczeństwa"
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
            else -> "Problem z certyfikatem bezpieczeństwa"
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

data class PageLoadError(val title: String, val message: String, val isHttpError: Boolean = false)
data class GeolocationRequestInfo(val origin: String, val callback: GeolocationPermissions.Callback)

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
    var isNewTabPage = mutableStateOf(true)
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
                if (ExternalUriHandler.tryHandle(view.context, target)) return true
                val verdict = DangerousUrlGuard.evaluate(target)
                when (verdict.risk) {
                    DangerousUrlGuard.RiskLevel.BLOCK -> { tab.securityWarning.value = verdict.reason; return true }
                    DangerousUrlGuard.RiskLevel.SUSPICIOUS -> { tab.securityWarning.value = verdict.reason }
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
                        historyDao.insert(HistoryEntity(url = u, title = view.title ?: u, visitedAt = System.currentTimeMillis()))
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
            override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest, threatType: Int, callback: SafeBrowsingResponse) {
                SafeBrowsingCallback.handle(threatType, callback) { reason -> tab.securityWarning.value = reason }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                val requestedHost = tab.lastNavigationUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                val errorHost = runCatching { Uri.parse(error.url).host }.getOrNull()
                if (requestedHost == null || requestedHost != errorHost) {
                    handler.cancel()
                    return
                }
                tab.pendingCertError.value = CertificateInspector.describe(error, handler)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    tab.isLoading.value = false
                    tab.pageError.value = PageLoadError("Nie można wczytać strony", "Sprawdź połączenie z internetem.\n\n${error.description}")
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    tab.isLoading.value = false
                    tab.pageError.value = PageLoadError("Błąd ${errorResponse.statusCode}", "Serwer zwrócił błąd.", true)
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
            override fun onShowFileChooser(view: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
                pendingFileChooserCallback?.onReceiveValue(null)
                pendingFileChooserCallback = filePathCallback
                fileChooserIntent.value = fileChooserParams.createIntent()
                return true
            }
            override fun onPermissionRequest(request: PermissionRequest) { pendingWebPermissionRequest.value = request }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                pendingGeolocationRequest.value = GeolocationRequestInfo(origin, callback)
            }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
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
            closedTab.webView.apply { clearHistory(); clearCache(true); clearFormData() }
        }
        closedTab.webView.destroy()
        tabs.removeAt(idx)
        if (wasPrivate && tabs.none { it.isPrivate }) clearCookiesAndSiteData()
        if (activeTabId.value == tabId) {
            activeTabId.value = tabs.getOrNull(idx.coerceAtMost(tabs.lastIndex))?.id
        }
        if (tabs.isEmpty()) createTab("", showStartPage = true)
    }

    fun restoreLastClosedTab() {
        if (closedTabsStack.isEmpty()) return
        createTab(closedTabsStack.removeLast(), select = true)
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
                DangerousUrlGuard.RiskLevel.BLOCK -> { tab.securityWarning.value = verdict.reason; return@launch }
                DangerousUrlGuard.RiskLevel.SUSPICIOUS -> { tab.securityWarning.value = verdict.reason }
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
        if (!trimmed.contains(" ") && domainPattern.matches(trimmed)) return "https://$trimmed"
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
        } catch (e: Exception) { null }
    }

    private fun searchUrl(rawInput: String, searchTemplate: String): String {
        val query = Uri.encode(rawInput.trim())
        return if (searchTemplate.contains("{query}")) searchTemplate.replace("{query}", query) else searchTemplate + query
    }

    fun goBack() { activeTab()?.takeIf { it.webView.canGoBack() }?.webView?.goBack() }
    fun goForward() { activeTab()?.takeIf { it.webView.canGoForward() }?.webView?.goForward() }
    fun reload() { activeTab()?.webView?.reload() }

    fun cancelCertificateWarning(tab: BrowserTab) {
        tab.pendingCertError.value?.handler?.let { CertificateInspector.cancelConnection(it) }
        tab.pendingCertError.value = null
    }
    fun proceedDespiteCertificateWarning(tab: BrowserTab) {
        tab.pendingCertError.value?.handler?.let { CertificateInspector.proceedDespiteWarning(it) }
        tab.pendingCertError.value = null
    }
    fun dismissSecurityWarning(tab: BrowserTab) { tab.securityWarning.value = null }
    fun openBlockedPopup(tab: BrowserTab) {
        val url = tab.blockedPopupUrl.value ?: return
        tab.blockedPopupUrl.value = null
        createTab(url, select = true, isPrivate = tab.isPrivate)
    }
    fun dismissBlockedPopup(tab: BrowserTab) { tab.blockedPopupUrl.value = null }
    fun exitFullscreenVideo(tab: BrowserTab) {
        tab.customViewCallback?.onCustomViewHidden()
        tab.customView.value = null
        tab.customViewCallback = null
    }
    fun startFindInPage(tab: BrowserTab) { tab.findInPageActive.value = true }
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

    private val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
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
        viewModelScope.launch { tab.isBookmarked.value = bookmarkDao.countByUrl(tab.url.value) > 0 }
    }

    fun toggleBookmark() {
        val tab = activeTab() ?: return
        viewModelScope.launch {
            if (bookmarkDao.countByUrl(tab.url.value) > 0) {
                bookmarkDao.deleteByUrl(tab.url.value)
                tab.isBookmarked.value = false
            } else {
                bookmarkDao.insert(BookmarkEntity(url = tab.url.value, title = tab.title.value, createdAt = System.currentTimeMillis()))
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

    fun restoreBookmark(entry: BookmarkEntity) { viewModelScope.launch { bookmarkDao.insert(entry) } }
    fun updateBookmark(id: Long, title: String, folderId: Long?) {
        viewModelScope.launch { bookmarkDao.update(id, title.ifBlank { "Bez nazwy" }, folderId) }
    }
    fun createBookmarkFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { bookmarkFolderDao.insert(BookmarkFolderEntity(name = name.trim(), createdAt = System.currentTimeMillis())) }
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
            val updated = if (current.any { it.url == url }) current.filterNot { it.url == url } else current + PinnedSite(url, title.ifBlank { url })
            settings.setPinnedSites(updated)
        }
    }
    fun clearHistory() { viewModelScope.launch { historyDao.clear() } }
    fun deleteHistoryEntry(id: Long) { viewModelScope.launch { historyDao.deleteById(id) } }
    fun restoreHistoryEntry(entry: HistoryEntity) { viewModelScope.launch { historyDao.insert(entry) } }
    fun restoreHistoryEntries(entries: List<HistoryEntity>) { viewModelScope.launch { entries.forEach { historyDao.insert(it) } } }
    fun clearCookiesAndSiteData() {
        android.webkit.CookieManager.getInstance().apply { removeAllCookies(null); flush() }
        android.webkit.WebStorage.getInstance().deleteAllData()
    }
    fun clearBrowserCache() { tabs.forEach { it.webView.clearCache(true) } }
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

    private val adBlockRules = mutableStateOf(AdBlockRuleParser.ParsedRules(emptySet(), emptyList(), emptySet(), emptySet()))
    private val fallbackAdBlockDomains = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "adsrvr.org", "criteo.com",
        "taboola.com", "outbrain.com", "scorecardresearch.com", "googletagmanager.com"
    )
    private val fallbackCosmeticSelectors = setOf(
        "[id*=\"google_ads\"]", "[class*=\"adsbygoogle\"]", "[id*=\"div-gpt-ad\"]"
    )
    private fun isAdHost(host: String?): Boolean {
        if (host == null) return false
        val rules = adBlockRules.value
        if (rules.exceptions.any { host == it || host.endsWith(".$it") }) return false
        if (rules.exactDomains.any { host == it || host.endsWith(".$it") }) return true
        if (!adBlockProEnabledCache) return false
        return rules.wildcardPatterns.any { it.matches(host) }
    }
    private fun injectCosmeticHiding(view: WebView) {
        val selectors = adBlockRules.value.cosmeticSelectors
        if (selectors.isEmpty()) return
        val css = selectors.joinToString(",") { it.replace("\\", "\\\\").replace("`", "") } + "{display:none!important;}"
        val js = "(function(){try{var s=document.createElement('style');s.textContent=`$css`;document.documentElement.appendChild(s);}catch(e){}})();"
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
                val cursor = manager.query(DownloadManager.Query().setFilterById(record.downloadManagerId))
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
                                    val localUriString = it.getString(it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                                    record.openUri.value = try { manager.getUriForDownloadedFile(record.downloadManagerId) } catch (e: Exception) { localUriString?.let { s -> Uri.parse(s) } }
                                    justCompletedDownload.value = record
                                    return@launch
                                }
                                DownloadManager.STATUS_FAILED -> { record.status.value = "Przerwano"; return@launch }
                                DownloadManager.STATUS_PAUSED -> record.status.value = "Wstrzymano"
                                DownloadManager.STATUS_PENDING -> record.status.value = "Oczekiwanie..."
                                else -> record.status.value = "Pobieranie..."
                            }
                        } else { missingRowStreak++ }
                    }
                }
                if (missingRowStreak >= 5) { record.status.value = "Błąd"; return@launch }
                delay(600)
            }
        }
    }

    fun openDownloadedFile(record: DownloadRecord) {
        val uri = record.openUri.value ?: run { openSystemDownloadsScreen(); return }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, record.mimeType?.takeIf { it.isNotBlank() } ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        } catch (e: Exception) { openSystemDownloadsScreen() }
    }
    private fun openSystemDownloadsScreen() {
        try { app.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }
}

// Fluent Microsoft Palette Colors
private val FluentLightColors = lightColorScheme(
    primary = Color(0xFF0078D4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1F0FF),
    onPrimaryContainer = Color(0xFF001D36),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFF1F1F1),
    onSurfaceVariant = Color(0xFF616161),
    outline = Color(0xFF8A8A8A)
)

private val FluentDarkColors = darkColorScheme(
    primary = Color(0xFF4CB3FF),
    onPrimary = Color(0xFF00325B),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFCEE5FF),
    surface = Color(0xFF181818),
    onSurface = Color(0xFFF3F3F3),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF707070)
)

@Composable
fun SuperBrowserTheme(appTheme: AppTheme, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (appTheme) {
        AppTheme.SYSTEM -> systemDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val colorScheme = if (useDark) FluentDarkColors else FluentLightColors
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
    val newTabGradientId by viewModel.newTabGradientIdFlow.collectAsState(initial = "microsoft_fluent")
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
            snackbarHostState.showSnackbar(if (wasPinned) "Odpięto stronę" else "Przypięto stronę", duration = SnackbarDuration.Short)
        }
    }

    val addressSuggestions = remember(addressText, addressFocused, history, bookmarks) {
        if (!addressFocused || addressText.isBlank()) emptyList()
        else {
            val query = addressText.trim().lowercase()
            val fromBookmarks = bookmarks.filter { it.url.lowercase().contains(query) || it.title.lowercase().contains(query) }.map { AddressSuggestion(it.title.ifBlank { it.url }, it.url, true) }
            val fromHistory = history.filter { it.url.lowercase().contains(query) || it.title.lowercase().contains(query) }.map { AddressSuggestion(it.title.ifBlank { it.url }, it.url, false) }
            (fromBookmarks + fromHistory).distinctBy { it.url }.take(5)
        }
    }

    val tabListState = rememberLazyListState()
    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = viewModel.pendingFileChooserCallback
        viewModel.pendingFileChooserCallback = null
        val uris = if (result.resultCode == android.app.Activity.RESULT_OK) WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data) else null
        callback?.onReceiveValue(uris)
    }

    LaunchedEffect(viewModel.fileChooserIntent.value) {
        viewModel.fileChooserIntent.value?.let { intent ->
            fileChooserLauncher.launch(intent)
            viewModel.fileChooserIntent.value = null
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
                    tonalElevation = 2.dp,
                    color = if (isActivePrivate) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.statusBarsPadding()
                ) {
                    Column {
                        if (isActivePrivate) {
                            Row(
                                Modifier.fillMaxWidth().background(Color(0xFF0F0F0F)).padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VisibilityOff, null, tint = Color(0xFF4CB3FF), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Tryb InPrivate — aktywna sesja poufna", color = Color(0xFFE1F0FF), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        // Fluent Search/Nav Bar (Edge Style)
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompactIcon(Icons.AutoMirrored.Filled.ArrowForward, "Dalej", activeTab?.canGoForward?.value == true, tint = if (isActivePrivate) Color.White else LocalContentColor.current) { viewModel.goForward() }
                            CompactIcon(Icons.Default.Refresh, "Odśwież", true, tint = if (isActivePrivate) Color.White else LocalContentColor.current) { viewModel.reload() }
                            Spacer(Modifier.width(4.dp))
                            AddressSecurityIcon(activeTab)
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isActivePrivate) Color(0xFF2D2D2D) else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, if (addressFocused) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (addressText.isEmpty()) {
                                    Text(
                                        "Wyszukaj lub wpisz adres internetowy",
                                        fontSize = 13.sp,
                                        color = if (isActivePrivate) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                BasicTextField(
                                    value = addressText,
                                    onValueChange = { addressText = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 13.sp, color = if (isActivePrivate) Color.White else MaterialTheme.colorScheme.onSurface),
                                    keyboardActions = KeyboardActions(onDone = {
                                        viewModel.navigate(addressText)
                                        addressFocused = false
                                        focusManager.clearFocus()
                                    }),
                                    modifier = Modifier.fillMaxWidth().onFocusChanged { addressFocused = it.isFocused }
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            CompactIcon(
                                icon = if (activeTab?.isBookmarked?.value == true) Icons.Default.Star else Icons.Outlined.StarOutline,
                                desc = "Zakładka",
                                tint = if (activeTab?.isBookmarked?.value == true) Color(0xFFFFB900) else if (isActivePrivate) Color.White else LocalContentColor.current
                            ) { viewModel.toggleBookmark() }
                            CompactIcon(Icons.Default.MoreVert, "Menu", true, tint = if (isActivePrivate) Color.White else LocalContentColor.current) { menuOpen = true }
                        }

                        if (activeTab?.findInPageActive?.value == true) {
                            val findFocusRequester = remember { FocusRequester() }
                            LaunchedEffect(activeTab.id) { findFocusRequester.requestFocus() }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = activeTab.findInPageQuery.value,
                                        onValueChange = { viewModel.updateFindInPageQuery(activeTab, it) },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                                        modifier = Modifier.fillMaxWidth().focusRequester(findFocusRequester)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                CompactIcon(Icons.Default.KeyboardArrowUp, "Poprzednie", true) { viewModel.findPrevious(activeTab) }
                                CompactIcon(Icons.Default.KeyboardArrowDown, "Następne", true) { viewModel.findNext(activeTab) }
                                CompactIcon(Icons.Default.Close, "Zamknij", true) { viewModel.stopFindInPage(activeTab) }
                            }
                        }

                        if (addressSuggestions.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                                addressSuggestions.forEach { suggestion ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            addressText = suggestion.url
                                            viewModel.navigate(suggestion.url)
                                            addressFocused = false
                                            focusManager.clearFocus()
                                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(if (suggestion.isBookmark) Icons.Default.Star else Icons.Default.History, null, modifier = Modifier.size(16.dp), tint = if (suggestion.isBookmark) Color(0xFFFFB900) else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(suggestion.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            Text(suggestion.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                            }
                        }

                        if (activeTab?.isLoading?.value == true) {
                            val progress = activeTab.loadProgress.value.coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent
                            )
                        }

                        activeTab?.securityWarning?.value?.let { warning ->
                            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.dismissSecurityWarning(activeTab) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, "Zamknij", modifier = Modifier.size(14.dp))
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
                            onOpenPrivateTab = { viewModel.createTab("", select = true, isPrivate = true, showStartPage = true) },
                            onTogglePin = onTogglePin
                        )
                    } else {
                        WebViewContainer(webView = tab.webView, modifier = Modifier.fillMaxSize())
                        tab.pageError.value?.let { err ->
                            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(if (err.isHttpError) Icons.Default.ErrorOutline else Icons.Default.WifiOff, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(16.dp))
                                    Text(err.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(8.dp))
                                    Text(err.message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(20.dp))
                                    Button(onClick = { tab.pageError.value = null; tab.webView.reload() }, shape = RoundedCornerShape(8.dp)) { Text("Spróbuj ponownie") }
                                }
                            }
                        }
                    }
                }
            }

            // Fluent Bottom Navigation Bar (Edge Style Tabs Bar)
            Surface(tonalElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(Modifier.weight(1f), state = tabListState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewModel.tabs, key = { it.id }) { tab ->
                            TabChip(tab = tab, selected = tab.id == viewModel.activeTabId.value, onSelect = { viewModel.selectTab(tab.id) }) {
                                val canUndo = tab.url.value.isNotBlank() && !tab.isPrivate
                                viewModel.closeTab(tab.id)
                                if (canUndo) {
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar("Zamknięto kartę", "Cofnij", duration = SnackbarDuration.Short)
                                        if (result == SnackbarResult.ActionPerformed) viewModel.restoreLastClosedTab()
                                    }
                                }
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.createTab("", showStartPage = true) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Add, "Nowa karta", modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 60.dp))

        if (menuOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { menuOpen = false })
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
                        val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, currentUrl) }
                        context.startActivity(Intent.createChooser(shareIntent, "Udostępnij"))
                    }
                },
                onCopyLink = {
                    if (currentUrl.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(currentUrl))
                        scope.launch { snackbarHostState.showSnackbar("Skopiowano link do schowka") }
                    }
                },
                onFind = { activeTab?.let { viewModel.startFindInPage(it) } },
                onToggleDesktop = { activeTab?.let { viewModel.toggleDesktopMode(it) } },
                onTogglePinClick = { onTogglePin(currentUrl, activeTab?.title?.value ?: currentUrl) },
                onTranslate = { viewModel.translatePage() },
                onNewPrivateTab = { viewModel.createTab("", select = true, isPrivate = true, showStartPage = true) },
                onRestoreClosedTab = { viewModel.restoreLastClosedTab() },
                onToggleDataSaver = { viewModel.setDataSaverEnabled(!dataSaverEnabled) },
                onShowTabs = {},
                onOpenHistory = onOpenHistory,
                onOpenBookmarks = onOpenBookmarks,
                onOpenDownloads = onOpenDownloads,
                onOpenSettings = onOpenSettings,
                onDismiss = { menuOpen = false }
            )
        }
    }
}

@Composable
private fun CompactIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean = true, tint: Color = LocalContentColor.current, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(icon, desc, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickActionButton(Icons.Default.Share, "Udostępnij", act(onShare))
                QuickActionButton(Icons.Default.ContentCopy, "Kopiuj", act(onCopyLink))
                QuickActionButton(Icons.Default.Search, "Znajdź", act(onFind))
                if (hasCurrentUrl) {
                    QuickActionButton(if (isCurrentPinned) Icons.Default.PushPin else Icons.Outlined.PushPin, if (isCurrentPinned) "Odepnij" else "Przypnij", act(onTogglePinClick))
                }
                QuickActionButton(Icons.Default.Computer, if (isDesktopMode) "Mobilna" else "Komputer", act(onToggleDesktop))
                QuickActionButton(if (dataSaverEnabled) Icons.Default.DataSaverOn else Icons.Default.DataSaverOff, "Oszczędzanie", act(onToggleDataSaver))
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            MenuRow(Icons.Default.Tab, "Karty ($tabCount)", act(onShowTabs))
            if (blockedAdsCount > 0) {
                MenuRow(Icons.Default.Shield, "Zablokowane reklamy: $blockedAdsCount", onDismiss)
            }
            MenuRow(Icons.Default.Translate, "Przetłumacz stronę", act(onTranslate))
            MenuRow(Icons.Default.VisibilityOff, "Nowa karta InPrivate", act(onNewPrivateTab))
            MenuRow(Icons.Default.Restore, "Przywróć zamkniętą kartę", act(onRestoreClosedTab))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            MenuRow(Icons.Default.History, "Historia", act(onOpenHistory))
            MenuRow(Icons.Default.Star, "Ulubione i zakładki", act(onOpenBookmarks))
            MenuRow(Icons.Default.Download, "Pobrane pliki", act(onOpenDownloads))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            MenuRow(Icons.Default.Settings, "Ustawienia", act(onOpenSettings))
        }
    }
}

@Composable
private fun QuickActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(70.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AddressSecurityIcon(tab: BrowserTab?) {
    val url = tab?.url?.value.orEmpty()
    val hasWarning = tab?.securityWarning?.value != null
    val icon = when {
        hasWarning -> Icons.Default.Warning
        url.startsWith("https://", ignoreCase = true) -> Icons.Default.Lock
        url.startsWith("http://", ignoreCase = true) -> Icons.Default.LockOpen
        else -> Icons.Default.Public
    }
    val tint = when {
        hasWarning -> MaterialTheme.colorScheme.error
        url.startsWith("https://", ignoreCase = true) -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
}

@Composable
private fun QuickShortcutChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, hasGradient: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (hasGradient) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (hasGradient) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (hasGradient) Color.White else MaterialTheme.colorScheme.onSurface)
        }
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
    val textColor = if (hasGradient || isPrivate) Color.White else MaterialTheme.colorScheme.onSurface
    val subtleColor = if (hasGradient || isPrivate) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
    val tileBackground = if (hasGradient) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant

    val backgroundModifier = when {
        hasGradient -> Modifier.background(Brush.verticalGradient(gradientColors))
        isPrivate -> Modifier.background(Color(0xFF141414))
        isDarkTheme -> Modifier.background(Color(0xFF181818))
        else -> Modifier.background(MaterialTheme.colorScheme.surface)
    }

    Column(
        Modifier.fillMaxSize().then(backgroundModifier).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Microsoft Edge Style Fluent Brand Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Public, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Edge / AiXE", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = textColor)
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isPrivate) {
                QuickShortcutChip(Icons.Default.VisibilityOff, "InPrivate", hasGradient, onOpenPrivateTab)
            }
            QuickShortcutChip(Icons.Default.Newspaper, "Wiadomości", hasGradient) { onSiteClick("https://news.google.com/?hl=pl&gl=PL&ceid=PL:pl") }
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
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(if (hasGradient) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { submitSearch() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Search, "Szukaj", tint = textColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.weight(1f)) {
                if (searchText.isEmpty()) {
                    Text("Wyszukaj lub wpisz adres sieci Web", fontSize = 14.sp, color = subtleColor)
                }
                BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = textColor),
                    keyboardActions = KeyboardActions(onDone = { submitSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (searchText.isNotEmpty()) {
                IconButton(onClick = { searchText = "" }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Wyczyść", tint = textColor, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        val pinnedAsTiles = remember(pinnedSites, frequentSites) {
            pinnedSites.map { pinned ->
                FrequentSite(url = pinned.url, title = pinned.title, visitCount = 0, faviconBase64 = frequentSites.find { it.url == pinned.url }?.faviconBase64)
            }
        }

        if (pinnedAsTiles.isNotEmpty()) {
            Text("Przypięte", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = textColor, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(12.dp))
            TileGrid(pinnedAsTiles, true, tileBackground, textColor, onSiteClick, onTogglePin)
            Spacer(Modifier.height(20.dp))
        }

        if (frequentSites.isNotEmpty()) {
            Text("Najczęściej odwiedzane", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = textColor, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(12.dp))
            TileGrid(frequentSites, false, tileBackground, textColor, onSiteClick, onTogglePin)
        }

        Spacer(Modifier.height(24.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (hasGradient || isPrivate) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(fact, style = MaterialTheme.typography.bodySmall, color = textColor)
            }
        }
    }
}

@Composable
private fun TileGrid(sites: List<FrequentSite>, isPinnedSection: Boolean, background: Color, textColor: Color, onClick: (String) -> Unit, onLongClick: (url: String, title: String) -> Unit) {
    sites.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { site ->
                FrequentSiteTile(site, background, textColor, isPinnedSection, { onClick(site.url) }, { onLongClick(site.url, site.title) }, Modifier.weight(1f))
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrequentSiteTile(site: FrequentSite, background: Color, textColor: Color, isPinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val host = remember(site.url) { runCatching { Uri.parse(site.url).host }.getOrNull() ?: site.url }
    val faviconBitmap = remember(site.faviconBase64) {
        site.faviconBase64?.let { encoded ->
            try {
                val bytes = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) { null }
        }
    }
    Column(modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(background),
            contentAlignment = Alignment.Center
        ) {
            if (faviconBitmap != null) {
                androidx.compose.foundation.Image(bitmap = faviconBitmap, contentDescription = null, modifier = Modifier.size(28.dp))
            } else {
                Text(host.removePrefix("www.").take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textColor)
            }
            if (isPinned) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PushPin, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(host.removePrefix("www."), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = textColor)
    }
}

@Composable
private fun WebViewContainer(webView: WebView, modifier: Modifier = Modifier) {
    AndroidView(modifier = modifier, factory = { FrameLayout(it) }, update = { frame ->
        if (frame.childCount == 0 || frame.getChildAt(0) !== webView) {
            frame.removeAllViews()
            (webView.parent as? ViewGroup)?.removeView(webView)
            frame.addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    })
}

@Composable
private fun TabChip(tab: BrowserTab, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val chipColor = when {
        tab.isPrivate && selected -> Color(0xFF2D2D2D)
        tab.isPrivate -> Color(0xFF1E1E1E)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val chipContentColor = if (tab.isPrivate) Color.White else MaterialTheme.colorScheme.onSurface
    Surface(
        color = chipColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(38.dp).widthIn(min = 110.dp, max = 160.dp),
        tonalElevation = if (selected) 4.dp else 0.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onSelect).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val favicon = tab.favicon.value
                when {
                    tab.isPrivate -> Icon(Icons.Default.VisibilityOff, null, tint = chipContentColor, modifier = Modifier.size(14.dp))
                    favicon != null -> androidx.compose.foundation.Image(bitmap = favicon.asImageBitmap(), null, modifier = Modifier.size(14.dp))
                    else -> Icon(Icons.Default.Public, null, tint = chipContentColor, modifier = Modifier.size(14.dp))
                }
                Text(tab.title.value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = chipContentColor, modifier = Modifier.weight(1f, fill = false))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp).padding(end = 2.dp)) {
                Icon(Icons.Default.Close, "Zamknij", tint = chipContentColor, modifier = Modifier.size(14.dp))
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historia przeglądania", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (history.isNotEmpty()) {
                        TextButton(onClick = { confirmingClearAll = true }) { Text("Wyczyść wszystko") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Brak historii", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(history, key = { it.id }) { entry ->
                    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { onNavigate(entry.url); onBack() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.title.ifBlank { entry.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(entry.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                viewModel.deleteHistoryEntry(entry.id)
                                scope.launch {
                                    val res = snackbarHostState.showSnackbar("Usunięto wpis", "Cofnij")
                                    if (res == SnackbarResult.ActionPerformed) viewModel.restoreHistoryEntry(entry)
                                }
                            }) { Icon(Icons.Default.Close, "Usuń", modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
    }
    if (confirmingClearAll) {
        AlertDialog(
            onDismissRequest = { confirmingClearAll = false },
            title = { Text("Wyczyścić historię?") },
            confirmButton = { TextButton(onClick = { confirmingClearAll = false; viewModel.clearHistory() }) { Text("Wyczyść", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmingClearAll = false }) { Text("Anuluj") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(viewModel: BrowserViewModel = viewModel(), onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val bookmarks by viewModel.bookmarksFlow.collectAsState(initial = emptyList())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ulubione i zakładki", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Brak zakładek", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(bookmarks, key = { it.id }) { entry ->
                    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { onNavigate(entry.url); onBack() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFB900), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.title.ifBlank { entry.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(entry.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.removeBookmark(entry.url) }) { Icon(Icons.Default.Close, "Usuń", modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: BrowserViewModel = viewModel(), onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pobrane pliki", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (viewModel.downloads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Brak pobranych plików", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(viewModel.downloads, key = { it.downloadManagerId }) { record ->
                    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(record.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(record.status.value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (record.status.value == "Ukończono") {
                                IconButton(onClick = { viewModel.openDownloadedFile(record) }) {
                                    Icon(Icons.Default.OpenInNew, "Otwórz", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: BrowserViewModel = viewModel(), onBack: () -> Unit) {
    val currentTheme by viewModel.theme.collectAsState(initial = AppTheme.SYSTEM)
    val adBlockEnabled by viewModel.adBlockEnabledFlow.collectAsState(initial = true)
    val adBlockProEnabled by viewModel.adBlockProEnabledFlow.collectAsState(initial = true)
    val newTabGradientId by viewModel.newTabGradientIdFlow.collectAsState(initial = "microsoft_fluent")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Motyw", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = currentTheme == AppTheme.SYSTEM, onClick = { viewModel.setTheme(AppTheme.SYSTEM) }, label = { Text("Systemowy") })
                        FilterChip(selected = currentTheme == AppTheme.LIGHT, onClick = { viewModel.setTheme(AppTheme.LIGHT) }, label = { Text("Jasny") })
                        FilterChip(selected = currentTheme == AppTheme.DARK, onClick = { viewModel.setTheme(AppTheme.DARK) }, label = { Text("Ciemny") })
                    }
                }
            }

            ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Prywatność i bezpieczeństwo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Blokowanie reklam (AdBlock)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = adBlockEnabled, onCheckedChange = { viewModel.setAdBlockEnabled(it) })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Blokowanie PRO (Elementy CSS)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = adBlockProEnabled, onCheckedChange = { viewModel.setAdBlockProEnabled(it) }, enabled = adBlockEnabled)
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashLogScreen(log: String, onContinue: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF1B1B1B)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
                Text("Wystąpił nieoczekiwany błąd", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black).padding(12.dp).verticalScroll(rememberScrollState())) {
                    Text(log, fontSize = 11.sp, color = Color(0xFF4CB3FF))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { clipboardManager.setText(AnnotatedString(log)) }, shape = RoundedCornerShape(8.dp)) { Text("Kopiuj log") }
                    OutlinedButton(onClick = onContinue, shape = RoundedCornerShape(8.dp)) { Text("Kontynuuj") }
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
        var crashLog by mutableStateOf<String?>(if (crashFile.exists()) crashFile.readText() else null)
        setContent {
            if (crashLog != null) {
                CrashLogScreen(log = crashLog!!) {
                    crashFile.delete()
                    crashLog = null
                    recreate()
                }
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
                        composable("history") { HistoryScreen(viewModel = viewModel, onBack = { navController.popBackStack() }, onNavigate = { url -> viewModel.navigate(url) }) }
                        composable("bookmarks") { BookmarksScreen(viewModel = viewModel, onBack = { navController.popBackStack() }, onNavigate = { url -> viewModel.navigate(url) }) }
                        composable("downloads") { DownloadsScreen(viewModel = viewModel, onBack = { navController.popBackStack() }) }
                        composable("settings") { SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() }) }
                    }
                }
            }
        }
    }
}
