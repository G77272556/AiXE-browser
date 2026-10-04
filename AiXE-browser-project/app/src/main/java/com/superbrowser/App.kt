package com.example.aixe

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

// ==========================================
// ENTRY POINT
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiXETheme {
                SuperBrowserApp()
            }
        }
    }
}

// ==========================================
// THEME
// ==========================================
@Composable
fun AiXETheme(content: @Composable () -> Unit) {
    val colorScheme = dynamicDarkColorScheme(androidx.compose.ui.platform.LocalContext.current)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

// ==========================================
// MODELS & VIEWMODEL
// ==========================================
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var url: String = "",
    var title: String = "Nowa karta",
    var progress: Float = 0f,
    var isLoading: Boolean = false,
    var webView: WebView? = null,
    val isIncognito: Boolean = false
)

class BrowserViewModel : ViewModel() {
    private val _tabs = MutableStateFlow<List<BrowserTab>>(listOf(BrowserTab()))
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _currentTabId = MutableStateFlow<String?>(_tabs.value.first().id)
    val currentTabId: StateFlow<String?> = _currentTabId.asStateFlow()

    private val _isTabManagerOpen = MutableStateFlow(false)
    val isTabManagerOpen: StateFlow<Boolean> = _isTabManagerOpen.asStateFlow()

    var adsBlockedCount by mutableIntStateOf(1420)
    var trackersBlockedCount by mutableIntStateOf(531)

    fun getCurrentTab(): BrowserTab? {
        val currentId = _currentTabId.value ?: return _tabs.value.firstOrNull()
        return _tabs.value.find { it.id == currentId } ?: _tabs.value.firstOrNull()
    }

    fun addTab(url: String = "", isIncognito: Boolean = false) {
        val newTab = BrowserTab(url = url, isIncognito = isIncognito)
        _tabs.update { it + newTab }
        _currentTabId.value = newTab.id
        _isTabManagerOpen.value = false
    }

    fun closeTab(tabId: String) {
        val currentList = _tabs.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == tabId }
        if (index != -1) {
            currentList[index].webView?.destroy()
            currentList.removeAt(index)
            _tabs.value = currentList
            
            if (currentList.isEmpty()) {
                addTab()
            } else if (_currentTabId.value == tabId) {
                _currentTabId.value = currentList.getOrNull(index - 1)?.id ?: currentList.first().id
            }
        }
    }

    fun switchTab(tabId: String) {
        _currentTabId.value = tabId
        _isTabManagerOpen.value = false
    }

    fun toggleTabManager() {
        _isTabManagerOpen.value = !_isTabManagerOpen.value
    }
    
    fun updateTabInfo(tabId: String, update: (BrowserTab) -> BrowserTab) {
        _tabs.update { list ->
            list.map { if (it.id == tabId) update(it) else it }
        }
    }
    
    fun loadUrl(url: String) {
        val current = getCurrentTab() ?: return
        var finalUrl = url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            finalUrl = if (url.contains(".") && !url.contains(" ")) {
                "https://$url"
            } else {
                "https://www.google.com/search?q=${Uri.encode(url)}"
            }
        }
        current.webView?.loadUrl(finalUrl) ?: run {
            updateTabInfo(current.id) { it.copy(url = finalUrl) }
        }
    }
}

// ==========================================
// UI: MAIN SCAFFOLD
// ==========================================
@Composable
fun SuperBrowserApp(viewModel: BrowserViewModel = viewModel()) {
    val tabs by viewModel.tabs.collectAsState()
    val currentTabId by viewModel.currentTabId.collectAsState()
    val isTabManagerOpen by viewModel.isTabManagerOpen.collectAsState()
    val currentTab = viewModel.getCurrentTab()

    BackHandler(enabled = true) {
        if (isTabManagerOpen) {
            viewModel.toggleTabManager()
        } else if (currentTab?.webView?.canGoBack() == true) {
            currentTab.webView?.goBack()
        }
    }

    Scaffold(
        bottomBar = {
            if (!isTabManagerOpen && currentTab != null) {
                BottomAddressBar(
                    currentTab = currentTab,
                    tabCount = tabs.size,
                    onUrlSubmitted = { viewModel.loadUrl(it) },
                    onTabManagerClick = { viewModel.toggleTabManager() },
                    onHomeClick = { 
                        currentTab.webView?.loadUrl("about:blank")
                        viewModel.updateTabInfo(currentTab.id) { it.copy(url = "") } 
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentTab != null) {
                if (currentTab.url.isEmpty() || currentTab.url == "about:blank") {
                    BentoHomeScreen(
                        viewModel = viewModel,
                        onUrlClick = { viewModel.loadUrl(it) }
                    )
                } else {
                    BrowserWebView(
                        tab = currentTab,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            AnimatedVisibility(
                visible = isTabManagerOpen,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                TabManagerScreen(
                    tabs = tabs,
                    currentTabId = currentTabId,
                    onTabSelected = { viewModel.switchTab(it) },
                    onTabClosed = { viewModel.closeTab(it) },
                    onNewTab = { viewModel.addTab() },
                    onCloseManager = { viewModel.toggleTabManager() }
                )
            }
        }
    }
}

// ==========================================
// UI: BOTTOM ADDRESS BAR
// ==========================================
@Composable
fun BottomAddressBar(
    currentTab: BrowserTab,
    tabCount: Int,
    onUrlSubmitted: (String) -> Unit,
    onTabManagerClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    var text by remember(currentTab.id, currentTab.url) { mutableStateOf(currentTab.url) }
    
    Column {
        if (currentTab.isLoading) {
            LinearProgressIndicator(
                progress = { currentTab.progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
        
        Surface(
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                IconButton(onClick = onHomeClick) {
                    Icon(Icons.Rounded.Home, contentDescription = "Home")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    shape = CircleShape,
                    placeholder = { Text("Szukaj lub wpisz adres URL") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onUrlSubmitted(text) }),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    leadingIcon = {
                        if (currentTab.url.startsWith("https")) {
                            Icon(Icons.Filled.Lock, contentDescription = "Secure", modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                        }
                    }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabManagerClick() }
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(
                        text = tabCount.toString(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

// ==========================================
// UI: BENTO GRID HOME SCREEN
// ==========================================
@Composable
fun BentoHomeScreen(viewModel: BrowserViewModel, onUrlClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Witaj w AiXE",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Prywatnie, szybko, nowocześnie.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BentoCard(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "${viewModel.adsBlockedCount}", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                    Text(text = "Zablokowane\nreklamy", style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
                }
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BentoCard(modifier = Modifier.height(82.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "${viewModel.trackersBlockedCount}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Text(text = "Skrypty śledzące", style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                BentoCard(
                    modifier = Modifier
                        .height(82.dp)
                        .clickable { viewModel.addTab(isIncognito = true) }, 
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Tryb\nPrywatny", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Ulubione", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val bookmarks = listOf(
                "google.com" to Icons.Filled.Search,
                "youtube.com" to Icons.Filled.PlayArrow,
                "github.com" to Icons.Filled.Build,
                "reddit.com" to Icons.Filled.Face
            )
            items(bookmarks) { (url, icon) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onUrlClick(url) }) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Icon(icon, contentDescription = url, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(url.split(".")[0].replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun BentoCard(modifier: Modifier = Modifier, color: Color, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = color,
        modifier = modifier.fillMaxWidth()
    ) {
        content()
    }
}

// ==========================================
// UI: TAB MANAGER SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabManagerScreen(
    tabs: List<BrowserTab>,
    currentTabId: String?,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseManager: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Karty") },
                navigationIcon = {
                    IconButton(onClick = onCloseManager) {
                        Icon(Icons.Filled.ArrowBack, "Powrót")
                    }
                },
                actions = {
                    IconButton(onClick = onNewTab) {
                        Icon(Icons.Filled.Add, "Nowa Karta")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            items(tabs) { tab ->
                val isSelected = tab.id == currentTabId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .clickable { onTabSelected(tab.id) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (tab.isIncognito) MaterialTheme.colorScheme.errorContainer 
                                         else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tab.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onTabClosed(tab.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, "Zamknij")
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            if (tab.url.isEmpty() || tab.url == "about:blank") {
                                Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))
                            } else {
                                Text(tab.url, style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// CORE: WEBVIEW INTEGRATION & ADBLOCK
// ==========================================
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebView(
    tab: BrowserTab,
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        viewModel.updateTabInfo(tab.id) { 
                            it.copy(url = url ?: "", isLoading = true)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        viewModel.updateTabInfo(tab.id) { 
                            it.copy(url = url ?: "", title = view?.title ?: "Strona", isLoading = false)
                        }
                        view?.evaluateJavascript(
                            "javascript:(function() { " +
                            "var elements = document.querySelectorAll('.ad-container, .adsbygoogle, [id^=google_ads_iframe]');" +
                            "for (var i = 0; i < elements.length; i++) { elements[i].style.display = 'none'; }" +
                            "})()", null
                        )
                    }
                    
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: ""
                        val blockedDomains = listOf("ads.google.com", "doubleclick.net", "analytics", "tracker")
                        
                        if (blockedDomains.any { url.contains(it) }) {
                            viewModel.adsBlockedCount++
                            return WebResourceResponse("text/plain", "UTF-8", null)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        viewModel.updateTabInfo(tab.id) { 
                            it.copy(progress = newProgress / 100f)
                        }
                    }
                    
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        viewModel.updateTabInfo(tab.id) { 
                            it.copy(title = title ?: "")
                        }
                    }
                }
                
                tab.webView = this
                
                if (tab.url.isNotEmpty() && tab.url != "about:blank") {
                    loadUrl(tab.url)
                }
            }
        },
        update = { webView ->
            if (tab.url.isNotEmpty() && tab.url != webView.url && tab.url != "about:blank") {
                webView.loadUrl(tab.url)
            }
        },
        modifier = modifier
    )
}
