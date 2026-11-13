import AppKit
import CoreGraphics
import OfficeCoreKit
import SwiftUI

struct MacDocumentCanvas: NSViewRepresentable {
    @Bindable var model: DocxWorkspaceModel

    func makeNSView(context: Context) -> NSScrollView {
        let scrollView = NSScrollView()
        scrollView.hasVerticalScroller = true
        scrollView.hasHorizontalScroller = true
        scrollView.allowsMagnification = false
        scrollView.drawsBackground = true
        scrollView.backgroundColor = NSColor(srgbRed: 0.925, green: 0.925, blue: 0.925, alpha: 1.0)

        let docView = DocxPageCanvasNSView(model: model)
        docView.autoresizingMask = [.width]
        scrollView.documentView = docView

        DispatchQueue.main.async {
            scrollView.window?.makeFirstResponder(docView)
        }
        return scrollView
    }

    func updateNSView(_ scrollView: NSScrollView, context: Context) {
        guard let docView = scrollView.documentView as? DocxPageCanvasNSView else { return }
        docView.syncFromModel(viewportSize: scrollView.contentSize)
    }
}

final class DocxPageCanvasNSView: NSView, @preconcurrency NSTextInputClient {
    private let model: DocxWorkspaceModel
    private let tileCache = NSCache<NSString, CGImage>()
    private var placedPages: [PlacedPageRect] = []
    private var lastScalePxPerPt: CGFloat = 1.0
    private var markedString: String = ""
    private var caretTimer: Timer?
    private var caretVisible: Bool = true
    private var lastSyncedRevision: Int = -1
    private var lastSyncedZoom: Double = -1

    struct PlacedPageRect {
        let pageIndex: Int
        let frameInView: CGRect
        let widthPt: CGFloat
        let heightPt: CGFloat
    }

    init(model: DocxWorkspaceModel) {
        self.model = model
        super.init(frame: .zero)
        tileCache.countLimit = 220
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        if window != nil {
            startCaretBlink()
        } else {
            caretTimer?.invalidate()
            caretTimer = nil
        }
    }

    override var isFlipped: Bool { true }
    override var acceptsFirstResponder: Bool { true }

    override func becomeFirstResponder() -> Bool {
        true
    }

    private func startCaretBlink() {
        caretTimer?.invalidate()
        caretTimer = Timer.scheduledTimer(withTimeInterval: 0.53, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self else { return }
                self.caretVisible.toggle()
                self.needsDisplay = true
            }
        }
    }

    func syncFromModel(viewportSize: CGSize) {
        if lastSyncedRevision != model.docRevision || abs(lastSyncedZoom - model.zoom) > 0.001 {
            if lastSyncedRevision != model.docRevision {
                tileCache.removeAllObjects()
            }
            lastSyncedRevision = model.docRevision
            lastSyncedZoom = model.zoom
        }
        recomputeLayout(viewportSize: viewportSize)
        needsDisplay = true
    }

    private func recomputeLayout(viewportSize: CGSize) {
        let vpW = max(400, viewportSize.width)
        let vpH = max(300, viewportSize.height)
        let count = max(1, Int(model.session.pageCount()))
        var sizes: [PageSizePt] = []
        sizes.reserveCapacity(count)
        for idx in 0..<count {
            sizes.append((try? model.session.pageSizePt(pageIndex: UInt32(idx))) ?? PageSizePt(widthPt: 612, heightPt: 792))
        }
        let refW = CGFloat(sizes.first?.widthPt ?? 612)
        let sidePad: CGFloat = 28
        let baseScale = min(3.0, max(0.5, (vpW - sidePad * 2) / refW))
        let scale = baseScale * CGFloat(model.zoom)
        lastScalePxPerPt = scale

        let pageGap: CGFloat = 20
        var y: CGFloat = 24
        var maxRight: CGFloat = vpW
        var result: [PlacedPageRect] = []
        for (idx, pt) in sizes.enumerated() {
            let wPx = max(1, CGFloat(pt.widthPt) * scale)
            let hPx = max(1, CGFloat(pt.heightPt) * scale)
            let x = max(sidePad, (vpW - wPx) / 2)
            let rect = CGRect(x: x, y: y, width: wPx, height: hPx)
            result.append(PlacedPageRect(pageIndex: idx, frameInView: rect, widthPt: CGFloat(pt.widthPt), heightPt: CGFloat(pt.heightPt)))
            y += hPx + pageGap
            maxRight = max(maxRight, x + wPx + sidePad)
        }
        placedPages = result
        let targetSize = CGSize(width: max(vpW, maxRight), height: max(vpH, y + 24))
        if frame.size != targetSize {
            setFrameSize(targetSize)
        }
    }

    override func draw(_ dirtyRect: NSRect) {
        super.draw(dirtyRect)
        guard let ctx = NSGraphicsContext.current?.cgContext else { return }

        NSColor(srgbRed: 0.925, green: 0.925, blue: 0.925, alpha: 1.0).setFill()
        ctx.fill(dirtyRect)

        let backingScale = window?.backingScaleFactor ?? 2.0
        let dpiScale = Float(lastScalePxPerPt * backingScale)
        let tileSizePx = 512

        for page in placedPages {
            let pf = page.frameInView
            guard pf.intersects(dirtyRect) else { continue }

            // Page shadow + crisp white paper surface
            ctx.saveGState()
            ctx.setShadow(offset: CGSize(width: 0, height: -2), blur: 8, color: NSColor.black.withAlphaComponent(0.14).cgColor)
            NSColor.white.setFill()
            ctx.fill(pf)
            ctx.restoreGState()

            ctx.setStrokeColor(NSColor(srgbRed: 0.88, green: 0.88, blue: 0.88, alpha: 1).cgColor)
            ctx.setLineWidth(1)
            ctx.stroke(pf)

            let fullW = max(1, Int((page.widthPt * CGFloat(dpiScale)).rounded()))
            let fullH = max(1, Int((page.heightPt * CGFloat(dpiScale)).rounded()))

            let visInPage = pf.intersection(dirtyRect)
            let relLeftPx = max(0, Int(floor((visInPage.minX - pf.minX) * backingScale)))
            let relTopPx = max(0, Int(floor((visInPage.minY - pf.minY) * backingScale)))
            let relRightPx = min(fullW, Int(ceil((visInPage.maxX - pf.minX) * backingScale)))
            let relBottomPx = min(fullH, Int(ceil((visInPage.maxY - pf.minY) * backingScale)))

            let colStart = relLeftPx / tileSizePx
            let rowStart = relTopPx / tileSizePx
            let colEnd = max(colStart, (relRightPx - 1) / tileSizePx)
            let rowEnd = max(rowStart, (relBottomPx - 1) / tileSizePx)

            for row in rowStart...rowEnd {
                let ty = row * tileSizePx
                if ty >= fullH { continue }
                let th = min(tileSizePx, fullH - ty)
                for col in colStart...colEnd {
                    let tx = col * tileSizePx
                    if tx >= fullW { continue }
                    let tw = min(tileSizePx, fullW - tx)

                    if let tileImage = cachedTileImage(
                        pageIndex: page.pageIndex,
                        dpiScale: dpiScale,
                        x: tx,
                        y: ty,
                        w: tw,
                        h: th
                    ) {
                        let dst = CGRect(
                            x: pf.minX + CGFloat(tx) / backingScale,
                            y: pf.minY + CGFloat(ty) / backingScale,
                            width: CGFloat(tw) / backingScale,
                            height: CGFloat(th) / backingScale
                        )
                        // In a flipped NSView, CGContext.draw expects inverted image coordinates
                        ctx.saveGState()
                        ctx.translateBy(x: 0, y: dst.maxY + dst.minY)
                        ctx.scaleBy(x: 1, y: -1)
                        ctx.draw(tileImage, in: dst)
                        ctx.restoreGState()
                    }
                }
            }
        }

        // Draw Selection Rectangles
        ctx.saveGState()
        ctx.setFillColor(NSColor(srgbRed: 0.23, green: 0.36, blue: 0.86, alpha: 0.28).cgColor)
        for sel in model.selection.selectionRects {
            guard Int(sel.pageIndex) < placedPages.count else { continue }
            let pf = placedPages[Int(sel.pageIndex)].frameInView
            let rect = CGRect(
                x: pf.minX + CGFloat(sel.xPt) * lastScalePxPerPt,
                y: pf.minY + CGFloat(sel.yPt) * lastScalePxPerPt,
                width: CGFloat(sel.widthPt) * lastScalePxPerPt,
                height: CGFloat(sel.heightPt) * lastScalePxPerPt
            )
            ctx.fill(rect)
        }
        ctx.restoreGState()

        // Draw Caret
        if model.selection.isCollapsed, caretVisible, let caret = model.selection.caret,
           Int(caret.pageIndex) < placedPages.count {
            let pf = placedPages[Int(caret.pageIndex)].frameInView
            let cx = pf.minX + CGFloat(caret.xPt) * lastScalePxPerPt
            let cy = pf.minY + CGFloat(caret.topPt) * lastScalePxPerPt
            let ch = max(14, CGFloat(caret.heightPt) * lastScalePxPerPt)
            ctx.saveGState()
            ctx.setFillColor(NSColor(srgbRed: 0.094, green: 0.353, blue: 0.741, alpha: 1.0).cgColor)
            ctx.fill(CGRect(x: cx, y: cy, width: 2.0, height: ch))
            ctx.restoreGState()
        }
    }

    private func cachedTileImage(
        pageIndex: Int,
        dpiScale: Float,
        x: Int,
        y: Int,
        w: Int,
        h: Int
    ) -> CGImage? {
        let bucket = Int((dpiScale * 100).rounded())
        let key = "\(model.docRevision):\(pageIndex):\(bucket):\(x):\(y):\(w):\(h)" as NSString
        if let hit = tileCache.object(forKey: key) {
            return hit
        }
        guard let rgba = try? model.session.renderTile(req: TileRequest(
            pageIndex: UInt32(pageIndex),
            dpiScale: dpiScale,
            tileXPx: UInt32(x),
            tileYPx: UInt32(y),
            tileWPx: UInt32(w),
            tileHPx: UInt32(h),
            darkPage: false
        )), rgba.count == w * h * 4 else {
            return nil
        }

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue)
        var premultiplied = rgba
        premultiplied.withUnsafeMutableBytes { buf in
            let ptr = buf.bindMemory(to: UInt8.self)
            for idx in stride(from: 0, to: ptr.count, by: 4) {
                let a = UInt16(ptr[idx + 3])
                if a < 255 {
                    ptr[idx] = UInt8((UInt16(ptr[idx]) * a) / 255)
                    ptr[idx + 1] = UInt8((UInt16(ptr[idx + 1]) * a) / 255)
                    ptr[idx + 2] = UInt8((UInt16(ptr[idx + 2]) * a) / 255)
                }
            }
        }
        guard let provider = CGDataProvider(data: premultiplied as CFData),
              let cgImage = CGImage(
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
        tileCache.setObject(cgImage, forKey: key)
        return cgImage
    }

    // MARK: Mouse & Trackpad Interaction

    override func mouseDown(with event: NSEvent) {
        window?.makeFirstResponder(self)
        let local = convert(event.locationInWindow, from: nil)
        guard let hit = hitPagePoint(local) else { return }
        caretVisible = true
        model.tapAt(pageIndex: UInt32(hit.pageIndex), xPt: Float(hit.xPt), yPt: Float(hit.yPt), clickCount: event.clickCount)
        needsDisplay = true
    }

    override func mouseDragged(with event: NSEvent) {
        let local = convert(event.locationInWindow, from: nil)
        guard let hit = hitPagePoint(local) else { return }
        model.dragSelection(isAnchor: false, pageIndex: UInt32(hit.pageIndex), xPt: Float(hit.xPt), yPt: Float(hit.yPt))
        needsDisplay = true
    }

    override func magnify(with event: NSEvent) {
        model.clampAndSetZoom(model.zoom * (1.0 + Double(event.magnification)))
    }

    private func hitPagePoint(_ point: CGPoint) -> (pageIndex: Int, xPt: CGFloat, yPt: CGFloat)? {
        guard lastScalePxPerPt > 0 else { return nil }
        for page in placedPages where page.frameInView.contains(point) {
            let xPt = min(page.widthPt, max(0, (point.x - page.frameInView.minX) / lastScalePxPerPt))
            let yPt = min(page.heightPt, max(0, (point.y - page.frameInView.minY) / lastScalePxPerPt))
            return (page.pageIndex, xPt, yPt)
        }
        return nil
    }

    // MARK: Keyboard & Spanish IME (NSTextInputClient)

    override func keyDown(with event: NSEvent) {
        if let chars = event.charactersIgnoringModifiers,
           event.modifierFlags.contains(.command) {
            switch chars.lowercased() {
            case "b": model.executeCommand("format.bold"); return
            case "i": model.executeCommand("format.italic"); return
            case "u": model.executeCommand("format.underline"); return
            case "a": model.executeCommand("select.all"); return
            case "c": model.executeCommand("edit.copy"); return
            case "x": model.executeCommand("edit.cut"); return
            case "v": model.executeCommand("edit.paste"); return
            case "z":
                model.executeCommand(event.modifierFlags.contains(.shift) ? "edit.redo" : "edit.undo")
                return
            case "y": model.executeCommand("edit.redo"); return
            case "k": model.showCommandSearch.toggle(); return
            default: break
            }
        }
        interpretKeyEvents([event])
    }

    func insertText(_ string: Any, replacementRange: NSRange) {
        let text: String
        if let s = string as? String {
            text = s
        } else if let attr = string as? NSAttributedString {
            text = attr.string
        } else {
            return
        }
        if !markedString.isEmpty {
            model.deleteSurrounding(before: UInt32(markedString.count), after: 0)
            markedString = ""
        }
        if text == "\n" || text == "\r" {
            model.executeCommand("text.newParagraph")
        } else if !text.isEmpty {
            model.commitImeText(text, joinWithPrevious: true)
        }
        caretVisible = true
        needsDisplay = true
    }

    func setMarkedText(_ string: Any, selectedRange: NSRange, replacementRange: NSRange) {
        let next: String
        if let s = string as? String {
            next = s
        } else if let attr = string as? NSAttributedString {
            next = attr.string
        } else {
            next = ""
        }
        if !markedString.isEmpty {
            model.deleteSurrounding(before: UInt32(markedString.count), after: 0)
            markedString = ""
        }
        if !next.isEmpty {
            model.commitImeText(next, joinWithPrevious: false)
            markedString = next
        }
        needsDisplay = true
    }

    func unmarkText() {
        markedString = ""
    }

    func selectedRange() -> NSRange { NSRange(location: 0, length: 0) }
    func markedRange() -> NSRange {
        markedString.isEmpty ? NSRange(location: NSNotFound, length: 0) : NSRange(location: 0, length: markedString.count)
    }
    func hasMarkedText() -> Bool { !markedString.isEmpty }
    func attributedSubstring(forProposedRange range: NSRange, actualRange: NSRangePointer?) -> NSAttributedString? { nil }
    func validAttributesForMarkedText() -> [NSAttributedString.Key] { [] }
    func firstRect(forCharacterRange range: NSRange, actualRange: NSRangePointer?) -> NSRect {
        guard let caret = model.selection.caret, Int(caret.pageIndex) < placedPages.count, let win = window else {
            return .zero
        }
        let pf = placedPages[Int(caret.pageIndex)].frameInView
        let pt = CGPoint(x: pf.minX + CGFloat(caret.xPt) * lastScalePxPerPt, y: pf.minY + CGFloat(caret.topPt) * lastScalePxPerPt)
        let inWindow = convert(NSRect(x: pt.x, y: pt.y, width: 2, height: 18), to: nil)
        return win.convertToScreen(inWindow)
    }
    func characterIndex(for point: NSPoint) -> Int { 0 }

    override func doCommand(by selector: Selector) {
        switch selector {
        case #selector(NSResponder.insertNewline(_:)):
            model.executeCommand("text.newParagraph")
        case #selector(NSResponder.insertTab(_:)):
            model.executeCommand("text.tab")
        case #selector(NSResponder.deleteBackward(_:)):
            model.deleteSurrounding(before: 1, after: 0)
        case #selector(NSResponder.deleteForward(_:)):
            model.deleteSurrounding(before: 0, after: 1)
        case #selector(NSResponder.moveLeft(_:)):
            model.executeCommand("caret.left")
        case #selector(NSResponder.moveRight(_:)):
            model.executeCommand("caret.right")
        case #selector(NSResponder.moveUp(_:)):
            model.executeCommand("caret.up")
        case #selector(NSResponder.moveDown(_:)):
            model.executeCommand("caret.down")
        case #selector(NSResponder.moveWordLeft(_:)):
            model.executeCommand("caret.wordLeft")
        case #selector(NSResponder.moveWordRight(_:)):
            model.executeCommand("caret.wordRight")
        case #selector(NSResponder.selectAll(_:)):
            model.executeCommand("select.all")
        default:
            super.doCommand(by: selector)
        }
        caretVisible = true
        needsDisplay = true
    }
}
