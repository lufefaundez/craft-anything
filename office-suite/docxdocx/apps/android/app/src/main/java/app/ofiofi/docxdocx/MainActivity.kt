package app.ofiofi.docxdocx

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ofiofi.docxdocx.ui.theme.OfficeTokens
import uniffi.word_mobile_ffi.MobileDocumentSession
import uniffi.word_mobile_ffi.TileRequest

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DocxAssetInitializer.ensureInitialized(applicationContext)
        val session = MobileDocumentSession.newBlank("es-CL")
        val catalog = RibbonCatalogLoader.loadFromAssets(applicationContext)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val isDark = isSystemInDarkTheme()
            DocxWorkspaceScreen(
                session = session,
                catalog = catalog,
                isExpandedWidth = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact,
                isDark = isDark,
            )
        }
    }
}

@SuppressLint("DiscouragedApi")
@Composable
fun DocxWorkspaceScreen(
    session: MobileDocumentSession,
    catalog: RibbonSchemaCatalog,
    isExpandedWidth: Boolean,
    isDark: Boolean,
) {
    val surfaces = if (isDark) OfficeTokens.DarkSurfaces else OfficeTokens.LightSurfaces
    val accent = if (isDark) OfficeTokens.AppAccents.Word.accentDark else OfficeTokens.AppAccents.Word.accentLight
    var activeTabIndex by remember { mutableIntStateOf(0) }
    var docRevision by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("Página 1 de ${session.pageCount()}  •  ${session.wordCount()} palabras") }

    val renderedPage = remember(docRevision, isDark) {
        val pageSize = session.pageSizePt(0u)
        val widthPx = 794u
        val scale = widthPx.toFloat() / pageSize.widthPt
        val heightPx = (pageSize.heightPt * scale).toUInt().coerceAtLeast(1u)
        val rgba = session.renderTile(
            TileRequest(
                pageIndex = 0u,
                dpiScale = scale,
                tileXPx = 0u,
                tileYPx = 0u,
                tileWPx = widthPx,
                tileHPx = heightPx,
                darkPage = false,
            ),
        )
        rgbaBytesToBitmap(rgba, widthPx.toInt(), heightPx.toInt())
    }

    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaces.canvas),
    ) {
        // Title Bar + QAT (38 dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(surfaces.titleBar)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DocxDocx", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.width(12.dp))
                Text("Documento1", color = surfaces.text, fontSize = 13.sp)
            }
            Text("OfiOfi Free", color = surfaces.textDim, fontSize = 12.sp)
        }

        // Ribbon Tab Strip (32 dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(surfaces.tabStrip)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            catalog.coreTabs.forEachIndexed { idx, tab ->
                val selected = idx == activeTabIndex
                Column(
                    modifier = Modifier
                        .clickable { activeTabIndex = idx }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tab.labelEsCl,
                        color = if (selected) accent else surfaces.text,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                    )
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .width(28.dp)
                                .height(2.5.dp)
                                .background(accent),
                        )
                    }
                }
            }
        }

        // Tablet/Foldable Ribbon Card (or compact quick bar preview on phone)
        val currentTab = catalog.coreTabs.getOrNull(activeTabIndex)
        if (currentTab != null && isExpandedWidth) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .height(92.dp)
                    .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.ribbonCard)),
                shape = RoundedCornerShape(OfficeTokens.Radii.ribbonCard),
                color = surfaces.ribbonCard,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    currentTab.groups.forEach { group ->
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                group.controls.take(6).forEach { control ->
                                    val resId = context.resources.getIdentifier(
                                        control.icon.lowercase(),
                                        "drawable",
                                        context.packageName,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(OfficeTokens.TouchTargets.tabletCompactButton)
                                            .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                                            .clickable {
                                                runCatching {
                                                    session.executeCommand(control.commandId, "{}")
                                                    docRevision += 1
                                                    statusMessage = "Página 1 de ${session.pageCount()}  •  ${session.wordCount()} palabras"
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (resId != 0) {
                                            Icon(
                                                painter = painterResource(resId),
                                                contentDescription = control.labelEsCl,
                                                tint = Color.Unspecified,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            Text(group.labelEsCl, color = surfaces.textDim, fontSize = 10.sp)
                        }
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(1.dp)
                                .height(64.dp)
                                .background(surfaces.ribbonBorder),
                        )
                    }
                }
            }
        }

        // Document Canvas Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (renderedPage != null) {
                Image(
                    bitmap = renderedPage.asImageBitmap(),
                    contentDescription = "Documento actual",
                    modifier = Modifier
                        .border(1.dp, surfaces.ribbonBorder)
                        .background(surfaces.page),
                )
            }
        }

        HorizontalDivider(color = surfaces.ribbonBorder)
        // Status Bar (26 dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(surfaces.statusBar)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(statusMessage, color = surfaces.textDim, fontSize = 11.sp)
            Text("Español (Chile)  •  100 %", color = surfaces.textDim, fontSize = 11.sp)
        }
    }
}

private fun rgbaBytesToBitmap(rgba: ByteArray, width: Int, height: Int): Bitmap? {
    if (width <= 0 || height <= 0 || rgba.size != width * height * 4) return null
    val pixels = IntArray(width * height)
    var i = 0
    var p = 0
    while (p < pixels.size) {
        val r = rgba[i].toInt() and 0xFF
        val g = rgba[i + 1].toInt() and 0xFF
        val b = rgba[i + 2].toInt() and 0xFF
        val a = rgba[i + 3].toInt() and 0xFF
        pixels[p] = (a shl 24) or (r shl 16) or (g shl 8) or b
        i += 4
        p += 1
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
