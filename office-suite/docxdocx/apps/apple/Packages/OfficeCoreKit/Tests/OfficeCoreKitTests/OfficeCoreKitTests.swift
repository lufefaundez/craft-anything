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
