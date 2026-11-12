import Foundation

/// Registers bundled SIL OFL metric fonts (Carlito, Caladea, Liberation Sans, STIX Two Math)
/// and MPL-1.1 Spanish dictionary packs from `OfficeCoreKit`'s resource bundle into Rust.
public enum DocxCoreBridge {
    private static let lock = NSLock()
    private static nonisolated(unsafe) var assetsInitialized = false

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
}
