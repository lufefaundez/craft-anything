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
                .frame(minWidth: 960, minHeight: 680)
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
                    HStack(spacing: 6) {
                        Text("🔍 ¿Qué desea hacer? (⌘K)")
                            .font(.system(size: 11))
                            .foregroundStyle(surfaces.textDim)
                    }
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

            // 3. Floating White Fluent Ribbon Card (92 pt)
            if let tab = currentTab {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 0) {
                        ForEach(tab.groups) { group in
                            let slots = AdaptiveRibbonHelper.layoutColumns(for: group)
                            VStack(spacing: 3) {
                                HStack(alignment: .center, spacing: 3) {
                                    ForEach(slots) { slot in
                                        switch slot {
                                        case .tall(let ctrl):
                                            MacRibbonButton(
                                                control: ctrl,
                                                tall: true,
                                                checked: model.activeToggleIds.contains(ctrl.commandId),
                                                surfaces: surfaces,
                                                accent: accent,
                                                checkedBg: checkedBg
                                            ) { cmd, params in
                                                model.executeCommand(cmd, paramsJson: params)
                                            }
                                        case .stackedPair(let top, let bottom):
                                            VStack(spacing: 2) {
                                                MacRibbonButton(
                                                    control: top,
                                                    tall: false,
                                                    checked: model.activeToggleIds.contains(top.commandId),
                                                    surfaces: surfaces,
                                                    accent: accent,
                                                    checkedBg: checkedBg
                                                ) { cmd, params in
                                                    model.executeCommand(cmd, paramsJson: params)
                                                }
                                                if let bottom {
                                                    MacRibbonButton(
                                                        control: bottom,
                                                        tall: false,
                                                        checked: model.activeToggleIds.contains(bottom.commandId),
                                                        surfaces: surfaces,
                                                        accent: accent,
                                                        checkedBg: checkedBg
                                                    ) { cmd, params in
                                                        model.executeCommand(cmd, paramsJson: params)
                                                    }
                                                } else {
                                                    Spacer().frame(height: 28)
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
                            .padding(.vertical, 5)

                            Rectangle()
                                .fill(surfaces.ribbonBorder)
                                .frame(width: 1, height: 70)
                        }
                    }
                    .padding(.horizontal, 6)
                }
                .frame(height: 92)
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

            // 4. Native macOS Tile Document Canvas
            MacDocumentCanvas(model: model)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            Divider().overlay(surfaces.ribbonBorder)

            // 5. Status Bar (26 pt)
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

private struct MacRibbonButton: View {
    let control: RibbonControlSpec
    let tall: Bool
    let checked: Bool
    let surfaces: OfficeTokens.SurfacePalette
    let accent: Color
    let checkedBg: Color
    let onTrigger: (String, String) -> Void

    var body: some View {
        if !control.submenu.isEmpty {
            Menu {
                ForEach(control.submenu) { sub in
                    Button(sub.labelEsCl) {
                        onTrigger(sub.commandId, "{}")
                    }
                }
            } label: {
                buttonLabel
            }
            .menuStyle(.borderlessButton)
            .help(control.labelEsCl)
        } else {
            Button {
                onTrigger(control.commandId, "{}")
            } label: {
                buttonLabel
            }
            .buttonStyle(.plain)
            .help(control.labelEsCl)
        }
    }

    @ViewBuilder
    private var buttonLabel: some View {
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
            .frame(width: 54, height: OfficeTokens.TouchTargets.tabletLargeButton)
            .background(checked ? checkedBg : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.button))
        } else {
            AdaptiveRibbonHelper.officeIconImage(named: control.icon)
                .resizable()
                .scaledToFit()
                .frame(width: 17, height: 17)
                .frame(width: 28, height: 28)
                .background(checked ? checkedBg : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.button))
        }
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
