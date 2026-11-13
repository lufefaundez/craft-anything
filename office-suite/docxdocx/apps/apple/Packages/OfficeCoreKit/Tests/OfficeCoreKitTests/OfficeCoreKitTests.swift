import Foundation
import Testing
@testable import OfficeCoreKit

@Test("Fluent tokens match Microsoft Word 16.115 palette")
func fluentTokensParity() throws {
    #expect(OfficeTokens.version == "1.0.0")
    #expect(OfficeTokens.Radii.ribbonCard == 8)
    #expect(OfficeTokens.TouchTargets.visionosMinPt == 60)
}

@Test("Blank session initializes fonts, language pack, and renders page 0")
func blankSessionRenders() throws {
    let session = try DocxCoreBridge.makeBlankSession(locale: "es-CL")
    #expect(session.pageCount() >= 1)
    let tile = try session.renderTile(
        req: TileRequest(
            pageIndex: 0,
            dpiScale: 1.0,
            tileXPx: 0,
            tileYPx: 0,
            tileWPx: 128,
            tileHPx: 128,
            darkPage: false
        )
    )
    #expect(tile.count == 128 * 128 * 4)
}

@Test("Bundled ribbon schema loads 9 core tabs and searches Spanish commands")
func bundledRibbonSchemaLoadsAndSearches() throws {
    let catalog = try RibbonSchemaCatalog.loadBundled()
    #expect(catalog.coreTabs.count == 9)
    #expect(catalog.contextualTabs.count >= 1)
    let matches = AdaptiveRibbonHelper.searchCommands(catalog: catalog, query: "negrita")
    #expect(matches.contains(where: { $0.control.commandId == "format.bold" }))
}

@Test("WebDAV XML parser and Chilean templates work across Apple platforms")
func webDavAndTemplates() throws {
    let cfg = WebDavConfig(baseUrl: "https://dav.example.cl/files", username: "lufe", password: "pw")
    let resolved = try cfg.resolveChildUrl("Oficio_2026.docx")
    #expect(resolved.absoluteString == "https://dav.example.cl/files/Oficio_2026.docx")

    let sampleXml = """
    <D:multistatus xmlns:D="DAV:">
      <D:response><D:href>/files/Carta.docx</D:href><D:propstat><D:prop><D:displayname>Carta.docx</D:displayname><D:getcontentlength>2048</D:getcontentlength></D:prop></D:propstat></D:response>
    </D:multistatus>
    """
    let entries = AppleWebDavClient.parsePropfindXml(sampleXml, requestPath: "/files")
    #expect(entries.count == 1)
    #expect(entries[0].displayName == "Carta.docx")
    #expect(DocTemplateId.oficioChile.seedParagraphs.contains(where: { $0.contains("OFICIO ORDINARIO") }))
}
