import Foundation
import SwiftUI

/// Application wave flavors in the clean-room OfiOfi suite.
public enum OfiOfiAppFlavor: String, CaseIterable, Sendable {
    case word
    case excel
    case powerpoint

    public var displayName: String {
        switch self {
        case .word: return "DocxDocx"
        case .excel: return "XlsxXlsx"
        case .powerpoint: return "PptxPptx"
        }
    }

    public var bundleIdentifier: String {
        switch self {
        case .word: return "app.ofiofi.docxdocx"
        case .excel: return "app.ofiofi.xlsxxlsx"
        case .powerpoint: return "app.ofiofi.pptxpptx"
        }
    }

    public var engineCrateName: String {
        switch self {
        case .word: return "wordcraft"
        case .excel: return "gridcraft"
        case .powerpoint: return "deckcraft"
        }
    }

    public var primaryExtension: String {
        switch self {
        case .word: return "docx"
        case .excel: return "xlsx"
        case .powerpoint: return "pptx"
        }
    }

    public var accentHexLight: String {
        switch self {
        case .word: return "#185ABD"
        case .excel: return "#107C41"
        case .powerpoint: return "#B7472A"
        }
    }

    public var accentColorLight: Color {
        switch self {
        case .word:
            return Color(.sRGB, red: 0.0941, green: 0.3529, blue: 0.7412, opacity: 1.0)
        case .excel:
            return Color(.sRGB, red: 0.0627, green: 0.4863, blue: 0.2549, opacity: 1.0)
        case .powerpoint:
            return Color(.sRGB, red: 0.7176, green: 0.2784, blue: 0.1647, opacity: 1.0)
        }
    }
}

/// Supported user-owned storage & sync providers (zero proprietary cloud).
public enum ShellStorageTarget: String, CaseIterable, Sendable {
    case localDevice
    case icloudDrive
    case googleDrive
    case webdav

    public var labelEsCl: String {
        switch self {
        case .localDevice: return "Este dispositivo"
        case .icloudDrive: return "iCloud Drive / Archivos"
        case .googleDrive: return "Google Drive"
        case .webdav: return "Servidor WebDAV"
        }
    }
}

/// Generic adaptive shell chrome metrics shared by Word, Excel, and PowerPoint.
public enum ShellChromeMetrics {
    public static let titleBarHeight: CGFloat = 38
    public static let tabStripHeight: CGFloat = 32
    public static let ribbonCardHeight: CGFloat = 92
    public static let statusBarHeight: CGFloat = 26
    public static let phoneMinTouchTarget: CGFloat = 44
    public static let visionOSMinTouchTarget: CGFloat = 60
}

/// Shared interface that Wave 1 (`wordcraft`), Wave 2 (`gridcraft`), and Wave 3 (`deckcraft`)
/// document bridges implement to plug into the unified adaptive Ribbon/Backstage/Canvas shell.
public protocol OfficeShellDocumentBridge: AnyObject, Sendable {
    var flavor: OfiOfiAppFlavor { get }
    var unitCount: Int { get }
    func runShellCommand(id: String, paramsJson: String) throws -> String
    func saveNativeBytes() throws -> Data
    func exportPdfDocumentBytes() throws -> Data
}
