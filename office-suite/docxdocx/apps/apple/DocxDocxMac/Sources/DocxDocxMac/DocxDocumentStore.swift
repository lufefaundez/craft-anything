import AppKit
import Foundation
import Observation
import OfficeCoreKit
import PDFKit
import UniformTypeIdentifiers

@MainActor
@Observable
final class DocxWorkspaceModel {
    var session: MobileDocumentSession
    let catalog: RibbonSchemaCatalog

    var documentTitle: String = "Documento1.docx"
    var fileURL: URL?
    var docRevision: Int = 0
    var zoom: Double = 1.0
    var isMobileReflow: Bool = false
    var showBackstage: Bool = false
    var showCommandSearch: Bool = false
    var searchQuery: String = ""
    var selectedTabIndex: Int = 0

    var isDirty: Bool = false
    var canUndo: Bool = false
    var canRedo: Bool = false
    var pageCount: UInt32 = 1
    var wordCount: UInt64 = 0
    var selection: SelectionGeometry
    var activeToggleIds: Set<String> = []
    var activeContexts: Set<String> = []
    var statusBanner: String?

    init() {
        let initialSession = (try? DocxCoreBridge.makeBlankSession(locale: "es-CL"))
            ?? MobileDocumentSession.newBlank(regionTag: "es-CL")
        let loadedCatalog = (try? RibbonSchemaCatalog.loadBundled())
            ?? RibbonSchemaCatalog(coreTabs: [], contextualTabs: [], qat: [], smartphoneQuickBar: [])
        self.session = initialSession
        self.catalog = loadedCatalog
        self.selection = initialSession.getSelectionGeometry()
        self.pageCount = initialSession.pageCount()
        self.wordCount = initialSession.wordCount()
    }

    var visibleTabs: [RibbonTabSpec] {
        AdaptiveRibbonHelper.visibleTabs(catalog: catalog, activeContexts: activeContexts)
    }

    func applyOutcome(_ outcome: CommandOutcome) {
        selection = outcome.selection
        isDirty = outcome.dirty
        canUndo = outcome.canUndo
        canRedo = outcome.canRedo
        pageCount = max(1, outcome.pageCount)
        wordCount = outcome.wordCount
        activeToggleIds = AdaptiveRibbonHelper.parseActiveToggleIds(formatStateJson: outcome.formatStateJson)
        activeContexts = AdaptiveRibbonHelper.parseActiveContexts(formatStateJson: outcome.formatStateJson)
        docRevision &+= 1
    }

    func executeCommand(_ commandId: String, paramsJson: String = "{}") {
        switch commandId {
        case "ui.openBackstage":
            showBackstage = true
            return
        case "file.new":
            newFromTemplate(.blank)
            return
        case "file.open":
            presentOpenPanel()
            return
        case "file.save":
            saveDocument()
            return
        case "file.saveAs":
            presentSaveAsPanel()
            return
        case "file.exportPdf":
            presentExportPdfPanel()
            return
        case "file.print":
            printDocument()
            return
        default:
            break
        }

        do {
            let outcome = try session.executeCommand(cmdId: commandId, paramsJson: paramsJson)
            applyOutcome(outcome)
        } catch {
            statusBanner = error.localizedDescription
        }
    }

    func commitImeText(_ text: String, joinWithPrevious: Bool = true) {
        do {
            let outcome = try session.imeCommitText(text: text, joinWithPrevious: joinWithPrevious)
            applyOutcome(outcome)
        } catch {
            statusBanner = error.localizedDescription
        }
    }

    func deleteSurrounding(before: UInt32, after: UInt32) {
        do {
            let outcome = try session.imeDeleteSurrounding(before: before, after: after)
            applyOutcome(outcome)
        } catch {
            statusBanner = error.localizedDescription
        }
    }

    func tapAt(pageIndex: UInt32, xPt: Float, yPt: Float, clickCount: Int) {
        do {
            let updated: SelectionGeometry
            switch clickCount {
            case 2:
                updated = try session.doubleTapAt(pageIndex: pageIndex, xPt: xPt, yPt: yPt)
            case 3:
                updated = try session.tripleTapAt(pageIndex: pageIndex, xPt: xPt, yPt: yPt)
            default:
                updated = try session.tapAt(pageIndex: pageIndex, xPt: xPt, yPt: yPt)
            }
            selection = updated
            if let fmt = try? session.executeCommand(cmdId: "format.state", paramsJson: "{}") {
                activeToggleIds = AdaptiveRibbonHelper.parseActiveToggleIds(formatStateJson: fmt.formatStateJson)
                activeContexts = AdaptiveRibbonHelper.parseActiveContexts(formatStateJson: fmt.formatStateJson)
            }
        } catch {
            statusBanner = error.localizedDescription
        }
    }

    func dragSelection(isAnchor: Bool, pageIndex: UInt32, xPt: Float, yPt: Float) {
        if let updated = try? session.dragSelectionHandle(isAnchor: isAnchor, pageIndex: pageIndex, xPt: xPt, yPt: yPt) {
            selection = updated
        }
    }

    func toggleLayoutMode() {
        let nextReflow = !isMobileReflow
        let cmd = nextReflow ? "view.webLayout" : "view.printLayout"
        if let outcome = try? session.executeCommand(cmdId: cmd, paramsJson: "{}") {
            isMobileReflow = nextReflow
            applyOutcome(outcome)
        }
    }

    func clampAndSetZoom(_ raw: Double) {
        zoom = min(4.0, max(0.5, raw))
    }

    func newFromTemplate(_ template: DocTemplateId) {
        do {
            let next = try template.makeSession(locale: "es-CL")
            replaceSession(next, title: "\(template.titleEsCl).docx", url: nil)
            showBackstage = false
        } catch {
            statusBanner = error.localizedDescription
        }
    }

    func openFile(url: URL) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        do {
            let data = try Data(contentsOf: url)
            let next = try MobileDocumentSession.openBytes(
                fileName: url.lastPathComponent,
                bytes: data,
                password: nil
            )
            replaceSession(next, title: url.lastPathComponent, url: url)
            showBackstage = false
            statusBanner = "Abierto: \(url.lastPathComponent)"
        } catch {
            statusBanner = "No se pudo abrir \(url.lastPathComponent): \(error.localizedDescription)"
        }
    }

    func openBytes(_ data: Data, filename: String) {
        do {
            let next = try MobileDocumentSession.openBytes(fileName: filename, bytes: data, password: nil)
            replaceSession(next, title: filename, url: nil)
            showBackstage = false
            statusBanner = "Descargado desde WebDAV: \(filename)"
        } catch {
            statusBanner = "Error al abrir \(filename): \(error.localizedDescription)"
        }
    }

    func presentOpenPanel() {
        let panel = NSOpenPanel()
        panel.title = "Abrir documento en DocxDocx"
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false
        panel.allowedContentTypes = [
            UTType(filenameExtension: "docx") ?? .data,
            UTType(filenameExtension: "doc") ?? .data,
            UTType(filenameExtension: "odt") ?? .data,
            .rtf,
            .plainText,
        ]
        if let icloudURL = FileManager.default.url(forUbiquityContainerIdentifier: nil)?.appendingPathComponent("Documents") {
            panel.directoryURL = icloudURL
        }
        if panel.runModal() == .OK, let url = panel.url {
            openFile(url: url)
        }
    }

    func saveDocument() {
        guard let url = fileURL else {
            presentSaveAsPanel()
            return
        }
        writeDocx(to: url)
    }

    func presentSaveAsPanel() {
        let panel = NSSavePanel()
        panel.title = "Guardar documento como .docx"
        panel.nameFieldStringValue = documentTitle.hasSuffix(".docx") ? documentTitle : "\(documentTitle).docx"
        panel.allowedContentTypes = [UTType(filenameExtension: "docx") ?? .data]
        if panel.runModal() == .OK, let url = panel.url {
            writeDocx(to: url)
        }
    }

    private func writeDocx(to url: URL) {
        do {
            let bytes = try session.saveDocxBytes()
            try bytes.write(to: url, options: .atomic)
            fileURL = url
            documentTitle = url.lastPathComponent
            isDirty = false
            showBackstage = false
            statusBanner = "Guardado en \(url.lastPathComponent)"
        } catch {
            statusBanner = "Error al guardar: \(error.localizedDescription)"
        }
    }

    func presentExportPdfPanel() {
        let panel = NSSavePanel()
        panel.title = "Exportar a PDF"
        let baseName = documentTitle.replacingOccurrences(of: ".docx", with: "", options: .caseInsensitive)
        panel.nameFieldStringValue = "\(baseName).pdf"
        panel.allowedContentTypes = [.pdf]
        if panel.runModal() == .OK, let url = panel.url {
            do {
                let pdfData = try session.exportPdfBytes()
                try pdfData.write(to: url, options: .atomic)
                showBackstage = false
                statusBanner = "PDF exportado: \(url.lastPathComponent)"
            } catch {
                statusBanner = "Error al exportar PDF: \(error.localizedDescription)"
            }
        }
    }

    func printDocument() {
        do {
            let pdfData = try session.exportPdfBytes()
            guard let pdfDoc = PDFDocument(data: pdfData) else { return }
            let info = NSPrintInfo.shared
            info.horizontalPagination = .fit
            info.verticalPagination = .automatic
            if let op = pdfDoc.printOperation(for: info, scalingMode: .pageScaleToFit, autoRotate: true) {
                op.showsPrintPanel = true
                op.showsProgressPanel = true
                op.run()
            }
        } catch {
            statusBanner = "Error al imprimir: \(error.localizedDescription)"
        }
    }

    private func replaceSession(_ next: MobileDocumentSession, title: String, url: URL?) {
        session = next
        documentTitle = title
        fileURL = url
        isMobileReflow = false
        selection = next.getSelectionGeometry()
        isDirty = false
        canUndo = false
        canRedo = false
        pageCount = max(1, next.pageCount())
        wordCount = next.wordCount()
        activeToggleIds = []
        activeContexts = []
        docRevision &+= 1
    }
}
