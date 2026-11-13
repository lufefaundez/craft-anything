package app.ofiofi.docxdocx.ui.shell

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ofiofi.docxdocx.RibbonControlSpec
import app.ofiofi.docxdocx.RibbonSchemaCatalog
import app.ofiofi.docxdocx.RibbonTabSpec
import app.ofiofi.docxdocx.ui.ribbon.RibbonAdvancedPresets
import app.ofiofi.docxdocx.ui.ribbon.RibbonCorePresets
import app.ofiofi.docxdocx.ui.ribbon.RibbonInteractiveControl
import app.ofiofi.docxdocx.ui.theme.OfficeTokens

/**
 * Adaptive chrome for DocxDocx:
 * - Tablet / Foldable (`Expanded`): Full Office 16.115 TitleBar+QAT + 9+Context TabStrip + 92dp White Floating Ribbon Card.
 * - Smartphone (`Compact`): Compact Header + Keyboard Accessory QuickBar + Full 9-Tab Bottom Sheet with Command Search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocxAdaptiveChrome(
    formFactor: ShellFormFactor,
    catalog: RibbonSchemaCatalog,
    documentTitle: String,
    activeToggleIds: Set<String>,
    activeContexts: Set<String>,
    formatSummary: RibbonFormatSummary = RibbonFormatSummary.Default,
    availableFonts: List<String> = RibbonCorePresets.fonts,
    shellMode: ShellMode = if (formFactor == ShellFormFactor.TABLET_EXPANDED_RIBBON) ShellMode.TABLET else ShellMode.PHONE,
    isDexHardware: Boolean = false,
    commentCount: Int = 0,
    showCommentsPane: Boolean = false,
    canUndo: Boolean,
    canRedo: Boolean,
    isDark: Boolean,
    onExecuteCommand: (commandId: String, paramsJson: String) -> Unit,
    onOpenBackstage: () -> Unit,
    onToggleCommentsPane: () -> Unit = {},
    onToggleDesktopMode: () -> Unit = {},
    canvasContent: @Composable (Modifier) -> Unit,
    statusBarContent: @Composable () -> Unit,
) {
    val surfaces = if (isDark) OfficeTokens.DarkSurfaces else OfficeTokens.LightSurfaces
    val accent = if (isDark) OfficeTokens.AppAccents.Word.accentDark else OfficeTokens.AppAccents.Word.accentLight
    val checkedBg = if (isDark) OfficeTokens.AppAccents.Word.checkedBgDark else OfficeTokens.AppAccents.Word.checkedBgLight

    val tabs = remember(catalog, activeContexts) {
        AdaptiveShellModel.visibleTabs(catalog, activeContexts)
    }
    var activeTabIndex by remember { mutableIntStateOf(0) }
    val safeTabIndex = activeTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val currentTab = tabs.getOrNull(safeTabIndex)

    var showPhoneSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val expandedChrome = formFactor == ShellFormFactor.TABLET_EXPANDED_RIBBON || shellMode != ShellMode.PHONE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaces.canvas),
    ) {
        if (expandedChrome) {
            TabletTitleAndQatBar(
                documentTitle = documentTitle,
                qatControls = catalog.qat,
                shellMode = shellMode,
                isDexHardware = isDexHardware,
                commentCount = commentCount,
                showCommentsPane = showCommentsPane,
                canUndo = canUndo,
                canRedo = canRedo,
                surfaces = surfaces,
                accent = accent,
                onOpenBackstage = onOpenBackstage,
                onToggleCommentsPane = onToggleCommentsPane,
                onToggleDesktopMode = onToggleDesktopMode,
                onExecuteCommand = onExecuteCommand,
            )
            RibbonTabStripBar(
                tabs = tabs,
                selectedIndex = safeTabIndex,
                activeContexts = activeContexts,
                surfaces = surfaces,
                accent = accent,
                onOpenBackstage = onOpenBackstage,
                onSelectTab = { activeTabIndex = it },
            )
            if (currentTab != null) {
                TabletFloatingRibbonCard(
                    tab = currentTab,
                    activeToggleIds = activeToggleIds,
                    formatSummary = formatSummary,
                    availableFonts = availableFonts,
                    surfaces = surfaces,
                    accent = accent,
                    checkedBg = checkedBg,
                    onExecuteCommand = onExecuteCommand,
                )
            }
            canvasContent(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            HorizontalDivider(color = surfaces.ribbonBorder)
            statusBarContent()
        } else {
            SmartphoneTopHeader(
                documentTitle = documentTitle,
                canUndo = canUndo,
                canRedo = canRedo,
                surfaces = surfaces,
                accent = accent,
                onOpenBackstage = onOpenBackstage,
                onUndo = { onExecuteCommand("edit.undo", "{}") },
                onRedo = { onExecuteCommand("edit.redo", "{}") },
                onOpenSearchRibbon = { showPhoneSheet = true },
                onToggleDesktopMode = onToggleDesktopMode,
            )
            canvasContent(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            HorizontalDivider(color = surfaces.ribbonBorder)
            SmartphoneQuickBar(
                pinnedControls = catalog.smartphoneQuickBar,
                activeToggleIds = activeToggleIds,
                surfaces = surfaces,
                accent = accent,
                checkedBg = checkedBg,
                onExecuteCommand = onExecuteCommand,
                onExpandRibbonSheet = { showPhoneSheet = true },
            )
            statusBarContent()
        }
    }

    if (!expandedChrome && showPhoneSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPhoneSheet = false },
            sheetState = sheetState,
            containerColor = surfaces.ribbonCard,
            shape = RoundedCornerShape(
                topStart = OfficeTokens.Radii.sheetTop,
                topEnd = OfficeTokens.Radii.sheetTop,
            ),
        ) {
            PhoneRibbonSheetContent(
                catalog = catalog,
                tabs = tabs,
                selectedIndex = safeTabIndex,
                searchQuery = searchQuery,
                activeToggleIds = activeToggleIds,
                formatSummary = formatSummary,
                availableFonts = availableFonts,
                surfaces = surfaces,
                accent = accent,
                checkedBg = checkedBg,
                onQueryChange = { searchQuery = it },
                onSelectTab = {
                    searchQuery = ""
                    activeTabIndex = it
                },
                onExecuteCommand = { cmd, params ->
                    onExecuteCommand(cmd, params)
                    showPhoneSheet = false
                },
            )
        }
    }
}

@Composable
private fun TabletTitleAndQatBar(
    documentTitle: String,
    qatControls: List<RibbonControlSpec>,
    shellMode: ShellMode,
    isDexHardware: Boolean,
    commentCount: Int,
    showCommentsPane: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onOpenBackstage: () -> Unit,
    onToggleCommentsPane: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onExecuteCommand: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(surfaces.titleBar)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Text(
                text = "DocxDocx",
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 6.dp),
            )
            qatControls.forEach { control ->
                val enabled = when (control.commandId) {
                    "edit.undo" -> canUndo
                    "edit.redo" -> canRedo
                    else -> true
                }
                RibbonIconButton(
                    control = control,
                    enabled = enabled,
                    checked = false,
                    surfaces = surfaces,
                    checkedBg = Color.Transparent,
                    onClick = {
                        if (control.commandId == "ui.openBackstage") {
                            onOpenBackstage()
                        } else {
                            onExecuteCommand(control.commandId, "{}")
                        }
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = documentTitle,
                color = surfaces.text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TitleActionChip(
                label = "Buscar",
                active = false,
                surfaces = surfaces,
                accent = accent,
                onClick = { onExecuteCommand("edit.find", "{}") },
            )
            TitleActionChip(
                label = if (commentCount > 0) "Comentarios ($commentCount)" else "Comentarios",
                active = showCommentsPane,
                surfaces = surfaces,
                accent = accent,
                onClick = onToggleCommentsPane,
            )
            TitleActionChip(
                label = "Imprimir",
                active = false,
                surfaces = surfaces,
                accent = accent,
                onClick = { onExecuteCommand("file.print", "{}") },
            )
            TitleActionChip(
                label = when {
                    shellMode == ShellMode.DESKTOP && isDexHardware -> "DeX / PC ✓"
                    shellMode == ShellMode.DESKTOP -> "Escritorio ✓"
                    else -> "Modo PC"
                },
                active = shellMode == ShellMode.DESKTOP,
                surfaces = surfaces,
                accent = accent,
                onClick = onToggleDesktopMode,
            )
        }
    }
}

@Composable
private fun TitleActionChip(
    label: String,
    active: Boolean,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(OfficeTokens.Radii.pill))
            .clickable { onClick() },
        color = if (active) accent.copy(alpha = 0.16f) else surfaces.panel,
        shape = RoundedCornerShape(OfficeTokens.Radii.pill),
    ) {
        Text(
            text = label,
            color = if (active) accent else surfaces.textDim,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun RibbonTabStripBar(
    tabs: List<RibbonTabSpec>,
    selectedIndex: Int,
    activeContexts: Set<String>,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onOpenBackstage: () -> Unit,
    onSelectTab: (Int) -> Unit,
) {
    val contextBanner = RibbonAdvancedPresets.contextualBannerLabel(activeContexts)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(surfaces.tabStrip)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .padding(end = 6.dp)
                .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                .clickable { onOpenBackstage() },
            color = accent,
            shape = RoundedCornerShape(OfficeTokens.Radii.button),
        ) {
            Text(
                text = "Archivo",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        tabs.forEachIndexed { idx, tab ->
            val selected = idx == selectedIndex
            val isContextual = tab.contextualTrigger != null
            Column(
                modifier = Modifier
                    .clickable { onSelectTab(idx) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = tab.labelEsCl,
                    color = when {
                        selected -> accent
                        isContextual -> Color(0xFFB7472A)
                        else -> surfaces.text
                    },
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp,
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .width(26.dp)
                            .height(2.5.dp)
                            .background(if (isContextual) Color(0xFFB7472A) else accent),
                    )
                }
            }
        }
        if (contextBanner != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = contextBanner,
                color = Color(0xFFB7472A),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(Color(0x1AB7472A), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun TabletFloatingRibbonCard(
    tab: RibbonTabSpec,
    activeToggleIds: Set<String>,
    formatSummary: RibbonFormatSummary,
    availableFonts: List<String>,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    checkedBg: Color,
    onExecuteCommand: (String, String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(92.dp)
            .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.ribbonCard)),
        shape = RoundedCornerShape(OfficeTokens.Radii.ribbonCard),
        color = surfaces.ribbonCard,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tab.groups.forEach { group ->
                val slots = remember(group) { AdaptiveShellModel.layoutGroupColumns(group) }
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        slots.forEach { slot ->
                            when (slot) {
                                is RibbonGroupSlot.Tall -> {
                                    RibbonControlWithSubmenu(
                                        control = slot.control,
                                        tall = true,
                                        checked = activeToggleIds.contains(slot.control.commandId),
                                        formatSummary = formatSummary,
                                        availableFonts = availableFonts,
                                        surfaces = surfaces,
                                        accent = accent,
                                        checkedBg = checkedBg,
                                        onExecuteCommand = onExecuteCommand,
                                    )
                                }
                                is RibbonGroupSlot.StackedPair -> {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        RibbonControlWithSubmenu(
                                            control = slot.top,
                                            tall = false,
                                            checked = activeToggleIds.contains(slot.top.commandId),
                                            formatSummary = formatSummary,
                                            availableFonts = availableFonts,
                                            surfaces = surfaces,
                                            accent = accent,
                                            checkedBg = checkedBg,
                                            onExecuteCommand = onExecuteCommand,
                                        )
                                        if (slot.bottom != null) {
                                            RibbonControlWithSubmenu(
                                                control = slot.bottom,
                                                tall = false,
                                                checked = activeToggleIds.contains(slot.bottom.commandId),
                                                formatSummary = formatSummary,
                                                availableFonts = availableFonts,
                                                surfaces = surfaces,
                                                accent = accent,
                                                checkedBg = checkedBg,
                                                onExecuteCommand = onExecuteCommand,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = group.labelEsCl,
                            color = surfaces.textDim,
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                        if (group.dialogCommandId != null) {
                            Text(
                                text = "↘",
                                color = accent,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .clickable { onExecuteCommand(group.dialogCommandId, "{}") }
                                    .padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(68.dp)
                        .background(surfaces.ribbonBorder),
                )
            }
        }
    }
}

@Composable
private fun RibbonControlWithSubmenu(
    control: RibbonControlSpec,
    tall: Boolean,
    checked: Boolean,
    formatSummary: RibbonFormatSummary,
    availableFonts: List<String>,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    checkedBg: Color,
    onExecuteCommand: (String, String) -> Unit,
) {
    RibbonInteractiveControl(
        control = control,
        formatSummary = formatSummary,
        surfaces = surfaces,
        accent = accent,
        checkedBg = checkedBg,
        availableFonts = availableFonts,
        onExecuteCommand = onExecuteCommand,
        iconContent = { iconName, contentDescription, sizeDp ->
            ControlVectorIcon(iconName = iconName, contentDescription = contentDescription, sizeDp = sizeDp)
        },
        fallbackButton = {
            var expanded by remember(control.commandId) { mutableStateOf(false) }

            Box {
                if (tall) {
                    Column(
                        modifier = Modifier
                            .height(OfficeTokens.TouchTargets.tabletLargeButton)
                            .width(56.dp)
                            .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                            .background(if (checked) checkedBg else Color.Transparent)
                            .clickable {
                                if (control.submenu.isNotEmpty()) {
                                    expanded = true
                                } else {
                                    onExecuteCommand(control.commandId, "{}")
                                }
                            }
                            .padding(horizontal = 3.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ControlVectorIcon(iconName = control.icon, contentDescription = control.labelEsCl, sizeDp = 22)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = control.labelEsCl,
                            color = if (checked) accent else surfaces.text,
                            fontSize = 9.sp,
                            maxLines = 2,
                            lineHeight = 10.sp,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    RibbonIconButton(
                        control = control,
                        enabled = true,
                        checked = checked,
                        surfaces = surfaces,
                        checkedBg = checkedBg,
                        onClick = {
                            if (control.submenu.isNotEmpty()) {
                                expanded = true
                            } else {
                                onExecuteCommand(control.commandId, "{}")
                            }
                        },
                    )
                }

                if (control.submenu.isNotEmpty()) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        control.submenu.forEach { sub ->
                            DropdownMenuItem(
                                text = { Text(sub.labelEsCl, fontSize = 13.sp) },
                                onClick = {
                                    expanded = false
                                    onExecuteCommand(sub.commandId, "{}")
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun RibbonIconButton(
    control: RibbonControlSpec,
    enabled: Boolean,
    checked: Boolean,
    surfaces: OfficeTokens.SurfacePalette,
    checkedBg: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(OfficeTokens.TouchTargets.tabletCompactButton)
            .clip(RoundedCornerShape(OfficeTokens.Radii.button))
            .background(if (checked) checkedBg else Color.Transparent)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        ControlVectorIcon(
            iconName = control.icon,
            contentDescription = control.labelEsCl,
            sizeDp = 18,
            alpha = if (enabled) 1f else 0.4f,
        )
    }
}

@Composable
private fun SmartphoneTopHeader(
    documentTitle: String,
    canUndo: Boolean,
    canRedo: Boolean,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onOpenBackstage: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenSearchRibbon: () -> Unit,
    onToggleDesktopMode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(surfaces.titleBar)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                    .clickable { onOpenBackstage() },
                color = accent,
            ) {
                Text(
                    text = "Archivo",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            Text(
                text = documentTitle,
                color = surfaces.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "↶",
                color = if (canUndo) surfaces.text else surfaces.textDisabled,
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable(enabled = canUndo) { onUndo() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Text(
                text = "↷",
                color = if (canRedo) surfaces.text else surfaces.textDisabled,
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable(enabled = canRedo) { onRedo() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Text(
                text = "🔍",
                color = accent,
                fontSize = 15.sp,
                modifier = Modifier
                    .clickable { onOpenSearchRibbon() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            TitleActionChip(
                label = "PC",
                active = false,
                surfaces = surfaces,
                accent = accent,
                onClick = onToggleDesktopMode,
            )
        }
    }
}

@Composable
private fun SmartphoneQuickBar(
    pinnedControls: List<RibbonControlSpec>,
    activeToggleIds: Set<String>,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    checkedBg: Color,
    onExecuteCommand: (String, String) -> Unit,
    onExpandRibbonSheet: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(surfaces.ribbonCard)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            pinnedControls.forEach { ctrl ->
                val checked = activeToggleIds.contains(ctrl.commandId)
                Box(
                    modifier = Modifier
                        .size(OfficeTokens.TouchTargets.phoneMin)
                        .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                        .background(if (checked) checkedBg else Color.Transparent)
                        .clickable { onExecuteCommand(ctrl.commandId, "{}") },
                    contentAlignment = Alignment.Center,
                ) {
                    ControlVectorIcon(iconName = ctrl.icon, contentDescription = ctrl.labelEsCl, sizeDp = 22)
                }
            }
        }
        Surface(
            modifier = Modifier
                .sizeIn(minHeight = OfficeTokens.TouchTargets.phoneMin)
                .clip(RoundedCornerShape(OfficeTokens.Radii.pill))
                .clickable { onExpandRibbonSheet() },
            color = accent.copy(alpha = 0.14f),
            shape = RoundedCornerShape(OfficeTokens.Radii.pill),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "▲ Cinta",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PhoneRibbonSheetContent(
    catalog: RibbonSchemaCatalog,
    tabs: List<RibbonTabSpec>,
    selectedIndex: Int,
    searchQuery: String,
    activeToggleIds: Set<String>,
    formatSummary: RibbonFormatSummary,
    availableFonts: List<String>,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    checkedBg: Color,
    onQueryChange: (String) -> Unit,
    onSelectTab: (Int) -> Unit,
    onExecuteCommand: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // Command Search Field
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.pill)),
            shape = RoundedCornerShape(OfficeTokens.Radii.pill),
            color = surfaces.panel,
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Buscar comando (ej. negrita, tabla, márgenes)...",
                        color = surfaces.textDim,
                        fontSize = 13.sp,
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = surfaces.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (searchQuery.isNotBlank()) {
            val matches = remember(catalog, searchQuery) {
                AdaptiveShellModel.searchCommands(catalog, searchQuery)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(matches, key = { "${it.tabId}:${it.control.commandId}:${it.control.labelEsCl}" }) { match ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = OfficeTokens.TouchTargets.phoneMin)
                            .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                            .clickable { onExecuteCommand(match.control.commandId, "{}") }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ControlVectorIcon(iconName = match.control.icon, contentDescription = match.control.labelEsCl, sizeDp = 22)
                            Column {
                                Text(match.control.labelEsCl, color = surfaces.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("${match.tabLabelEsCl} › ${match.groupLabelEsCl}", color = surfaces.textDim, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Horizontal 9+ Tabs Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tabs.forEachIndexed { idx, tab ->
                    val selected = idx == selectedIndex
                    Surface(
                        modifier = Modifier
                            .sizeIn(minHeight = OfficeTokens.TouchTargets.phoneMin)
                            .clip(RoundedCornerShape(OfficeTokens.Radii.pill))
                            .clickable { onSelectTab(idx) },
                        color = if (selected) accent else surfaces.panel,
                        shape = RoundedCornerShape(OfficeTokens.Radii.pill),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tab.labelEsCl,
                                color = if (selected) Color.White else surfaces.text,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            val currentTab = tabs.getOrNull(selectedIndex)
            if (currentTab != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(currentTab.groups, key = { it.id }) { group ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(OfficeTokens.Radii.ribbonCard))
                                .padding(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(group.labelEsCl, color = surfaces.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                if (group.dialogCommandId != null) {
                                    Text(
                                        text = "Opciones ↘",
                                        color = accent,
                                        fontSize = 11.sp,
                                        modifier = Modifier.clickable { onExecuteCommand(group.dialogCommandId, "{}") },
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            group.controls.forEach { ctrl ->
                                val isChecked = activeToggleIds.contains(ctrl.commandId)
                                val hasInlinePicker = RibbonCorePresets.supportsInteractiveControl(ctrl)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .sizeIn(minHeight = OfficeTokens.TouchTargets.phoneMin)
                                        .clip(RoundedCornerShape(OfficeTokens.Radii.button))
                                        .background(if (isChecked) checkedBg else Color.Transparent)
                                        .clickable(enabled = !hasInlinePicker) {
                                            onExecuteCommand(ctrl.commandId, "{}")
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        ControlVectorIcon(iconName = ctrl.icon, contentDescription = ctrl.labelEsCl, sizeDp = 22)
                                        Text(
                                            text = ctrl.labelEsCl,
                                            color = if (isChecked) accent else surfaces.text,
                                            fontSize = 14.sp,
                                        )
                                    }
                                    if (hasInlinePicker) {
                                        RibbonInteractiveControl(
                                            control = ctrl,
                                            formatSummary = formatSummary,
                                            surfaces = surfaces,
                                            accent = accent,
                                            checkedBg = checkedBg,
                                            availableFonts = availableFonts,
                                            onExecuteCommand = onExecuteCommand,
                                            iconContent = { iconName, desc, sz ->
                                                ControlVectorIcon(iconName = iconName, contentDescription = desc, sizeDp = sz)
                                            },
                                            fallbackButton = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("DiscouragedApi")
@Composable
private fun ControlVectorIcon(
    iconName: String,
    contentDescription: String,
    sizeDp: Int,
    alpha: Float = 1f,
) {
    val context = LocalContext.current
    val resId = remember(iconName) {
        context.resources.getIdentifier(iconName.lowercase(), "drawable", context.packageName)
    }
    if (resId != 0) {
        Icon(
            painter = painterResource(resId),
            contentDescription = contentDescription,
            tint = Color.Unspecified,
            modifier = Modifier.size(sizeDp.dp),
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .background(Color.Gray.copy(alpha = 0.25f * alpha), RoundedCornerShape(3.dp)),
        )
    }
}
