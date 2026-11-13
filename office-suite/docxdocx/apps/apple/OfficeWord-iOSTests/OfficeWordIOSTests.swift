import CoreGraphics
import OfficeCoreKit
import XCTest
@testable import DocxDocx

@MainActor
final class OfficeWordIOSTests: XCTestCase {
    func testCanvasLayoutAndTileRendering() throws {
        let store = IOSDocxStore()
        XCTAssertGreaterThanOrEqual(store.pageCount, 1)

        let layout = IOSCanvasLayoutMath.layoutPages(
            sizesPt: [CGSize(width: 612, height: 792), CGSize(width: 612, height: 935.43)],
            viewportWidth: 1024,
            scale: 1.0
        )
        XCTAssertEqual(layout.placements.count, 2)
        XCTAssertGreaterThan(layout.placements[1].rectPx.minY, layout.placements[0].rectPx.maxY)

        let cg = store.renderTileImage(
            pageIndex: 0,
            scale: 1.0,
            tileRectPx: CGRect(x: 0, y: 0, width: 256, height: 256),
            darkPage: false
        )
        XCTAssertNotNil(cg)
        XCTAssertEqual(cg?.width, 256)
    }

    func testUITextInputComposingSpanishAndBackspace() throws {
        let store = IOSDocxStore()
        let canvas = DocxUIKitCanvasContainer(store: store, isDark: false)
        canvas.setMarkedText("Ofi", selectedRange: NSRange(location: 3, length: 0))
        XCTAssertNotNil(canvas.markedTextRange)
        canvas.unmarkText()
        canvas.insertText("cio N° 88 ñandú")
        XCTAssertGreaterThanOrEqual(store.wordCount, 4)

        canvas.deleteBackward()
        XCTAssertGreaterThanOrEqual(store.wordCount, 3)
    }

    func testAdaptiveRibbonApplePencilAndChileanPresets() throws {
        let store = IOSDocxStore()
        XCTAssertEqual(store.catalog.coreTabs.count, 9)

        store.executeCommand("draw.pen")
        XCTAssertEqual(store.activeInkTool, .pen)
        store.executeCommand("draw.eraser")
        XCTAssertEqual(store.activeInkTool, .eraser)

        let oficio = try XCTUnwrap(OfficeRibbonPresets.paperSizes.first(where: { $0.id == "oficio_cl" }))
        store.executeCommand(oficio.commandId, paramsJson: oficio.paramsJson)
        let sz = try store.session.pageSizePt(pageIndex: 0)
        XCTAssertEqual(Double(sz.heightPt), 935.43, accuracy: 1.0)

        store.newFromTemplate(.oficioChile)
        XCTAssertTrue(store.documentTitle.contains("Oficio"))
        XCTAssertGreaterThan(store.wordCount, 5)
    }
}
