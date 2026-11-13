import Foundation

/// Registers bundled SIL OFL metric fonts (Carlito, Caladea, Liberation Sans, STIX Two Math)
/// and MPL-1.1 Spanish dictionary packs from `OfficeCoreKit`'s resource bundle into Rust.
public enum DocxCoreBridge {
    private static let lock = NSLock()
    private static nonisolated(unsafe) var assetsInitialized = false
    private static nonisolated(unsafe) var cachedFonts: [String]?

    @discardableResult
    public static func ensureAssetsInitialized() throws -> UInt32 {
        lock.lock()
        defer { lock.unlock() }
        if assetsInitialized {
            return 0
        }
        guard let resourceURL = Bundle.module.resourceURL else {
            return 0
        }
        let fontsPath = resourceURL.appendingPathComponent("fonts", isDirectory: true).path
        let packsURL = resourceURL.appendingPathComponent("language-packs", isDirectory: true)
        let packsPath = FileManager.default.fileExists(atPath: packsURL.path) ? packsURL.path : nil
        let loaded = try initMobileAssets(fontsDir: fontsPath, packsDir: packsPath)
        assetsInitialized = true
        return loaded
    }

    public static func makeBlankSession(locale: String = "es-CL") throws -> MobileDocumentSession {
        try ensureAssetsInitialized()
        return MobileDocumentSession.newBlank(regionTag: locale)
    }

    public static let preferredWordFonts: [String] = [
        "Aptos",
        "Calibri",
        "Carlito",
        "Times New Roman",
        "Caladea",
        "Cambria",
        "Arial",
        "Liberation Sans",
        "Georgia",
        "Verdana",
        "Tahoma",
        "Trebuchet MS",
        "Courier New",
        "STIX Two Math",
    ]

    public static func mergeFontFamilies(_ systemFamilies: [String]) -> [String] {
        var seen = Set<String>()
        var result: [String] = []
        for font in preferredWordFonts {
            let trimmed = font.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty, seen.insert(trimmed.lowercased()).inserted {
                result.append(trimmed)
            }
        }
        let sortedSystem = systemFamilies
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.hasPrefix(".") }
            .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
        for font in sortedSystem {
            if seen.insert(font.lowercased()).inserted {
                result.append(font)
            }
        }
        return result
    }

    public static func loadAllSystemFonts() -> [String] {
        lock.lock()
        if let existing = cachedFonts {
            lock.unlock()
            return existing
        }
        lock.unlock()
        _ = try? ensureAssetsInitialized()
        let merged = mergeFontFamilies(loadAndListSystemFonts())
        lock.lock()
        cachedFonts = merged
        lock.unlock()
        return merged
    }
}
