import AppKit
import OfficeCoreKit
import SwiftUI

final class DocxAppDelegate: NSObject, NSApplicationDelegate {
    weak var workspaceModel: DocxWorkspaceModel?

    func application(_ application: NSApplication, open urls: [URL]) {
        guard let url = urls.first else { return }
        Task { @MainActor [weak self] in
            self?.workspaceModel?.openFile(url: url)
        }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        true
    }
}

@main
struct DocxDocxMacApp: App {
    @NSApplicationDelegateAdaptor(DocxAppDelegate.self) private var appDelegate
    @State private var model = DocxWorkspaceModel()

    var body: some Scene {
        WindowGroup("DocxDocx — OfiOfi") {
            MacWorkspaceView(model: model)
                .onAppear {
                    appDelegate.workspaceModel = model
                }
                .onOpenURL { url in
                    model.openFile(url: url)
                }
                .frame(minWidth: 1024, minHeight: 700)
        }
        .windowStyle(.titleBar)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("Nuevo documento") { model.newFromTemplate(.blank) }
                    .keyboardShortcut("n", modifiers: .command)
                Button("Abrir...") { model.presentOpenPanel() }
                    .keyboardShortcut("o", modifiers: .command)
            }
            CommandGroup(replacing: .saveItem) {
                Button("Guardar") { model.saveDocument() }
                    .keyboardShortcut("s", modifiers: .command)
                Button("Guardar como...") { model.presentSaveAsPanel() }
                    .keyboardShortcut("s", modifiers: [.command, .shift])
                Button("Exportar a PDF...") { model.presentExportPdfPanel() }
                    .keyboardShortcut("e", modifiers: [.command, .shift])
                Divider()
                Button("Imprimir...") { model.printDocument() }
                    .keyboardShortcut("p", modifiers: .command)
            }
            CommandGroup(replacing: .undoRedo) {
                Button("Deshacer") { model.executeCommand("edit.undo") }
                    .keyboardShortcut("z", modifiers: .command)
                    .disabled(!model.canUndo)
                Button("Rehacer") { model.executeCommand("edit.redo") }
                    .keyboardShortcut("z", modifiers: [.command, .shift])
                    .disabled(!model.canRedo)
            }
            CommandMenu("Formato y Herramientas") {
                Button("Negrita") { model.executeCommand("format.bold") }
                    .keyboardShortcut("b", modifiers: .command)
                Button("Cursiva") { model.executeCommand("format.italic") }
                    .keyboardShortcut("i", modifiers: .command)
                Button("Subrayado") { model.executeCommand("format.underline") }
                    .keyboardShortcut("u", modifiers: .command)
                Divider()
                Button("Mostrar/Ocultar Regla") { model.showRuler.toggle() }
                    .keyboardShortcut("r", modifiers: [.command, .option])
                Button("Buscar comando (¿Qué desea hacer?)") {
                    model.showCommandSearch.toggle()
                }
                .keyboardShortcut("k", modifiers: .command)
            }
        }
    }
}

struct MacWorkspaceView: View {
    @Bindable var model: DocxWorkspaceModel
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let surfaces = colorScheme == .dark ? OfficeTokens.darkSurfaces : OfficeTokens.lightSurfaces
        let accent = colorScheme == .dark ? OfficeTokens.AppAccents.Word.accentDark : OfficeTokens.AppAccents.Word.accentLight
        let checkedBg = colorScheme == .dark ? OfficeTokens.AppAccents.Word.checkedBgDark : OfficeTokens.AppAccents.Word.checkedBgLight
        let tabs = model.visibleTabs
        let safeIndex = min(max(0, model.selectedTabIndex), max(0, tabs.count - 1))
        let currentTab = tabs.isEmpty ? nil : tabs[safeIndex]

        VStack(spacing: 0) {
            // 1. Title Bar + Quick Access Toolbar (QAT, 38 pt)
            HStack(spacing: 10) {
                Text("DocxDocx")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(accent)

                HStack(spacing: 4) {
                    ForEach(model.catalog.qat) { qatCtrl in
                        let enabled: Bool = {
                            switch qatCtrl.commandId {
                            case "edit.undo": return model.canUndo
                            case "edit.redo": return model.canRedo
                            default: return true
                            }
                        }()
                        Button {
                            model.executeCommand(qatCtrl.commandId)
                        } label: {
                            AdaptiveRibbonHelper.officeIconImage(named: qatCtrl.icon)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 16, height: 16)
                                .opacity(enabled ? 1.0 : 0.38)
                                .frame(width: 28, height: 28)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .disabled(!enabled)
                        .help(qatCtrl.labelEsCl)
                    }
                }

                Spacer()

                HStack(spacing: 4) {
                    Text(model.documentTitle)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(surfaces.text)
                    if model.isDirty {
                        Text("— Editado")
                            .font(.system(size: 11))
                            .foregroundStyle(surfaces.textDim)
                    }
                }

                Spacer()

                Button {
                    model.showCommandSearch.toggle()
                } label: {
                    Text("🔍 ¿Qué desea hacer? (⌘K)")
                        .font(.system(size: 11))
                        .foregroundStyle(surfaces.textDim)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(surfaces.ribbonCard)
                        .overlay(
                            RoundedRectangle(cornerRadius: OfficeTokens.Radii.pill)
                                .stroke(surfaces.ribbonBorder, lineWidth: 1)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.pill))
                }
                .buttonStyle(.plain)
                .popover(isPresented: $model.showCommandSearch, arrowEdge: .bottom) {
                    CommandSearchPopover(model: model, surfaces: surfaces, accent: accent)
                }
            }
            .padding(.horizontal, 12)
            .frame(height: 38)
            .background(surfaces.titleBar)

            // 2. Ribbon Tab Strip (32 pt)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 2) {
                    Button {
                        model.showBackstage = true
                    } label: {
                        Text("Archivo")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 5)
                            .background(accent)
                            .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.button))
                    }
                    .buttonStyle(.plain)
                    .padding(.trailing, 6)

                    ForEach(Array(tabs.enumerated()), id: \.element.id) { idx, tab in
                        let selected = idx == safeIndex
                        let isContextual = tab.contextualTrigger != nil
                        Button {
                            model.selectedTabIndex = idx
                        } label: {
                            VStack(spacing: 3) {
                                Text(tab.labelEsCl)
                                    .font(.system(size: 12.5, weight: selected ? .semibold : .regular))
                                    .foregroundStyle(
                                        selected ? accent : (isContextual ? OfficeTokens.AppAccents.Powerpoint.accentLight : surfaces.text)
                                    )
                                Rectangle()
                                    .fill(selected ? accent : Color.clear)
                                    .frame(width: 26, height: 2.5)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 3)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 10)
            }
            .frame(height: 32)
            .background(surfaces.tabStrip)

            // 3. Floating White Fluent Ribbon Card (94 pt)
            if let tab = currentTab {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 0) {
                        ForEach(tab.groups) { group in
                            let slots = AdaptiveRibbonHelper.layoutColumns(for: group)
                            VStack(spacing: 3) {
                                HStack(alignment: .center, spacing: 4) {
                                    ForEach(slots) { slot in
                                        switch slot {
                                        case .tall(let ctrl):
                                            MacRibbonSlotControl(
                                                model: model,
                                                control: ctrl,
                                                tall: true,
                                                surfaces: surfaces,
                                                accent: accent,
                                                checkedBg: checkedBg
                                            )
                                        case .stackedPair(let top, let bottom):
                                            VStack(alignment: .leading, spacing: 3) {
                                                MacRibbonSlotControl(
                                                    model: model,
                                                    control: top,
                                                    tall: false,
                                                    surfaces: surfaces,
                                                    accent: accent,
                                                    checkedBg: checkedBg
                                                )
                                                if let bottom {
                                                    MacRibbonSlotControl(
                                                        model: model,
                                                        control: bottom,
                                                        tall: false,
                                                        surfaces: surfaces,
                                                        accent: accent,
                                                        checkedBg: checkedBg
                                                    )
                                                } else {
                                                    Spacer().frame(height: 26)
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
                                        .lineLimit(1)
                                    if let dlg = group.dialogCommandId {
                                        Button("↘") {
                                            model.executeCommand(dlg)
                                        }
                                        .buttonStyle(.plain)
                                        .font(.system(size: 10))
                                        .foregroundStyle(accent)
                                    }
                                }
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)

                            Rectangle()
                                .fill(surfaces.ribbonBorder)
                                .frame(width: 1, height: 74)
                        }
                    }
                    .padding(.horizontal, 6)
                }
                .frame(height: 94)
                .background(surfaces.ribbonCard)
                .overlay(
                    RoundedRectangle(cornerRadius: OfficeTokens.Radii.ribbonCard)
                        .stroke(surfaces.ribbonBorder, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.ribbonCard))
                .shadow(color: surfaces.pageShadow.opacity(0.45), radius: 3, y: 1)
                .padding(.horizontal, 8)
                .padding(.bottom, 6)
            }

            // 4. Interactive Horizontal Ruler (Word 16.115 style)
            if model.showRuler {
                MacHorizontalRuler(zoom: model.zoom, surfaces: surfaces, accent: accent)
            }

            // 5. Native Centered Retina Document Canvas
            MacDocumentCanvas(model: model)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            Divider().overlay(surfaces.ribbonBorder)

            // 6. Status Bar (26 pt)
            HStack(spacing: 14) {
                let pageNum = Int(model.selection.caret?.pageIndex ?? 0) + 1
                Text("Página \(pageNum) de \(model.pageCount)   •   \(model.wordCount) palabras")
                    .font(.system(size: 11))
                    .foregroundStyle(surfaces.textDim)

                Text("Español (Chile)")
                    .font(.system(size: 11))
                    .foregroundStyle(surfaces.textDim)

                if let banner = model.statusBanner {
                    Text("•  \(banner)")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(accent)
                        .lineLimit(1)
                }

                Spacer()

                Button {
                    model.toggleLayoutMode()
                } label: {
                    Text(model.isMobileReflow ? "Vista Web (Reflow)" : "Diseño de Impresión")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(accent)
                }
                .buttonStyle(.plain)

                HStack(spacing: 6) {
                    Button("-") { model.clampAndSetZoom(model.zoom - 0.1) }
                        .buttonStyle(.plain)
                    Slider(
                        value: Binding(
                            get: { model.zoom },
                            set: { model.clampAndSetZoom($0) }
                        ),
                        in: 0.5...4.0
                    )
                    .frame(width: 110)
                    Button("+") { model.clampAndSetZoom(model.zoom + 0.1) }
                        .buttonStyle(.plain)
                    Text("\(Int((model.zoom * 100).rounded())) %")
                        .font(.system(size: 11))
                        .foregroundStyle(surfaces.textDim)
                        .frame(width: 42, alignment: .trailing)
                }
            }
            .padding(.horizontal, 12)
            .frame(height: 26)
            .background(surfaces.statusBar)
        }
        .background(surfaces.canvas)
        .sheet(isPresented: $model.showBackstage) {
            MacBackstageView(model: model)
        }
    }
}

private struct MacHorizontalRuler: View {
    let zoom: Double
    let surfaces: OfficeTokens.SurfacePalette
    let accent: Color

    var body: some View {
        GeometryReader { geo in
            let maxPageW = min(820.0, max(320.0, geo.size.width - 64.0)) * zoom
            let rulerW = min(geo.size.width - 32.0, maxPageW)
            let marginW = rulerW * (72.0 / 612.0)

            ZStack {
                surfaces.tabStrip
                HStack(spacing: 0) {
                    Rectangle().fill(surfaces.rulerMargin).frame(width: marginW)
                    Rectangle().fill(surfaces.ruler)
                    Rectangle().fill(surfaces.rulerMargin).frame(width: marginW)
                }
                .frame(width: rulerW, height: 15)
                .overlay(Rectangle().stroke(surfaces.ribbonBorder, lineWidth: 0.5))

                HStack(spacing: 0) {
                    ForEach(1..<22, id: \.self) { cm in
                        Text("\(cm)")
                            .font(.system(size: 8.5, weight: .medium))
                            .foregroundStyle(surfaces.textDim)
                            .frame(maxWidth: .infinity)
                    }
                }
                .frame(width: max(100, rulerW - marginW * 2), height: 15)
            }
        }
        .frame(height: 20)
        .overlay(alignment: .bottom) {
            Divider().overlay(surfaces.ribbonBorder)
        }
    }
}

private struct MacRibbonSlotControl: View {
    @Bindable var model: DocxWorkspaceModel
    let control: RibbonControlSpec
    let tall: Bool
    let surfaces: OfficeTokens.SurfacePalette
    let accent: Color
    let checkedBg: Color

    @State private var showTableGrid = false
    @State private var showColorPopover = false

    private static let fontChoices = [
        "Aptos", "Calibri", "Carlito", "Caladea", "Arial", "Liberation Sans", "Times New Roman", "Georgia", "Courier New",
    ]
    private static let sizeChoices: [Int] = [8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 36, 48, 72]
    private static let liveStyles: [(id: String, label: String, size: CGFloat, bold: Bool)] = [
        ("Normal", "Normal", 12, false),
        ("No Spacing", "Sin espaciado", 11, false),
        ("Heading 1", "Título 1", 15, true),
        ("Heading 2", "Título 2", 13, true),
        ("Title", "Título", 16, false),
        ("Subtitle", "Subtítulo", 12, false),
    ]
    private static let paletteSwatches: [(name: String, hex: String, color: Color)] = [
        ("Automático", "auto", Color.primary),
        ("Negro", "000000", Color.black),
        ("Azul Word", "185ABD", Color(red: 0.094, green: 0.353, blue: 0.741)),
        ("Azul marino", "0F2C59", Color(red: 0.06, green: 0.17, blue: 0.35)),
        ("Rojo", "C0392B", Color(red: 0.75, green: 0.22, blue: 0.17)),
        ("Verde", "107C41", Color(red: 0.06, green: 0.49, blue: 0.25)),
        ("Naranjo", "D35400", Color(red: 0.83, green: 0.33, blue: 0.0)),
        ("Gris oscuro", "595959", Color(red: 0.35, green: 0.35, blue: 0.35)),
    ]

    var body: some View {
        switch control.kind {
        case "font_picker":
            Menu {
                ForEach(Self.fontChoices, id: \.self) { fontName in
                    Button(fontName) {
                        model.executeCommand("format.font", paramsJson: #"{"name":"\#(fontName)"}"#)
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(model.activeFontName)
                        .font(.system(size: 11.5))
                        .foregroundStyle(surfaces.text)
                        .lineLimit(1)
                    Spacer(minLength: 2)
                    Text("▾").font(.system(size: 9)).foregroundStyle(surfaces.textDim)
                }
                .padding(.horizontal, 7)
                .frame(width: 116, height: 24)
                .background(surfaces.panel)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(surfaces.ribbonBorder, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 4))
            }
            .menuStyle(.borderlessButton)
            .help("Fuente")

        case "size_picker":
            Menu {
                ForEach(Self.sizeChoices, id: \.self) { pt in
                    Button("\(pt)") {
                        model.executeCommand("format.size", paramsJson: #"{"size":\#(pt)}"#)
                    }
                }
            } label: {
                HStack(spacing: 3) {
                    Text("\(Int(model.activeFontSize.rounded()))")
                        .font(.system(size: 11.5))
                        .foregroundStyle(surfaces.text)
                    Spacer(minLength: 1)
                    Text("▾").font(.system(size: 9)).foregroundStyle(surfaces.textDim)
                }
                .padding(.horizontal, 6)
                .frame(width: 48, height: 24)
                .background(surfaces.panel)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(surfaces.ribbonBorder, lineWidth: 1))
                .clipShape(RoundedRectangle(cornerRadius: 4))
            }
            .menuStyle(.borderlessButton)
            .help("Tamaño de fuente")

        case "style_gallery":
            HStack(spacing: 4) {
                ForEach(Self.liveStyles, id: \.id) { st in
                    let isSelected = model.activeStyleName.caseInsensitiveCompare(st.id) == .orderedSame
                        || model.activeStyleName.caseInsensitiveCompare(st.label) == .orderedSame
                    Button {
                        model.executeCommand("styles.apply", paramsJson: #"{"style":"\#(st.id)"}"#)
                    } label: {
                        VStack(spacing: 2) {
                            Text("AaBbCc")
                                .font(.system(size: st.size, weight: st.bold ? .bold : .regular))
                                .foregroundStyle(st.id.hasPrefix("Heading") ? accent : surfaces.text)
                                .lineLimit(1)
                            Text(st.label)
                                .font(.system(size: 9))
                                .foregroundStyle(surfaces.textDim)
                                .lineLimit(1)
                        }
                        .frame(width: 68, height: 52)
                        .background(surfaces.panel)
                        .overlay(
                            RoundedRectangle(cornerRadius: OfficeTokens.Radii.styleTile)
                                .stroke(isSelected ? accent : surfaces.ribbonBorder, lineWidth: isSelected ? 2 : 1)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.styleTile))
                    }
                    .buttonStyle(.plain)
                }
            }

        case "table_grid":
            Button {
                showTableGrid.toggle()
            } label: {
                VStack(spacing: 3) {
                    AdaptiveRibbonHelper.officeIconImage(named: control.icon)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 22, height: 22)
                    Text(control.labelEsCl)
                        .font(.system(size: 9.5))
                        .foregroundStyle(surfaces.text)
                }
                .padding(.horizontal, 8)
                .frame(minWidth: 56, minHeight: OfficeTokens.TouchTargets.tabletLargeButton, maxHeight: OfficeTokens.TouchTargets.tabletLargeButton)
            }
            .buttonStyle(.plain)
            .popover(isPresented: $showTableGrid, arrowEdge: .bottom) {
                MacTableGridPopover(surfaces: surfaces, accent: accent) { rows, cols in
                    model.executeCommand("insert.table", paramsJson: #"{"rows":\#(rows),"cols":\#(cols)}"#)
                    showTableGrid = false
                }
            }

        case "color_picker":
            Button {
                showColorPopover.toggle()
            } label: {
                VStack(spacing: 1) {
                    AdaptiveRibbonHelper.officeIconImage(named: control.icon)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 15, height: 15)
                    Rectangle()
                        .fill(control.commandId == "format.highlight" ? surfaces.highlightYellow : accent)
                        .frame(width: 16, height: 3)
                }
                .frame(width: 28, height: 26)
            }
            .buttonStyle(.plain)
            .help(control.labelEsCl)
            .popover(isPresented: $showColorPopover, arrowEdge: .bottom) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(control.labelEsCl)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(surfaces.text)
                    LazyVGrid(columns: Array(repeating: GridItem(.fixed(24), spacing: 6), count: 4), spacing: 6) {
                        ForEach(Self.paletteSwatches, id: \.hex) { swatch in
                            Button {
                                if control.commandId == "format.color" {
                                    model.executeCommand("format.color", paramsJson: #"{"color":"\#(swatch.hex)"}"#)
                                } else {
                                    model.executeCommand(control.commandId, paramsJson: "{}")
                                }
                                showColorPopover = false
                            } label: {
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(swatch.color)
                                    .frame(width: 22, height: 22)
                                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(surfaces.ribbonBorder, lineWidth: 1))
                            }
                            .buttonStyle(.plain)
                            .help(swatch.name)
                        }
                    }
                }
                .padding(10)
            }

        default:
            if control.commandId == "layout.size" {
                Menu {
                    Button("Carta (Letter • 21.59 × 27.94 cm)") {
                        model.executeCommand("layout.size", paramsJson: #"{"name":"Letter"}"#)
                    }
                    Button("Oficio Chile (21.59 × 33.00 cm)") {
                        model.executeCommand("layout.size", paramsJson: #"{"width":612,"height":935.43}"#)
                    }
                    Button("Legal (21.59 × 35.56 cm)") {
                        model.executeCommand("layout.size", paramsJson: #"{"name":"Legal"}"#)
                    }
                    Button("A4 (21.00 × 29.70 cm)") {
                        model.executeCommand("layout.size", paramsJson: #"{"name":"A4"}"#)
                    }
                } label: {
                    standardButtonLabel(checked: false)
                }
                .menuStyle(.borderlessButton)
                .help("Tamaño de papel (Carta / Oficio Chile / A4)")
            } else if control.commandId == "layout.orientation" {
                Menu {
                    Button("Vertical") {
                        model.executeCommand("layout.orientation", paramsJson: #"{"value":"portrait"}"#)
                    }
                    Button("Horizontal") {
                        model.executeCommand("layout.orientation", paramsJson: #"{"value":"landscape"}"#)
                    }
                } label: {
                    standardButtonLabel(checked: false)
                }
                .menuStyle(.borderlessButton)
                .help("Orientación de página")
            } else if !control.submenu.isEmpty {
                Menu {
                    ForEach(control.submenu) { sub in
                        Button(sub.labelEsCl) {
                            model.executeCommand(sub.commandId)
                        }
                    }
                } label: {
                    standardButtonLabel(checked: model.activeToggleIds.contains(control.commandId))
                }
                .menuStyle(.borderlessButton)
                .help(control.labelEsCl)
            } else {
                Button {
                    model.executeCommand(control.commandId)
                } label: {
                    standardButtonLabel(checked: model.activeToggleIds.contains(control.commandId))
                }
                .buttonStyle(.plain)
                .help(control.labelEsCl)
            }
        }
    }

    @ViewBuilder
    private func standardButtonLabel(checked: Bool) -> some View {
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
                    .fixedSize(horizontal: true, vertical: false)
            }
            .padding(.horizontal, 6)
            .frame(minWidth: 56, minHeight: OfficeTokens.TouchTargets.tabletLargeButton, maxHeight: OfficeTokens.TouchTargets.tabletLargeButton)
            .background(checked ? checkedBg : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.button))
        } else {
            AdaptiveRibbonHelper.officeIconImage(named: control.icon)
                .resizable()
                .scaledToFit()
                .frame(width: 17, height: 17)
                .frame(width: 28, height: 26)
                .background(checked ? checkedBg : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.button))
        }
    }
}

private struct MacTableGridPopover: View {
    let surfaces: OfficeTokens.SurfacePalette
    let accent: Color
    let onSelect: (Int, Int) -> Void

    @State private var hoverRows = 3
    @State private var hoverCols = 4

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Insertar tabla de \(hoverRows) × \(hoverCols)")
                .font(.system(size: 11.5, weight: .semibold))
                .foregroundStyle(surfaces.text)
            VStack(spacing: 3) {
                ForEach(1...6, id: \.self) { row in
                    HStack(spacing: 3) {
                        ForEach(1...8, id: \.self) { col in
                            let active = row <= hoverRows && col <= hoverCols
                            Rectangle()
                                .fill(active ? accent.opacity(0.28) : surfaces.panel)
                                .overlay(Rectangle().stroke(active ? accent : surfaces.ribbonBorder, lineWidth: 1))
                                .frame(width: 18, height: 16)
                                .onHover { inside in
                                    if inside {
                                        hoverRows = row
                                        hoverCols = col
                                    }
                                }
                                .onTapGesture {
                                    onSelect(row, col)
                                }
                        }
                    }
                }
            }
        }
        .padding(10)
    }
}

private struct CommandSearchPopover: View {
    @Bindable var model: DocxWorkspaceModel
    let surfaces: OfficeTokens.SurfacePalette
    let accent: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            TextField("Buscar entre los 651 comandos (ej. negrita, tabla, márgenes)...", text: $model.searchQuery)
                .textFieldStyle(.roundedBorder)

            let matches = AdaptiveRibbonHelper.searchCommands(catalog: model.catalog, query: model.searchQuery)
            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    if model.searchQuery.trimmingCharacters(in: .whitespaces).isEmpty {
                        Text("Escribe el nombre de cualquier herramienta de Word en español o inglés.")
                            .font(.caption)
                            .foregroundStyle(surfaces.textDim)
                            .padding(.vertical, 8)
                    } else if matches.isEmpty {
                        Text("Sin resultados para \"\(model.searchQuery)\"")
                            .font(.caption)
                            .foregroundStyle(surfaces.textDim)
                            .padding(.vertical, 8)
                    } else {
                        ForEach(matches) { match in
                            Button {
                                model.executeCommand(match.control.commandId)
                                model.showCommandSearch = false
                            } label: {
                                HStack(spacing: 8) {
                                    AdaptiveRibbonHelper.officeIconImage(named: match.control.icon)
                                        .resizable()
                                        .scaledToFit()
                                        .frame(width: 18, height: 18)
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(match.control.labelEsCl)
                                            .font(.system(size: 12.5, weight: .medium))
                                            .foregroundStyle(surfaces.text)
                                        Text("\(match.tabLabelEsCl) › \(match.groupLabelEsCl)")
                                            .font(.system(size: 10.5))
                                            .foregroundStyle(surfaces.textDim)
                                    }
                                    Spacer()
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 5)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .frame(height: 260)
        }
        .padding(12)
        .frame(width: 360)
    }
}
