package app.ofiofi.docxdocx.ui.backstage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ofiofi.docxdocx.storage.DocTemplateId
import app.ofiofi.docxdocx.storage.RecentDocEntry
import app.ofiofi.docxdocx.storage.SafDocumentHelper
import app.ofiofi.docxdocx.storage.StorageProviderKind
import app.ofiofi.docxdocx.storage.WebDavClient
import app.ofiofi.docxdocx.storage.WebDavConfig
import app.ofiofi.docxdocx.storage.WebDavEntry
import app.ofiofi.docxdocx.ui.theme.OfficeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.word_mobile_ffi.MobileDocumentSession

enum class BackstageSection(val labelEsCl: String) {
    HOME("Inicio"),
    NEW("Nuevo"),
    OPEN("Abrir"),
    SAVE_AS("Guardar / Exportar"),
    WEBDAV("WebDAV"),
    INFO("Información"),
}

/**
 * Full Office Backstage (`Archivo`):
 * - Local storage & Google Drive via Android Storage Access Framework (SAF)
 * - Self-hosted / user-chosen WebDAV connector (Nextcloud, ownCloud, Synology, custom DAV)
 * - ZERO proprietary cloud: user stays 100% owner of every document.
 */
@Composable
fun BackstageArchivoOverlay(
    currentTitle: String,
    activeUri: Uri?,
    recentDocs: List<RecentDocEntry>,
    pageCount: UInt,
    wordCount: ULong,
    isDark: Boolean,
    isCompactWidth: Boolean,
    onDismiss: () -> Unit,
    onNewFromTemplate: (DocTemplateId) -> Unit,
    onOpenSafUri: (Uri) -> Unit,
    onSaveToSafUri: (Uri) -> Unit,
    onExportPdfToSafUri: (Uri) -> Unit,
    onReplaceSessionFromBytes: (filename: String, bytes: ByteArray, origin: String, provider: StorageProviderKind) -> Unit,
    onGetDocxBytes: () -> ByteArray,
) {
    val surfaces = if (isDark) OfficeTokens.DarkSurfaces else OfficeTokens.LightSurfaces
    val accent = if (isDark) OfficeTokens.AppAccents.Word.accentDark else OfficeTokens.AppAccents.Word.accentLight
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var section by remember { mutableStateOf(BackstageSection.HOME) }
    var davUrl by remember { mutableStateOf("https://") }
    var davUser by remember { mutableStateOf("") }
    var davPass by remember { mutableStateOf("") }
    var davEntries by remember { mutableStateOf<List<WebDavEntry>>(emptyList()) }
    var davStatus by remember { mutableStateOf<String?>(null) }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onOpenSafUri(uri)
            onDismiss()
        }
    }

    val saveAsDocxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(SafDocumentHelper.DOCX_MIME),
    ) { uri ->
        if (uri != null) {
            onSaveToSafUri(uri)
            onDismiss()
        }
    }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(SafDocumentHelper.PDF_MIME),
    ) { uri ->
        if (uri != null) {
            onExportPdfToSafUri(uri)
            onDismiss()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = surfaces.panel,
    ) {
        if (isCompactWidth) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accent)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "← Volver",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { onDismiss() },
                    )
                    Text("Archivo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.width(48.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaces.ribbonCard)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BackstageSection.entries.forEach { item ->
                        val selected = section == item
                        Text(
                            text = item.labelEsCl,
                            color = if (selected) accent else surfaces.textDim,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                                .clickable { section = item }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
                HorizontalDivider(color = surfaces.ribbonBorder)
                BackstageContentPane(
                    section = section,
                    currentTitle = currentTitle,
                    activeUri = activeUri,
                    recentDocs = recentDocs,
                    pageCount = pageCount,
                    wordCount = wordCount,
                    davUrl = davUrl,
                    davUser = davUser,
                    davPass = davPass,
                    davEntries = davEntries,
                    davStatus = davStatus,
                    surfaces = surfaces,
                    accent = accent,
                    onDavUrlChange = { davUrl = it },
                    onDavUserChange = { davUser = it },
                    onDavPassChange = { davPass = it },
                    onNewFromTemplate = {
                        onNewFromTemplate(it)
                        onDismiss()
                    },
                    onOpenSaf = { openLauncher.launch(SafDocumentHelper.OPEN_DOCUMENT_MIME_TYPES) },
                    onSaveExistingSaf = {
                        if (activeUri != null) {
                            onSaveToSafUri(activeUri)
                            onDismiss()
                        } else {
                            saveAsDocxLauncher.launch("$currentTitle.docx")
                        }
                    },
                    onSaveAsDocx = { saveAsDocxLauncher.launch("$currentTitle.docx") },
                    onExportPdf = { exportPdfLauncher.launch("$currentTitle.pdf") },
                    onDavConnectList = {
                        scope.launch {
                            davStatus = "Conectando a WebDAV..."
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    WebDavClient().listDirectory(WebDavConfig(davUrl, davUser, davPass))
                                }
                            }
                            result.onSuccess { entries ->
                                davEntries = entries
                                davStatus = "${entries.size} elementos encontrados"
                            }.onFailure { err ->
                                davStatus = err.message ?: "Error al conectar"
                            }
                        }
                    },
                    onDavDownloadEntry = { entry ->
                        scope.launch {
                            davStatus = "Descargando ${entry.displayName}..."
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    WebDavClient().downloadBytes(WebDavConfig(davUrl, davUser, davPass), entry.href)
                                }
                            }
                            result.onSuccess { bytes ->
                                onReplaceSessionFromBytes(
                                    entry.displayName,
                                    bytes,
                                    entry.href,
                                    StorageProviderKind.WEBDAV,
                                )
                                onDismiss()
                            }.onFailure { err ->
                                davStatus = err.message ?: "Error descargando archivo"
                            }
                        }
                    },
                    onDavUploadCurrent = {
                        scope.launch {
                            davStatus = "Subiendo $currentTitle.docx..."
                            val bytes = onGetDocxBytes()
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    WebDavClient().uploadBytes(
                                        WebDavConfig(davUrl, davUser, davPass),
                                        "${currentTitle.removeSuffix(".docx")}.docx",
                                        bytes,
                                    )
                                }
                            }
                            davStatus = result.fold(
                                onSuccess = { "Subido correctamente a WebDAV" },
                                onFailure = { it.message ?: "Error subiendo archivo" },
                            )
                        }
                    },
                    onOpenRecentSaf = { uriStr ->
                        runCatching { Uri.parse(uriStr) }.getOrNull()?.let {
                            onOpenSafUri(it)
                            onDismiss()
                        } ?: run {
                            davStatus = "Abre desde WebDAV"
                            section = BackstageSection.WEBDAV
                        }
                    },
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left blue/accent Word navigation rail (Tablet)
                Column(
                    modifier = Modifier
                        .width(210.dp)
                        .fillMaxHeight()
                        .background(accent)
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "← Volver",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                    Spacer(Modifier.height(6.dp))
                    BackstageSection.entries.forEach { item ->
                        val selected = section == item
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                                .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { section = item }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = item.labelEsCl,
                                color = Color.White,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "OfiOfi Free\nSin nube propietaria",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    BackstageContentPane(
                        section = section,
                        currentTitle = currentTitle,
                        activeUri = activeUri,
                        recentDocs = recentDocs,
                        pageCount = pageCount,
                        wordCount = wordCount,
                        davUrl = davUrl,
                        davUser = davUser,
                        davPass = davPass,
                        davEntries = davEntries,
                        davStatus = davStatus,
                        surfaces = surfaces,
                        accent = accent,
                        onDavUrlChange = { davUrl = it },
                        onDavUserChange = { davUser = it },
                        onDavPassChange = { davPass = it },
                        onNewFromTemplate = {
                            onNewFromTemplate(it)
                            onDismiss()
                        },
                        onOpenSaf = { openLauncher.launch(SafDocumentHelper.OPEN_DOCUMENT_MIME_TYPES) },
                        onSaveExistingSaf = {
                            if (activeUri != null) {
                                onSaveToSafUri(activeUri)
                                onDismiss()
                            } else {
                                saveAsDocxLauncher.launch("$currentTitle.docx")
                            }
                        },
                        onSaveAsDocx = { saveAsDocxLauncher.launch("$currentTitle.docx") },
                        onExportPdf = { exportPdfLauncher.launch("$currentTitle.pdf") },
                        onDavConnectList = {
                            scope.launch {
                                davStatus = "Conectando a WebDAV..."
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        WebDavClient().listDirectory(WebDavConfig(davUrl, davUser, davPass))
                                    }
                                }
                                result.onSuccess { entries ->
                                    davEntries = entries
                                    davStatus = "${entries.size} elementos encontrados"
                                }.onFailure { err ->
                                    davStatus = err.message ?: "Error al conectar"
                                }
                            }
                        },
                        onDavDownloadEntry = { entry ->
                            scope.launch {
                                davStatus = "Descargando ${entry.displayName}..."
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        WebDavClient().downloadBytes(WebDavConfig(davUrl, davUser, davPass), entry.href)
                                    }
                                }
                                result.onSuccess { bytes ->
                                    onReplaceSessionFromBytes(
                                        entry.displayName,
                                        bytes,
                                        entry.href,
                                        StorageProviderKind.WEBDAV,
                                    )
                                    onDismiss()
                                }.onFailure { err ->
                                    davStatus = err.message ?: "Error descargando archivo"
                                }
                            }
                        },
                        onDavUploadCurrent = {
                            scope.launch {
                                davStatus = "Subiendo $currentTitle.docx..."
                                val bytes = onGetDocxBytes()
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        WebDavClient().uploadBytes(
                                            WebDavConfig(davUrl, davUser, davPass),
                                            "${currentTitle.removeSuffix(".docx")}.docx",
                                            bytes,
                                        )
                                    }
                                }
                                davStatus = result.fold(
                                    onSuccess = { "Subido correctamente a WebDAV" },
                                    onFailure = { it.message ?: "Error subiendo archivo" },
                                )
                            }
                        },
                        onOpenRecentSaf = { uriStr ->
                            runCatching { Uri.parse(uriStr) }.getOrNull()?.let {
                                onOpenSafUri(it)
                                onDismiss()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackstageContentPane(
    section: BackstageSection,
    currentTitle: String,
    activeUri: Uri?,
    recentDocs: List<RecentDocEntry>,
    pageCount: UInt,
    wordCount: ULong,
    davUrl: String,
    davUser: String,
    davPass: String,
    davEntries: List<WebDavEntry>,
    davStatus: String?,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onDavUrlChange: (String) -> Unit,
    onDavUserChange: (String) -> Unit,
    onDavPassChange: (String) -> Unit,
    onNewFromTemplate: (DocTemplateId) -> Unit,
    onOpenSaf: () -> Unit,
    onSaveExistingSaf: () -> Unit,
    onSaveAsDocx: () -> Unit,
    onExportPdf: () -> Unit,
    onDavConnectList: () -> Unit,
    onDavDownloadEntry: (WebDavEntry) -> Unit,
    onDavUploadCurrent: () -> Unit,
    onOpenRecentSaf: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (section) {
            BackstageSection.HOME -> {
                item {
                    Text("Plantillas rápidas", color = surfaces.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                items(DocTemplateId.entries) { tpl ->
                    TemplateRow(tpl, surfaces, accent) { onNewFromTemplate(tpl) }
                }
                item {
                    Spacer(Modifier.height(6.dp))
                    Text("Documentos recientes", color = surfaces.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (recentDocs.isEmpty()) {
                    item {
                        Text("Aún no has abierto documentos desde almacenamiento local, Google Drive (SAF) o WebDAV.", color = surfaces.textDim, fontSize = 13.sp)
                    }
                } else {
                    items(recentDocs) { entry ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.ribbonCard))
                                .clickable {
                                    if (entry.provider == StorageProviderKind.LOCAL_OR_DRIVE_SAF) {
                                        onOpenRecentSaf(entry.uriOrUrl)
                                    }
                                },
                            shape = RoundedCornerShape(OfficeTokens.Radii.ribbonCard),
                            color = surfaces.ribbonCard,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(entry.title, color = surfaces.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    text = "${entry.provider.name} • ${entry.uriOrUrl}",
                                    color = surfaces.textDim,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            BackstageSection.NEW -> {
                item {
                    Text("Crear documento nuevo", color = surfaces.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                items(DocTemplateId.entries) { tpl ->
                    TemplateRow(tpl, surfaces, accent) { onNewFromTemplate(tpl) }
                }
            }
            BackstageSection.OPEN -> {
                item {
                    Text("Abrir documento (.docx, .doc, .odt, .rtf)", color = surfaces.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenSaf,
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                    ) {
                        Text("Explorar Dispositivo / Google Drive (SAF)", color = Color.White)
                    }
                }
            }
            BackstageSection.SAVE_AS -> {
                item {
                    Text("Guardar o Exportar ($currentTitle)", color = surfaces.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (activeUri != null) {
                            Button(onClick = onSaveExistingSaf, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                                Text("Guardar cambios en archivo actual", color = Color.White)
                            }
                        }
                        Button(onClick = onSaveAsDocx, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                            Text("Guardar como .docx (Local / Google Drive)", color = Color.White)
                        }
                        Button(onClick = onExportPdf, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                            Text("Exportar como PDF (.pdf)", color = Color.White)
                        }
                        Button(onClick = onDavUploadCurrent, colors = ButtonDefaults.buttonColors(containerColor = surfaces.panel)) {
                            Text("Subir a servidor WebDAV configurado", color = accent)
                        }
                    }
                }
            }
            BackstageSection.WEBDAV -> {
                item {
                    Text("Conector WebDAV elegido por el usuario", color = surfaces.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Compatible con Nextcloud, ownCloud, Synology Drive y servidores WebDAV HTTPS. No usamos nube propia.", color = surfaces.textDim, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    DavInputField("URL base WebDAV (ej. https://nube.ejemplo.cl/remote.php/dav/files/user/)", davUrl, surfaces, accent, onDavUrlChange)
                    Spacer(Modifier.height(6.dp))
                    DavInputField("Usuario", davUser, surfaces, accent, onDavUserChange)
                    Spacer(Modifier.height(6.dp))
                    DavInputField("Contraseña / Token de aplicación", davPass, surfaces, accent, onDavPassChange)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDavConnectList, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                            Text("Conectar y listar", color = Color.White)
                        }
                        Button(onClick = onDavUploadCurrent, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                            Text("Subir $currentTitle.docx", color = Color.White)
                        }
                    }
                    if (davStatus != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(davStatus, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                items(davEntries) { entry ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.button))
                            .clickable(enabled = !entry.isDirectory) { onDavDownloadEntry(entry) },
                        color = surfaces.ribbonCard,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = (if (entry.isDirectory) "📁 " else "📄 ") + entry.displayName,
                                color = surfaces.text,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = entry.contentLengthBytes?.let { "${it / 1024} KB" } ?: "",
                                color = surfaces.textDim,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
            BackstageSection.INFO -> {
                item {
                    Text("Información del documento", color = surfaces.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Título: $currentTitle", color = surfaces.text, fontSize = 14.sp)
                    Text("Páginas: $pageCount", color = surfaces.text, fontSize = 14.sp)
                    Text("Palabras: $wordCount", color = surfaces.text, fontSize = 14.sp)
                    Text("Región e idioma: Español (Chile) es-CL", color = surfaces.text, fontSize = 14.sp)
                    Text("Almacenamiento: Local / Google Drive (SAF) / WebDAV propio", color = surfaces.textDim, fontSize = 13.sp)
                    Text("Suite OfiOfi 100 % gratuita • Sin nube propietaria", color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(
    tpl: DocTemplateId,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.ribbonCard))
            .clickable { onClick() },
        shape = RoundedCornerShape(OfficeTokens.Radii.ribbonCard),
        color = surfaces.ribbonCard,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(tpl.titleEsCl, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(tpl.subtitleEsCl, color = surfaces.textDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DavInputField(
    label: String,
    value: String,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onValueChange: (String) -> Unit,
) {
    Column {
        Text(label, color = surfaces.textDim, fontSize = 11.sp)
        Spacer(Modifier.height(3.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.button)),
            color = surfaces.ribbonCard,
            shape = RoundedCornerShape(OfficeTokens.Radii.button),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = surfaces.text, fontSize = 13.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}
