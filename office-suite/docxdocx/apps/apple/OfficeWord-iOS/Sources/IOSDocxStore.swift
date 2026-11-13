import CoreGraphics
import Foundation
import Observation
import OfficeCoreKit
import UIKit

public struct IOSRecentDoc: Identifiable, Codable, Hashable, Sendable {
    public let id: UUID
    public let title: String
    public let providerLabel: String
    public let pathOrUrl: String
    public let openedAt: Date

    public init(id: UUID = UUID(), title: String, providerLabel: String, pathOrUrl: String, openedAt: Date = Date()) {
        self.id = id
        self.title = title
        self.providerLabel = providerLabel
        self.pathOrUrl = pathOrUrl
        self.openedAt = openedAt
    }
}

public enum IOSModalDialogKind: String, Identifiable {
    case font
    case paragraph
    case pageSetup

    public var id: String { rawValue }
}

@MainActor
@Observable
public final class IOSDocxStore {
    public var session: MobileDocumentSession
    public var catalog: RibbonSchemaCatalog
    public var documentTitle: String = "Documento1.docx"
    public var activeFileUrl: URL?
    public var pageCount: Int = 1
    public var wordCount: Int = 0
    public var canUndo: Bool = false
    public var canRedo: Bool = false
    public var docRevision: Int = 0
    public var zoom: CGFloat = 1.0
    public var isMobileReflow: Bool = false
    public var showRuler: Bool = true
    public var activeTabIndex: Int = 0
    public var activeToggleIds: Set<String> = []
    public var activeContexts: Set<String> = []
    public var activeFontName: String = "Aptos"
    public var activeFontSize: Double = 12.0
    public var activeStyleName: String = "Normal"
    public var activeInkTool: AppleInkTool = .none
    public var selection: SelectionGeometry
    public var recentDocs: [IOSRecentDoc] = []
    public var showBackstage: Bool = false
    public var showFindReplaceBar: Bool = false
    public var activeModalDialog: IOSModalDialogKind?
    public var lastError: String?

    private var composingText: String = ""
    private let tileCache = NSCache<NSString, CGImage>()
    private let recentDefaultsKey = "app.ofiofi.docxdocx.ios.recents"

    public init() {
        let blankSession = (try? DocxCoreBridge.makeBlankSession(locale: "es-CL"))
            ?? { fatalError("Failed to initialize blank MobileDocumentSession") }()
        self.session = blankSession
        self.catalog = (try? RibbonSchemaCatalog.loadBundled())
            ?? RibbonSchemaCatalog(coreTabs: [], contextualTabs: [], qat: [], smartphoneQuickBar: [])
        self.selection = blankSession.getSelectionGeometry()
        self.pageCount = Int(blankSession.pageCount())
        self.wordCount = Int(blankSession.wordCount())
        self.tileCache.countLimit = 96
        loadRecents()
    }

    public func executeCommand(_ commandId: String, paramsJson: String = "{}") {
        if let inkTool = AppleInkTool.from(commandId: commandId) {
            activeInkTool = (activeInkTool == inkTool && inkTool != AppleInkTool.none) ? AppleInkTool.none : inkTool
            return
        }
        switch commandId {
        case "ui.openBackstage":
            showBackstage = true
            return
        case "view.ruler":
            showRuler.toggle()
            return
        case "view.zoom100":
            zoom = 1.0
            return
        case "view.webLayout":
            isMobileReflow = true
        case "view.printLayout":
            isMobileReflow = false
        case "edit.find", "edit.findReplace":
            if paramsJson == "{}" {
                showFindReplaceBar = true
                return
            }
        case "format.fontDialog":
            activeModalDialog = .font
            return
        case "para.dialog":
            activeModalDialog = .paragraph
            return
        case "layout.pageSetup":
            activeModalDialog = .pageSetup
            return
        case "file.print":
            printActiveDocument()
            return
        default:
            break
        }

        do {
            let outcome = try session.executeCommand(cmdId: commandId, paramsJson: paramsJson)
            applyOutcome(outcome)
        } catch {
            lastError = error.localizedDescription
        }
    }

    public func imeCommitText(_ text: String) {
        do {
            if !composingText.isEmpty {
                _ = try session.imeDeleteSurrounding(before: UInt32(composingText.count), after: 0)
                composingText = ""
            }
            let outcome = try session.imeCommitText(text: text, joinWithPrevious: false)
            applyOutcome(outcome)
        } catch {
            lastError = error.localizedDescription
        }
    }

    public func imeSetComposing(_ text: String) {
        do {
            if !composingText.isEmpty {
                _ = try session.imeDeleteSurrounding(before: UInt32(composingText.count), after: 0)
            }
            composingText = text
            let outcome = try session.imeCommitText(text: text, joinWithPrevious: false)
            applyOutcome(outcome)
        } catch {
            lastError = error.localizedDescription
        }
    }

    public func imeBackspace() {
        do {
            if !composingText.isEmpty {
                composingText.removeLast()
            }
            let outcome = try session.imeDeleteSurrounding(before: 1, after: 0)
            applyOutcome(outcome)
        } catch {
            lastError = error.localizedDescription
        }
    }

    public func currentComposingText() -> String {
        composingText
    }

    public func tapAt(pageIndex: Int, xPt: Float, yPt: Float, tapCount: Int) {
        do {
            let p = UInt32(max(0, pageIndex))
            switch tapCount {
            case 2: selection = try session.doubleTapAt(pageIndex: p, xPt: xPt, yPt: yPt)
            case 3: selection = try session.tripleTapAt(pageIndex: p, xPt: xPt, yPt: yPt)
            default: selection = try session.tapAt(pageIndex: p, xPt: xPt, yPt: yPt)
            }
            pageCount = Int(session.pageCount())
            wordCount = Int(session.wordCount())
            docRevision += 1
        } catch {
            lastError = error.localizedDescription
        }
    }

    public func newFromTemplate(_ template: DocTemplateId) {
        do {
            let next = try template.makeSession(locale: "es-CL")
            replaceSession(next, title: "\(template.titleEsCl).docx", fileUrl: nil, providerLabel: "Plantilla")
            showBackstage = false
        } catch {
            lastError = error.localizedDescription
        }
    }

    public func openSecurityScopedUrl(_ url: URL) {
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed { url.stopAccessingSecurityScopedResource() }
        }
        do {
            let data = try Data(contentsOf: url)
            let next = try MobileDocumentSession.openBytes(fileName: url.lastPathComponent, bytes: data, password: nil)
            let provider = url.path.contains("Mobile Documents") ? "iCloud Drive" : "Archivos / Drive"
            replaceSession(next, title: url.lastPathComponent, fileUrl: url, providerLabel: provider)
            showBackstage = false
        } catch {
            lastError = error.localizedDescription
        }
    }

    public func openBytes(_ data: Data, filename: String, providerLabel: String, origin: String) {
        do {
            let next = try MobileDocumentSession.openBytes(fileName: filename, bytes: data, password: nil)
            replaceSession(next, title: filename, fileUrl: nil, providerLabel: providerLabel, customOrigin: origin)
            showBackstage = false
        } catch {
            lastError = error.localizedDescription
        }
    }

    public func saveDocxBytes() throws -> Data {
        let data = try session.saveDocxBytes()
        if let activeFileUrl {
            let accessed = activeFileUrl.startAccessingSecurityScopedResource()
            defer {
                if accessed { activeFileUrl.stopAccessingSecurityScopedResource() }
            }
            try data.write(to: activeFileUrl, options: .atomic)
        }
        return data
    }

    public func exportPdfBytes() throws -> Data {
        try session.exportPdfBytes()
    }

    public func printActiveDocument() {
        guard let pdfData = try? session.exportPdfBytes(),
              UIPrintInteractionController.canPrint(pdfData) else { return }
        let controller = UIPrintInteractionController.shared
        let info = UIPrintInfo(dictionary: nil)
        info.outputType = .general
        info.jobName = documentTitle
        controller.printInfo = info
        controller.printingItem = pdfData
        controller.present(animated: true, completionHandler: nil)
    }

    public func renderTileImage(pageIndex: Int, scale: CGFloat, tileRectPx: CGRect, darkPage: Bool) -> CGImage? {
        let bucket = Int((scale * 10.0).rounded())
        let key = "\(docRevision)-\(pageIndex)-\(bucket)-\(Int(tileRectPx.minX))-\(Int(tileRectPx.minY))-\(Int(tileRectPx.width))-\(Int(tileRectPx.height))-\(darkPage ? 1 : 0)" as NSString
        if let cached = tileCache.object(forKey: key) {
            return cached
        }
        let w = max(1, Int(tileRectPx.width))
        let h = max(1, Int(tileRectPx.height))
        guard let rgba = try? session.renderTile(
            req: TileRequest(
                pageIndex: UInt32(max(0, pageIndex)),
                dpiScale: Float(scale),
                tileXPx: UInt32(max(0, Int(tileRectPx.minX))),
                tileYPx: UInt32(max(0, Int(tileRectPx.minY))),
                tileWPx: UInt32(w),
                tileHPx: UInt32(h),
                darkPage: darkPage
            )
        ) else {
            return nil
        }
        guard let provider = CGDataProvider(data: rgba as CFData) else { return nil }
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue)
        guard let image = CGImage(
            width: w,
            height: h,
            bitsPerComponent: 8,
            bitsPerPixel: 32,
            bytesPerRow: w * 4,
            space: colorSpace,
            bitmapInfo: bitmapInfo,
            provider: provider,
            decode: nil,
            shouldInterpolate: true,
            intent: .defaultIntent
        ) else {
            return nil
        }
        tileCache.setObject(image, forKey: key)
        return image
    }

    private func replaceSession(
        _ next: MobileDocumentSession,
        title: String,
        fileUrl: URL?,
        providerLabel: String,
        customOrigin: String? = nil
    ) {
        session = next
        documentTitle = title
        activeFileUrl = fileUrl
        pageCount = Int(next.pageCount())
        wordCount = Int(next.wordCount())
        canUndo = false
        canRedo = false
        activeToggleIds = []
        activeContexts = []
        selection = next.getSelectionGeometry()
        docRevision += 1
        tileCache.removeAllObjects()

        let origin = customOrigin ?? fileUrl?.path ?? title
        let entry = IOSRecentDoc(title: title, providerLabel: providerLabel, pathOrUrl: origin)
        recentDocs.removeAll(where: { $0.pathOrUrl == origin })
        recentDocs.insert(entry, at: 0)
        if recentDocs.count > 12 { recentDocs.removeLast(recentDocs.count - 12) }
        saveRecents()
    }

    private func applyOutcome(_ outcome: CommandOutcome) {
        selection = outcome.selection
        pageCount = Int(outcome.pageCount)
        wordCount = Int(outcome.wordCount)
        canUndo = outcome.canUndo
        canRedo = outcome.canRedo
        activeToggleIds = AdaptiveRibbonHelper.parseActiveToggleIds(formatStateJson: outcome.formatStateJson)
        activeContexts = AdaptiveRibbonHelper.parseActiveContexts(formatStateJson: outcome.formatStateJson)
        let summary = AdaptiveRibbonHelper.parseFormatSummary(formatStateJson: outcome.formatStateJson)
        activeFontName = summary.font
        activeFontSize = summary.size
        activeStyleName = summary.style
        docRevision += 1
        tileCache.removeAllObjects()
    }

    private func loadRecents() {
        guard let data = UserDefaults.standard.data(forKey: recentDefaultsKey),
              let decoded = try? JSONDecoder().decode([IOSRecentDoc].self, from: data) else { return }
        recentDocs = decoded
    }

    private func saveRecents() {
        if let data = try? JSONEncoder().encode(recentDocs) {
            UserDefaults.standard.set(data, forKey: recentDefaultsKey)
        }
    }
}
