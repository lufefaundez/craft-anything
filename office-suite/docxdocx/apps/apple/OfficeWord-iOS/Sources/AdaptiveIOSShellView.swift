import OfficeCoreKit
import SwiftUI

public struct AdaptiveIOSShellView: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Environment(\.colorScheme) private var colorScheme
    @Bindable var store: IOSDocxStore

    @State private var showPhoneRibbonSheet = false
    @State private var phoneSearchQuery = ""
    @State private var findQuery = ""
    @State private var replaceQuery = ""

    private var isDark: Bool { colorScheme == .dark }
    private var surfaces: OfficeTokens.SurfacePalette {
        isDark ? OfficeTokens.darkSurfaces : OfficeTokens.lightSurfaces
    }
    private var accent: Color {
        isDark ? OfficeTokens.AppAccents.Word.accentDark : OfficeTokens.AppAccents.Word.accentLight
    }
    private var checkedBg: Color {
        isDark ? OfficeTokens.AppAccents.Word.checkedBgDark : OfficeTokens.AppAccents.Word.checkedBgLight
    }

    public init(store: IOSDocxStore) {
        self.store = store
    }

    public var body: some View {
        let visibleTabs = AdaptiveRibbonHelper.visibleTabs(
            catalog: store.catalog,
            activeContexts: store.activeContexts
        )
        let safeIndex = min(max(0, store.activeTabIndex), max(0, visibleTabs.count - 1))
        let currentTab = visibleTabs.indices.contains(safeIndex) ? visibleTabs[safeIndex] : nil

        VStack(spacing: 0) {
            if sizeClass == .regular {
                ipadTitleAndQatBar(surfaces: surfaces, accent: accent)
                ipadTabStrip(tabs: visibleTabs, selectedIndex: safeIndex, surfaces: surfaces, accent: accent)
                if let currentTab {
                    ipadFloatingRibbonCard(tab: currentTab, surfaces: surfaces, accent: accent, checkedBg: checkedBg)
                }
                if store.showFindReplaceBar {
                    findReplaceBar
                }
                if store.showRuler {
                    IOSHorizontalRulerView(surfaces: surfaces)
                }
            } else {
                iphoneTopBar
                if store.showFindReplaceBar {
                    findReplaceBar
                }
            }

            IOSDocumentCanvasView(store: store, isDark: isDark)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            Divider()

            if sizeClass == .compact {
                iphoneStickyQuickBar
            }
            statusBar
        }
        .background(surfaces.canvas.ignoresSafeArea())
        .sheet(isPresented: $store.showBackstage) {
            IOSBackstageView(store: store, isDark: isDark)
        }
        .sheet(isPresented: $showPhoneRibbonSheet) {
            iphoneRibbonSheet(tabs: visibleTabs, safeIndex: safeIndex)
        }
        .sheet(item: $store.activeModalDialog) { kind in
            IOSModalDialogSheet(store: store, kind: kind, surfaces: surfaces, accent: accent)
        }
    }

    // MARK: - iPad Regular Ribbon (Office 16.115 Full Parity)

    private func ipadTitleAndQatBar(surfaces: OfficeTokens.SurfacePalette, accent: Color) -> some View {
        HStack(spacing: 10) {
            Text("DocxDocx")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(accent)

            ForEach(store.catalog.qat) { qat in
                let disabled = (qat.commandId == "edit.undo" && !store.canUndo)
                    || (qat.commandId == "edit.redo" && !store.canRedo)
                Button {
                    store.executeCommand(qat.commandId)
                } label: {
                    AdaptiveRibbonHelper.officeIconImage(named: qat.icon)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 16, height: 16)
                        .frame(width: 28, height: 26)
                }
                .buttonStyle(.plain)
                .disabled(disabled)
                .opacity(disabled ? 0.35 : 1.0)
            }

            Spacer()

            Text(store.documentTitle)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(surfaces.text)
                .lineLimit(1)

            Spacer()

            Text("OfiOfi Free • Local / iCloud / Drive / WebDAV")
                .font(.system(size: 11))
                .foregroundStyle(surfaces.textDim)
        }
        .padding(.horizontal, 12)
        .frame(height: 36)
        .background(surfaces.titleBar)
    }

    private func ipadTabStrip(
        tabs: [RibbonTabSpec],
        selectedIndex: Int,
        surfaces: OfficeTokens.SurfacePalette,
        accent: Color
    ) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                Button {
                    store.showBackstage = true
                } label: {
                    Text("Archivo")
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 5)
                        .background(accent)
                        .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.button))
                }
                .buttonStyle(.plain)

                ForEach(Array(tabs.enumerated()), id: \.element.id) { idx, tab in
                    let isSelected = idx == selectedIndex
                    let isContextual = tab.contextualTrigger != nil
                    Button {
                        store.activeTabIndex = idx
                    } label: {
                        VStack(spacing: 2) {
                            Text(tab.labelEsCl)
                                .font(.system(size: 13, weight: isSelected ? .semibold : .regular))
                                .foregroundStyle(
                                    isSelected ? accent : (isContextual ? Color(red: 0.72, green: 0.28, blue: 0.16) : surfaces.text)
                                )
                            Rectangle()
                                .fill(isSelected ? (isContextual ? Color(red: 0.72, green: 0.28, blue: 0.16) : accent) : .clear)
                                .frame(height: 2.5)
                        }
                        .padding(.horizontal, 8)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10)
        }
        .frame(height: 34)
        .background(surfaces.tabStrip)
    }

    private func ipadFloatingRibbonCard(
        tab: RibbonTabSpec,
        surfaces: OfficeTokens.SurfacePalette,
        accent: Color,
        checkedBg: Color
    ) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(tab.groups) { group in
                    let slots = AdaptiveRibbonHelper.layoutColumns(for: group)
                    VStack(spacing: 3) {
                        HStack(spacing: 4) {
                            ForEach(slots) { slot in
                                switch slot {
                                case .tall(let ctrl):
                                    IOSRibbonControlView(
                                        store: store,
                                        control: ctrl,
                                        tall: true,
                                        surfaces: surfaces,
                                        accent: accent,
                                        checkedBg: checkedBg
                                    )
                                case .stackedPair(let top, let bottom):
                                    VStack(spacing: 3) {
                                        IOSRibbonControlView(
                                            store: store,
                                            control: top,
                                            tall: false,
                                            surfaces: surfaces,
                                            accent: accent,
                                            checkedBg: checkedBg
                                        )
                                        if let bottom {
                                            IOSRibbonControlView(
                                                store: store,
                                                control: bottom,
                                                tall: false,
                                                surfaces: surfaces,
                                                accent: accent,
                                                checkedBg: checkedBg
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        .frame(maxHeight: .infinity)

                        HStack(spacing: 4) {
                            Text(group.labelEsCl)
                                .font(.system(size: 10))
                                .foregroundStyle(surfaces.textDim)
                            if let dialogCmd = group.dialogCommandId {
                                Button {
                                    store.executeCommand(dialogCmd)
                                } label: {
                                    Text("↘")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundStyle(accent)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(.horizontal, 6)
                    Divider().frame(height: 70)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
        }
        .frame(height: 92)
        .background(surfaces.ribbonCard)
        .overlay(
            RoundedRectangle(cornerRadius: OfficeTokens.Radii.ribbonCard)
                .stroke(surfaces.ribbonBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.ribbonCard))
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }

    // MARK: - iPhone Compact Chrome + Full Searchable Bottom Sheet

    private var iphoneTopBar: some View {
        HStack(spacing: 10) {
            Button { store.showBackstage = true } label: {
                Text("Archivo")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(accent)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
            }
            Button { store.executeCommand("edit.undo") } label: {
                Image(systemName: "arrow.uturn.backward")
            }
            .disabled(!store.canUndo)
            Button { store.executeCommand("edit.redo") } label: {
                Image(systemName: "arrow.uturn.forward")
            }
            .disabled(!store.canRedo)

            Spacer()

            Text(store.documentTitle)
                .font(.system(size: 14, weight: .medium))
                .lineLimit(1)

            Spacer()

            Button { showPhoneRibbonSheet = true } label: {
                Image(systemName: "magnifyingglass")
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 46)
        .background(surfaces.titleBar)
    }

    private var iphoneStickyQuickBar: some View {
        HStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(store.catalog.smartphoneQuickBar) { ctrl in
                        let checked = store.activeToggleIds.contains(ctrl.commandId)
                        Button {
                            store.executeCommand(ctrl.commandId)
                        } label: {
                            AdaptiveRibbonHelper.officeIconImage(named: ctrl.icon)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 20, height: 20)
                                .frame(width: OfficeTokens.TouchTargets.phoneMin, height: 38)
                                .background(checked ? checkedBg : .clear)
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            Button {
                showPhoneRibbonSheet = true
            } label: {
                Text("⋯ Cinta")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(accent)
                    .padding(.horizontal, 10)
                    .frame(height: 36)
                    .background(checkedBg)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 8)
        .frame(height: 46)
        .background(surfaces.ribbonCard)
    }

    private func iphoneRibbonSheet(tabs: [RibbonTabSpec], safeIndex: Int) -> some View {
        NavigationStack {
            VStack(spacing: 8) {
                TextField("Buscar comando (ej. negrita, tabla, Oficio)...", text: $phoneSearchQuery)
                    .textFieldStyle(.roundedBorder)
                    .padding(.horizontal)

                if !phoneSearchQuery.trimmingCharacters(in: .whitespaces).isEmpty {
                    let matches = AdaptiveRibbonHelper.searchCommands(catalog: store.catalog, query: phoneSearchQuery)
                    List(matches) { m in
                        Button {
                            showPhoneRibbonSheet = false
                            store.executeCommand(m.control.commandId)
                        } label: {
                            HStack(spacing: 10) {
                                AdaptiveRibbonHelper.officeIconImage(named: m.control.icon)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 22, height: 22)
                                VStack(alignment: .leading) {
                                    Text(m.control.labelEsCl).foregroundStyle(surfaces.text)
                                    Text("\(m.tabLabelEsCl) › \(m.groupLabelEsCl)")
                                        .font(.caption)
                                        .foregroundStyle(surfaces.textDim)
                                }
                            }
                        }
                    }
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Array(tabs.enumerated()), id: \.element.id) { idx, t in
                                Button {
                                    store.activeTabIndex = idx
                                } label: {
                                    Text(t.labelEsCl)
                                        .font(.subheadline.weight(idx == safeIndex ? .semibold : .regular))
                                        .foregroundStyle(idx == safeIndex ? .white : surfaces.text)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(idx == safeIndex ? accent : surfaces.panel)
                                        .clipShape(Capsule())
                                }
                            }
                        }
                        .padding(.horizontal)
                    }

                    if tabs.indices.contains(safeIndex) {
                        List(tabs[safeIndex].groups) { grp in
                            Section(grp.labelEsCl) {
                                ForEach(grp.controls) { ctrl in
                                    Button {
                                        showPhoneRibbonSheet = false
                                        store.executeCommand(ctrl.commandId)
                                    } label: {
                                        HStack(spacing: 10) {
                                            AdaptiveRibbonHelper.officeIconImage(named: ctrl.icon)
                                                .resizable()
                                                .scaledToFit()
                                                .frame(width: 22, height: 22)
                                            Text(ctrl.labelEsCl).foregroundStyle(surfaces.text)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Herramientas")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { showPhoneRibbonSheet = false }
                }
            }
        }
    }

    private var findReplaceBar: some View {
        HStack(spacing: 8) {
            TextField("Buscar...", text: $findQuery)
                .textFieldStyle(.roundedBorder)
            TextField("Reemplazar...", text: $replaceQuery)
                .textFieldStyle(.roundedBorder)
            Button("Siguiente") {
                store.executeCommand("edit.findNext", paramsJson: OfficeRibbonPresets.findPayload(query: findQuery))
            }
            Button("Reemplazar") {
                store.executeCommand(
                    "edit.replaceAll",
                    paramsJson: OfficeRibbonPresets.replacePayload(query: findQuery, replacement: replaceQuery)
                )
            }
            Button("Cerrar") { store.showFindReplaceBar = false }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(surfaces.ribbonCard)
    }

    private var statusBar: some View {
        HStack {
            Text("Página 1 de \(store.pageCount)  •  \(store.wordCount) palabras  •  Español (Chile)")
                .font(.system(size: 11))
                .foregroundStyle(surfaces.textDim)
            Spacer()
            Button(store.isMobileReflow ? "Vista Móvil" : "Diseño Impresión") {
                store.executeCommand(store.isMobileReflow ? "view.printLayout" : "view.webLayout")
            }
            .font(.system(size: 11, weight: .medium))
            .foregroundStyle(accent)
            Text("\(Int((store.zoom * 100).rounded())) %")
                .font(.system(size: 11))
                .foregroundStyle(surfaces.textDim)
        }
        .padding(.horizontal, 12)
        .frame(height: 26)
        .background(surfaces.statusBar)
    }
}

struct IOSRibbonControlView: View {
    @Bindable var store: IOSDocxStore
    let control: RibbonControlSpec
    let tall: Bool
    let surfaces: OfficeTokens.SurfacePalette
    let accent: Color
    let checkedBg: Color

    @State private var showTablePopover = false
    @State private var gridRows = 3
    @State private var gridCols = 4

    var body: some View {
        switch control.kind {
        case "font_picker":
            Menu {
                ForEach(DocxCoreBridge.loadAllSystemFonts(), id: \.self) { f in
                    Button(f) { store.executeCommand("format.font", paramsJson: #"{"name":"\#(f)"}"#) }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(store.activeFontName).font(.system(size: 11)).lineLimit(1)
                    Spacer(minLength: 2)
                    Text("▾").font(.system(size: 9)).foregroundStyle(surfaces.textDim)
                }
                .padding(.horizontal, 6)
                .frame(width: 108, height: 24)
                .background(surfaces.panel)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(surfaces.ribbonBorder, lineWidth: 1))
            }
        case "size_picker":
            Menu {
                ForEach(OfficeRibbonPresets.fontSizes, id: \.self) { sz in
                    Button("\(Int(sz))") { store.executeCommand("format.size", paramsJson: #"{"size":\#(sz)}"#) }
                }
            } label: {
                HStack(spacing: 2) {
                    Text("\(Int(store.activeFontSize.rounded()))").font(.system(size: 11))
                    Text("▾").font(.system(size: 9)).foregroundStyle(surfaces.textDim)
                }
                .padding(.horizontal, 5)
                .frame(width: 46, height: 24)
                .background(surfaces.panel)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(surfaces.ribbonBorder, lineWidth: 1))
            }
        case "style_gallery":
            HStack(spacing: 4) {
                ForEach(OfficeRibbonPresets.styles) { st in
                    let selected = store.activeStyleName.caseInsensitiveCompare(st.id) == .orderedSame
                    Button {
                        store.executeCommand("styles.apply", paramsJson: #"{"style":"\#(st.id)"}"#)
                    } label: {
                        VStack(spacing: 2) {
                            Text("AaBbCc")
                                .font(.system(size: st.previewSize, weight: st.isBold ? .bold : .regular))
                                .foregroundStyle(st.id.hasPrefix("Heading") ? accent : surfaces.text)
                            Text(st.labelEsCl).font(.system(size: 9)).foregroundStyle(surfaces.textDim)
                        }
                        .frame(width: 64, height: 50)
                        .background(selected ? checkedBg : surfaces.panel)
                        .overlay(
                            RoundedRectangle(cornerRadius: OfficeTokens.Radii.styleTile)
                                .stroke(selected ? accent : surfaces.ribbonBorder, lineWidth: selected ? 1.5 : 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        case "table_grid":
            Button { showTablePopover.toggle() } label: {
                standardLabel(checked: false)
            }
            .buttonStyle(.plain)
            .popover(isPresented: $showTablePopover) {
                VStack(spacing: 8) {
                    Text("Insertar tabla de \(gridRows) × \(gridCols)").font(.caption.weight(.semibold))
                    ForEach(1...6, id: \.self) { r in
                        HStack(spacing: 3) {
                            ForEach(1...8, id: \.self) { c in
                                RoundedRectangle(cornerRadius: 2)
                                    .fill((r <= gridRows && c <= gridCols) ? accent.opacity(0.28) : Color.secondary.opacity(0.15))
                                    .frame(width: 18, height: 18)
                                    .onTapGesture {
                                        gridRows = r
                                        gridCols = c
                                    }
                            }
                        }
                    }
                    Button("Insertar \(gridRows) × \(gridCols)") {
                        showTablePopover = false
                        store.executeCommand("insert.table", paramsJson: OfficeRibbonPresets.tablePayload(rows: gridRows, cols: gridCols))
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(12)
            }
        case "color_picker":
            Menu {
                ForEach(OfficeRibbonPresets.swatches) { sw in
                    Button(sw.nameEsCl) {
                        let cmd = (control.commandId == "para.shading") ? "format.shading" : control.commandId
                        store.executeCommand(cmd, paramsJson: OfficeRibbonPresets.colorPayload(commandId: cmd, hex: sw.hex))
                    }
                }
            } label: {
                standardLabel(checked: false)
            }
        default:
            let checked = store.activeToggleIds.contains(control.commandId)
                || AppleInkTool.from(commandId: control.commandId) == store.activeInkTool && store.activeInkTool != .none
            if control.commandId == "layout.size" {
                Menu {
                    ForEach(OfficeRibbonPresets.paperSizes) { p in
                        Button(p.labelEsCl) { store.executeCommand(p.commandId, paramsJson: p.paramsJson) }
                    }
                } label: { standardLabel(checked: false) }
            } else if control.commandId == "layout.margins" {
                Menu {
                    ForEach(OfficeRibbonPresets.margins) { m in
                        Button(m.labelEsCl) { store.executeCommand(m.commandId, paramsJson: m.paramsJson) }
                    }
                } label: { standardLabel(checked: false) }
            } else if control.commandId == "layout.orientation" {
                Menu {
                    ForEach(OfficeRibbonPresets.orientations) { o in
                        Button(o.labelEsCl) { store.executeCommand(o.commandId, paramsJson: o.paramsJson) }
                    }
                } label: { standardLabel(checked: false) }
            } else {
                Button { store.executeCommand(control.commandId) } label: { standardLabel(checked: checked) }
                    .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private func standardLabel(checked: Bool) -> some View {
        if tall {
            VStack(spacing: 3) {
                AdaptiveRibbonHelper.officeIconImage(named: control.icon)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 22, height: 22)
                Text(control.labelEsCl)
                    .font(.system(size: 9.5))
                    .foregroundStyle(checked ? accent : surfaces.text)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 6)
            .frame(minWidth: 54, minHeight: OfficeTokens.TouchTargets.tabletLargeButton)
            .background(checked ? checkedBg : .clear)
            .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.button))
        } else {
            AdaptiveRibbonHelper.officeIconImage(named: control.icon)
                .resizable()
                .scaledToFit()
                .frame(width: 17, height: 17)
                .frame(width: 30, height: 26)
                .background(checked ? checkedBg : .clear)
                .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.button))
        }
    }
}

struct IOSHorizontalRulerView: View {
    let surfaces: OfficeTokens.SurfacePalette

    var body: some View {
        Canvas { ctx, size in
            let ticks = OfficeRulerMath.buildTicks(totalPoints: 612.0, marginStartPt: 72.0)
            let rulerW = min(size.width - 36.0, 760.0)
            let startX = max(18.0, (size.width - rulerW) / 2.0)
            let pxPerPt = rulerW / 612.0
            for t in ticks {
                let x = startX + t.positionPt * pxPerPt
                let topY = t.isMajor ? size.height * 0.48 : size.height * 0.68
                var p = Path()
                p.move(to: CGPoint(x: x, y: topY))
                p.addLine(to: CGPoint(x: x, y: size.height))
                ctx.stroke(p, with: .color(surfaces.textDim.opacity(0.7)), lineWidth: t.isMajor ? 1.0 : 0.6)
                if let lbl = t.label {
                    ctx.draw(
                        Text(lbl).font(.system(size: 8.5)).foregroundStyle(surfaces.textDim),
                        at: CGPoint(x: x, y: size.height * 0.28)
                    )
                }
            }
        }
        .frame(height: 20)
        .background(surfaces.panel)
    }
}

struct IOSModalDialogSheet: View {
    @Bindable var store: IOSDocxStore
    let kind: IOSModalDialogKind
    let surfaces: OfficeTokens.SurfacePalette
    let accent: Color

    @State private var selectedPaperId = "letter"
    @State private var selectedOrientation = "portrait"

    var body: some View {
        NavigationStack {
            Form {
                switch kind {
                case .font:
                    Section("Fuente y estilo") {
                        Picker("Tipografía", selection: Binding(
                            get: { store.activeFontName },
                            set: { store.executeCommand("format.font", paramsJson: #"{"name":"\#($0)"}"#) }
                        )) {
                            ForEach(DocxCoreBridge.loadAllSystemFonts(), id: \.self) { f in Text(f).tag(f) }
                        }
                        Button("Negrita") { store.executeCommand("format.bold") }
                        Button("Cursiva") { store.executeCommand("format.italic") }
                        Button("Subrayado") { store.executeCommand("format.underline") }
                    }
                case .paragraph:
                    Section("Alineación e interlineado") {
                        Button("Izquierda") { store.executeCommand("para.alignLeft") }
                        Button("Centrado") { store.executeCommand("para.alignCenter") }
                        Button("Derecha") { store.executeCommand("para.alignRight") }
                        Button("Justificado") { store.executeCommand("para.justify") }
                        Button("Interlineado 1,5") { store.executeCommand("para.oneAndHalf") }
                    }
                case .pageSetup:
                    Section("Tamaño y orientación") {
                        Picker("Papel", selection: $selectedPaperId) {
                            ForEach(OfficeRibbonPresets.paperSizes) { p in Text(p.labelEsCl).tag(p.id) }
                        }
                        Picker("Orientación", selection: $selectedOrientation) {
                            ForEach(OfficeRibbonPresets.orientations) { o in Text(o.labelEsCl).tag(o.id) }
                        }
                    }
                }
            }
            .navigationTitle(kind == .font ? "Fuente" : (kind == .paragraph ? "Párrafo" : "Configurar página"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Aplicar") {
                        if kind == .pageSetup {
                            if let p = OfficeRibbonPresets.paperSizes.first(where: { $0.id == selectedPaperId }) {
                                store.executeCommand(p.commandId, paramsJson: p.paramsJson)
                            }
                            if let o = OfficeRibbonPresets.orientations.first(where: { $0.id == selectedOrientation }) {
                                store.executeCommand(o.commandId, paramsJson: o.paramsJson)
                            }
                        }
                        store.activeModalDialog = nil
                    }
                }
            }
        }
    }
}
