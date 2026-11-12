package app.ofiofi.docxdocx

import android.content.Context
import android.content.res.AssetManager
import uniffi.word_mobile_ffi.initMobileAssets
import java.io.File

/**
 * Extracts bundled SIL OFL metric fonts (Carlito, Caladea, Liberation Sans, STIX Two Math)
 * and MPL-1.1 Spanish dictionary assets on first launch, then registers them with Rust.
 */
object DocxAssetInitializer {
    private const val ASSET_VERSION = "1"
    private const val STAMP_FILE = ".ofiofi_assets_version"

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context): UInt {
        if (initialized) return 0u
        val baseDir = File(context.filesDir, "ofiofi_assets")
        val stamp = File(baseDir, STAMP_FILE)
        if (!stamp.isFile || stamp.readText().trim() != ASSET_VERSION) {
            copyAssetTree(context.assets, "fonts", File(baseDir, "fonts"))
            copyAssetTree(context.assets, "language-packs", File(baseDir, "language-packs"))
            baseDir.mkdirs()
            stamp.writeText(ASSET_VERSION)
        }
        val fontsDir = File(baseDir, "fonts").absolutePath
        val packsDir = File(baseDir, "language-packs").absolutePath
        val count = initMobileAssets(fontsDir, packsDir)
        initialized = true
        return count
    }

    private fun copyAssetTree(assets: AssetManager, assetPath: String, destDir: File) {
        val children = assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            destDir.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                destDir.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        destDir.mkdirs()
        for (child in children) {
            copyAssetTree(assets, "$assetPath/$child", File(destDir, child))
        }
    }
}
