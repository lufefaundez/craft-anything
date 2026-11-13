import CoreGraphics
import OfficeCoreKit
import SwiftUI
import UniformTypeIdentifiers

@MainActor
@Observable
final class VisionDocumentState {
    var session: MobileDocumentSession
    var catalog: RibbonSchemaCatalog
    var documentTitle: String = "Documento1.docx"
    var activeTabIndex: Int = 0
    var pageCount: Int = 1
    var wordCount: Int = 0
    var zoom: CGFloat = 1.0
    var isFocusMode: Bool = false
    var showImporter: Bool = false
    var activeToggleIds: Set<String> = []
    var activeContexts: Set<String> = []
    var docRevision: Int = 0
    var quickInputText: String = ""

    init() {
        self.session = (try? DocxCoreBridge.makeBlankSession(locale: "es-CL"))
            ?? { fatalError("Unable to initialize blank MobileDocumentSession") }()
        self.catalog = (try? RibbonSchemaCatalog.loadBundled())
            ?? RibbonSchemaCatalog(coreTabs: [], contextualTabs: [], qat: [], smartphoneQuickBar: [])
        self.pageCount = Int(session.pageCount())
        self.wordCount = Int(session.wordCount())
    }

    func executeCommand(_ cmd: String, paramsJson: String = "{}") {
        if cmd == "view.focusMode" {
            isFocusMode.toggle()
            return
        }
        guard let outcome = try? session.executeCommand(cmdId: cmd, paramsJson: paramsJson) else { return }
        pageCount = Int(outcome.pageCount)
        wordCount = Int(outcome.wordCount)
        activeToggleIds = AdaptiveRibbonHelper.parseActiveToggleIds(formatStateJson: outcome.formatStateJson)
        activeContexts = AdaptiveRibbonHelper.parseActiveContexts(formatStateJson: outcome.formatStateJson)
        docRevision += 1
    }

    func commitQuickText() {
        guard !quickInputText.isEmpty else { return }
        if let outcome = try? session.imeCommitText(text: quickInputText, joinWithPrevious: false) {
            pageCount = Int(outcome.pageCount)
            wordCount = Int(outcome.wordCount)
            docRevision += 1
            quickInputText = ""
        }
    }

    func openDocument(url: URL) {
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed { url.stopAccessingSecurityScopedResource() }
        }
        guard let data = try? Data(contentsOf: url),
              let opened = try? MobileDocumentSession.openBytes(
                  fileName: url.lastPathComponent,
                  bytes: data,
                  password: nil
              ) else { return }
        self.session = opened
        self.documentTitle = url.lastPathComponent
        self.pageCount = Int(opened.pageCount())
        self.wordCount = Int(opened.wordCount())
        self.docRevision += 1
    }

    func renderPageImage(pageIndex: Int, scale: CGFloat = 1.5) -> CGImage? {
        guard let size = try? session.pageSizePt(pageIndex: UInt32(max(0, pageIndex))) else { return nil }
        let widthPx = max(1, Int(round(CGFloat(size.widthPt) * scale)))
        let heightPx = max(1, Int(round(CGFloat(size.heightPt) * scale)))
        guard widthPx <= 4096, heightPx <= 4096 else { return nil }
        let req = TileRequest(
            pageIndex: UInt32(pageIndex),
            dpiScale: Float(scale),
            tileXPx: 0,
            tileYPx: 0,
            tileWPx: UInt32(widthPx),
            tileHPx: UInt32(heightPx),
            darkPage: false
        )
        guard let rgba = try? session.renderTile(req: req),
              let provider = CGDataProvider(data: rgba as CFData) else { return nil }
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue)
            .union(.byteOrder32Big)
        return CGImage(
            width: widthPx,
            height: heightPx,
            bitsPerComponent: 8,
            bitsPerPixel: 32,
            bytesPerRow: widthPx * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: bitmapInfo,
            provider: provider,
            decode: nil,
            shouldInterpolate: true,
            intent: .defaultIntent
        )
    }
}

@main
struct OfficeWordVisionApp: App {
    var body: some Scene {
        WindowGroup(id: "document", for: UUID.self) { _ in
            VisionDocumentWindow()
        } defaultValue: {
            UUID()
        }
        .defaultSize(width: 1120, height: 860)
    }
}

struct VisionDocumentWindow: View {
    @Environment(\.openWindow) private var openWindow
    @State private var state = VisionDocumentState()

    var body: some View {
        let tabs = AdaptiveRibbonHelper.visibleTabs(catalog: state.catalog, activeContexts: state.activeContexts)
        let safeIdx = min(max(0, state.activeTabIndex), max(0, tabs.count - 1))

        ScrollView(.vertical) {
            VStack(spacing: 24) {
                ForEach(0..<max(1, state.pageCount), id: \.self) { pageIdx in
                    let _ = state.docRevision
                    let pageSize = (try? state.session.pageSizePt(pageIndex: UInt32(pageIdx)))
                        ?? PageSizePt(widthPt: 612, heightPt: 792)
                    let displayW = CGFloat(pageSize.widthPt) * state.zoom
                    let displayH = CGFloat(pageSize.heightPt) * state.zoom
                    if let cgImage = state.renderPageImage(pageIndex: pageIdx) {
                        Image(decorative: cgImage, scale: 1.5)
                            .resizable()
                            .frame(width: displayW, height: displayH)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                            .shadow(radius: 12)
                    } else {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(Color.white)
                            .frame(width: displayW, height: displayH)
                    }
                }
            }
            .padding(28)
            .frame(maxWidth: .infinity)
        }
        .glassBackgroundEffect()
        .fileImporter(
            isPresented: $state.showImporter,
            allowedContentTypes: [
                UTType(filenameExtension: "docx") ?? .data,
                .rtf,
                .plainText,
            ],
            allowsMultipleSelection: false
        ) { result in
            if case let .success(urls) = result, let url = urls.first {
                state.openDocument(url: url)
            }
        }
        .ornament(attachmentAnchor: .scene(.top)) {
            if !state.isFocusMode {
                HStack(spacing: 8) {
                    Button {
                        state.showImporter = true
                    } label: {
                        Label("Archivo", systemImage: "folder")
                    }
                    .frame(minWidth: OfficeTokens.TouchTargets.visionosMinPt, minHeight: OfficeTokens.TouchTargets.visionosMinPt)
                    .buttonStyle(.borderedProminent)
                    .tint(OfficeTokens.AppAccents.Word.accentLight)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Array(tabs.enumerated()), id: \.element.id) { idx, t in
                                Button(t.labelEsCl) {
                                    state.activeTabIndex = idx
                                }
                                .frame(minWidth: OfficeTokens.TouchTargets.visionosMinPt, minHeight: OfficeTokens.TouchTargets.visionosMinPt)
                                .buttonStyle(.bordered)
                                .tint(idx == safeIdx ? OfficeTokens.AppAccents.Word.accentLight : .secondary)
                            }
                        }
                    }

                    Button {
                        openWindow(id: "document", value: UUID())
                    } label: {
                        Image(systemName: "plus.rectangle.on.rectangle")
                    }
                    .frame(minWidth: OfficeTokens.TouchTargets.visionosMinPt, minHeight: OfficeTokens.TouchTargets.visionosMinPt)
                    .buttonStyle(.bordered)
                    .help("Comparar en nueva ventana")

                    Button {
                        state.isFocusMode.toggle()
                    } label: {
                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                    }
                    .frame(minWidth: OfficeTokens.TouchTargets.visionosMinPt, minHeight: OfficeTokens.TouchTargets.visionosMinPt)
                    .buttonStyle(.bordered)
                    .help("Modo Concentración")
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .glassBackgroundEffect()
            } else {
                Button("Salir de Concentración") {
                    state.isFocusMode = false
                }
                .frame(minHeight: OfficeTokens.TouchTargets.visionosMinPt)
                .padding(8)
                .glassBackgroundEffect()
            }
        }
        .ornament(attachmentAnchor: .scene(.bottom)) {
            HStack(spacing: 12) {
                if !state.isFocusMode, tabs.indices.contains(safeIdx) {
                    ForEach(tabs[safeIdx].groups.prefix(3)) { grp in
                        ForEach(grp.controls.prefix(3)) { ctrl in
                            Button {
                                state.executeCommand(ctrl.commandId)
                            } label: {
                                AdaptiveRibbonHelper.officeIconImage(named: ctrl.icon)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 24, height: 24)
                                    .frame(minWidth: OfficeTokens.TouchTargets.visionosMinPt, minHeight: OfficeTokens.TouchTargets.visionosMinPt)
                            }
                            .buttonStyle(.bordered)
                            .tint(state.activeToggleIds.contains(ctrl.commandId) ? OfficeTokens.AppAccents.Word.accentLight : .secondary)
                        }
                    }
                }

                Divider().frame(height: 28)

                TextField("Dictar o escribir...", text: $state.quickInputText)
                    .textFieldStyle(.roundedBorder)
                    .frame(width: 200)
                    .frame(minHeight: OfficeTokens.TouchTargets.visionosMinPt)
                    .onSubmit { state.commitQuickText() }

                Text("Pág. 1 de \(state.pageCount) • \(state.wordCount) pal.")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Button("-") { state.zoom = max(0.5, state.zoom - 0.1) }
                    .frame(minWidth: OfficeTokens.TouchTargets.visionosMinPt, minHeight: OfficeTokens.TouchTargets.visionosMinPt)
                Text("\(Int(round(state.zoom * 100)))%")
                    .font(.caption.monospacedDigit())
                Button("+") { state.zoom = min(2.0, state.zoom + 0.1) }
                    .frame(minWidth: OfficeTokens.TouchTargets.visionosMinPt, minHeight: OfficeTokens.TouchTargets.visionosMinPt)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .glassBackgroundEffect()
        }
    }
}
