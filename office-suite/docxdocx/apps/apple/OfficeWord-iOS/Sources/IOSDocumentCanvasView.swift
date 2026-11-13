import OfficeCoreKit
import PencilKit
import SwiftUI
import UIKit

public struct IOSCanvasPagePlacement {
    public let pageIndex: Int
    public let rectPx: CGRect
}

public enum IOSCanvasLayoutMath {
    public static func layoutPages(
        sizesPt: [CGSize],
        viewportWidth: CGFloat,
        scale: CGFloat,
        pageGap: CGFloat = 18.0
    ) -> (placements: [IOSCanvasPagePlacement], contentSize: CGSize) {
        var y: CGFloat = 18.0
        var maxWidth: CGFloat = viewportWidth
        var placements: [IOSCanvasPagePlacement] = []
        placements.reserveCapacity(sizesPt.count)
        for (idx, pt) in sizesPt.enumerated() {
            let w = pt.width * scale
            let h = pt.height * scale
            let x = max(16.0, (viewportWidth - w) / 2.0)
            placements.append(IOSCanvasPagePlacement(pageIndex: idx, rectPx: CGRect(x: x, y: y, width: w, height: h)))
            maxWidth = max(maxWidth, x + w + 16.0)
            y += h + pageGap
        }
        return (placements, CGSize(width: maxWidth, height: max(y + 24.0, 200.0)))
    }
}

public struct IOSDocumentCanvasView: UIViewRepresentable {
    public let store: IOSDocxStore
    public let isDark: Bool

    public init(store: IOSDocxStore, isDark: Bool) {
        self.store = store
        self.isDark = isDark
    }

    public func makeUIView(context: Context) -> DocxUIKitCanvasContainer {
        DocxUIKitCanvasContainer(store: store, isDark: isDark)
    }

    public func updateUIView(_ uiView: DocxUIKitCanvasContainer, context: Context) {
        uiView.syncState(store: store, isDark: isDark)
    }
}

final class DocxIndexedTextPosition: UITextPosition {
    let offset: Int
    init(offset: Int) { self.offset = offset }
}

final class DocxIndexedTextRange: UITextRange {
    let startPos: DocxIndexedTextPosition
    let endPos: DocxIndexedTextPosition

    init(start: Int, end: Int) {
        self.startPos = DocxIndexedTextPosition(offset: start)
        self.endPos = DocxIndexedTextPosition(offset: end)
    }

    override var start: UITextPosition { startPos }
    override var end: UITextPosition { endPos }
    override var isEmpty: Bool { startPos.offset == endPos.offset }
}

@MainActor
public final class DocxUIKitCanvasContainer: UIView, UITextInput, UIKeyInput, UIScrollViewDelegate, UIContextMenuInteractionDelegate {
    private var store: IOSDocxStore
    private var isDark: Bool
    private var lastRevision: Int = -1
    private var lastZoom: CGFloat = -1
    private var lastInkTool: AppleInkTool = .none
    private var currentFitScale: CGFloat = 1.0

    private let scrollView = UIScrollView()
    private let pageCanvasView = DocxRenderedPageView()
    private let pencilCanvasView = PKCanvasView()
    private var placedPages: [IOSCanvasPagePlacement] = []

    // UITextInput backing state
    private let inputToken = UITextInputStringTokenizer()
    private var markedRangeInternal: NSRange?
    public var inputDelegate: UITextInputDelegate?

    public init(store: IOSDocxStore, isDark: Bool) {
        self.store = store
        self.isDark = isDark
        super.init(frame: .zero)
        setupHierarchy()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func setupHierarchy() {
        backgroundColor = isDark ? UIColor(white: 0.14, alpha: 1) : UIColor(red: 0.94, green: 0.95, blue: 0.97, alpha: 1)

        scrollView.frame = bounds
        scrollView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        scrollView.alwaysBounceVertical = true
        scrollView.delegate = self
        addSubview(scrollView)

        pageCanvasView.backgroundColor = .clear
        scrollView.addSubview(pageCanvasView)

        pencilCanvasView.backgroundColor = .clear
        pencilCanvasView.isOpaque = false
        pencilCanvasView.drawingPolicy = .anyInput
        pencilCanvasView.isUserInteractionEnabled = false
        scrollView.addSubview(pencilCanvasView)

        let tap1 = UITapGestureRecognizer(target: self, action: #selector(handleSingleTap(_:)))
        let tap2 = UITapGestureRecognizer(target: self, action: #selector(handleDoubleTap(_:)))
        tap2.numberOfTapsRequired = 2
        tap1.require(toFail: tap2)
        scrollView.addGestureRecognizer(tap1)
        scrollView.addGestureRecognizer(tap2)

        let pinch = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
        scrollView.addGestureRecognizer(pinch)

        let contextInteraction = UIContextMenuInteraction(delegate: self)
        scrollView.addInteraction(contextInteraction)
    }

    public func syncState(store: IOSDocxStore, isDark: Bool) {
        self.store = store
        self.isDark = isDark
        backgroundColor = isDark ? UIColor(white: 0.14, alpha: 1) : UIColor(red: 0.94, green: 0.95, blue: 0.97, alpha: 1)

        if lastInkTool != store.activeInkTool {
            lastInkTool = store.activeInkTool
            configurePencilTool(store.activeInkTool)
        }

        if lastRevision != store.docRevision || abs(lastZoom - store.zoom) > 0.01 || abs(pageCanvasView.bounds.width - scrollView.bounds.width) > 1.0 {
            lastRevision = store.docRevision
            lastZoom = store.zoom
            relayoutPages()
        }
    }

    override public func layoutSubviews() {
        super.layoutSubviews()
        relayoutPages()
    }

    private func relayoutPages() {
        let vpW = max(bounds.width, 320.0)
        var sizesPt: [CGSize] = []
        let count = max(1, store.pageCount)
        for idx in 0..<count {
            if let sz = try? store.session.pageSizePt(pageIndex: UInt32(idx)) {
                sizesPt.append(CGSize(width: CGFloat(sz.widthPt), height: CGFloat(sz.heightPt)))
            } else {
                sizesPt.append(CGSize(width: 612, height: 792))
            }
        }
        let firstW = sizesPt.first?.width ?? 612.0
        let fitScale = max(0.35, min((vpW - 36.0) / firstW, 2.2)) * store.zoom
        let (placements, contentSize) = IOSCanvasLayoutMath.layoutPages(
            sizesPt: sizesPt,
            viewportWidth: vpW,
            scale: fitScale
        )
        placedPages = placements
        currentFitScale = fitScale
        scrollView.contentSize = contentSize
        let contentFrame = CGRect(origin: .zero, size: contentSize)
        pageCanvasView.frame = contentFrame
        pencilCanvasView.frame = contentFrame
        pageCanvasView.update(
            store: store,
            placements: placements,
            scale: fitScale,
            isDark: isDark
        )
        publishViewportMetrics()
    }

    public func scrollViewDidScroll(_ scrollView: UIScrollView) {
        publishViewportMetrics()
    }

    private func publishViewportMetrics() {
        let activeIdx = Int(store.selection.caret?.pageIndex ?? 0)
        guard let activePlacement = placedPages.first(where: { $0.pageIndex == activeIdx }) ?? placedPages.first else {
            return
        }
        let next = OfficeRulerViewportMetrics(
            pageLeftPx: activePlacement.rectPx.minX - scrollView.contentOffset.x,
            pageWidthPx: activePlacement.rectPx.width,
            pageTopPx: activePlacement.rectPx.minY - scrollView.contentOffset.y,
            pageHeightPx: activePlacement.rectPx.height,
            scalePxPerPt: currentFitScale
        )
        if abs(store.viewportMetrics.pageLeftPx - next.pageLeftPx) > 0.5
            || abs(store.viewportMetrics.pageTopPx - next.pageTopPx) > 0.5
            || abs(store.viewportMetrics.pageWidthPx - next.pageWidthPx) > 0.5
            || abs(store.viewportMetrics.scalePxPerPt - next.scalePxPerPt) > 0.002 {
            let capturedStore = store
            Task { @MainActor in
                capturedStore.viewportMetrics = next
            }
        }
    }

    public func contextMenuInteraction(
        _ interaction: UIContextMenuInteraction,
        configurationForMenuAtLocation location: CGPoint
    ) -> UIContextMenuConfiguration? {
        let ptInCanvas = scrollView.convert(location, to: pageCanvasView)
        dispatchTap(at: ptInCanvas, tapCount: 1)
        return UIContextMenuConfiguration(identifier: nil, previewProvider: nil) { [weak self] _ in
            guard let self else { return nil }
            let cut = UIAction(title: "Cortar", image: UIImage(systemName: "scissors")) { _ in
                self.store.executeCommand("edit.cut")
            }
            let copy = UIAction(title: "Copiar", image: UIImage(systemName: "doc.on.doc")) { _ in
                self.store.executeCommand("edit.copy")
            }
            let paste = UIAction(title: "Pegar", image: UIImage(systemName: "doc.on.clipboard")) { _ in
                self.store.executeCommand("edit.paste")
            }
            let comment = UIAction(title: "Nuevo comentario...", image: UIImage(systemName: "text.bubble")) { _ in
                self.store.executeCommand("review.newComment")
            }
            let font = UIAction(title: "Fuente...", image: UIImage(systemName: "textformat")) { _ in
                self.store.executeCommand("format.fontDialog")
            }
            let para = UIAction(title: "Párrafo...", image: UIImage(systemName: "paragraphsign")) { _ in
                self.store.executeCommand("para.dialog")
            }
            let printDoc = UIAction(title: "Imprimir...", image: UIImage(systemName: "printer")) { _ in
                self.store.executeCommand("file.print")
            }
            return UIMenu(title: "", children: [
                UIMenu(options: .displayInline, children: [cut, copy, paste]),
                UIMenu(options: .displayInline, children: [comment, font, para]),
                UIMenu(options: .displayInline, children: [printDoc]),
            ])
        }
    }

    private func configurePencilTool(_ tool: AppleInkTool) {
        pencilCanvasView.isUserInteractionEnabled = (tool != .none)
        switch tool {
        case .pen:
            pencilCanvasView.tool = PKInkingTool(.pen, color: .systemBlue, width: 3)
        case .pencil:
            pencilCanvasView.tool = PKInkingTool(.pencil, color: .darkGray, width: 3)
        case .highlighter:
            pencilCanvasView.tool = PKInkingTool(.marker, color: UIColor.yellow.withAlphaComponent(0.45), width: 16)
        case .eraser:
            pencilCanvasView.tool = PKEraserTool(.vector)
        case .lasso:
            pencilCanvasView.tool = PKLassoTool()
        case .none:
            break
        }
    }

    @objc private func handleSingleTap(_ gr: UITapGestureRecognizer) {
        becomeFirstResponder()
        dispatchTap(at: gr.location(in: pageCanvasView), tapCount: 1)
    }

    @objc private func handleDoubleTap(_ gr: UITapGestureRecognizer) {
        becomeFirstResponder()
        dispatchTap(at: gr.location(in: pageCanvasView), tapCount: 2)
    }

    @objc private func handlePinch(_ gr: UIPinchGestureRecognizer) {
        if gr.state == .changed {
            let next = max(0.5, min(3.5, store.zoom * gr.scale))
            store.zoom = next
            gr.scale = 1.0
        }
    }

    private func dispatchTap(at point: CGPoint, tapCount: Int) {
        for placement in placedPages where placement.rectPx.contains(point) {
            let firstW = (try? store.session.pageSizePt(pageIndex: UInt32(placement.pageIndex)).widthPt) ?? 612.0
            let scale = placement.rectPx.width / CGFloat(firstW)
            let xPt = Float((point.x - placement.rectPx.minX) / scale)
            let yPt = Float((point.y - placement.rectPx.minY) / scale)
            store.tapAt(pageIndex: placement.pageIndex, xPt: xPt, yPt: yPt, tapCount: tapCount)
            return
        }
        if let first = placedPages.first {
            store.tapAt(pageIndex: first.pageIndex, xPt: 72, yPt: 72, tapCount: tapCount)
        }
    }

    // MARK: - UIResponder / UIKeyInput / UITextInput

    override public var canBecomeFirstResponder: Bool { true }
    public var hasText: Bool { true }

    public func insertText(_ text: String) {
        markedRangeInternal = nil
        if text == "\n" {
            store.executeCommand("text.newParagraph")
        } else if text == "\t" {
            store.executeCommand("text.tab")
        } else {
            store.imeCommitText(text)
        }
    }

    public func deleteBackward() {
        markedRangeInternal = nil
        store.imeBackspace()
    }

    public var selectedTextRange: UITextRange? {
        get { DocxIndexedTextRange(start: 0, end: 0) }
        set {}
    }

    public var markedTextRange: UITextRange? {
        guard let r = markedRangeInternal, r.length > 0 else { return nil }
        return DocxIndexedTextRange(start: r.location, end: r.location + r.length)
    }

    public var markedTextStyle: [NSAttributedString.Key: Any]? {
        get { nil }
        set {}
    }

    public func setMarkedText(_ markedText: String?, selectedRange: NSRange) {
        let s = markedText ?? ""
        markedRangeInternal = s.isEmpty ? nil : NSRange(location: 0, length: s.utf16.count)
        store.imeSetComposing(s)
    }

    public func unmarkText() {
        let pending = store.currentComposingText()
        markedRangeInternal = nil
        if !pending.isEmpty {
            store.imeCommitText(pending)
        }
    }

    public func text(in range: UITextRange) -> String? { "" }
    public func replace(_ range: UITextRange, withText text: String) { insertText(text) }
    public var beginningOfDocument: UITextPosition { DocxIndexedTextPosition(offset: 0) }
    public var endOfDocument: UITextPosition { DocxIndexedTextPosition(offset: 0) }
    public func textRange(from fromPosition: UITextPosition, to toPosition: UITextPosition) -> UITextRange? { DocxIndexedTextRange(start: 0, end: 0) }
    public func position(from position: UITextPosition, offset: Int) -> UITextPosition? { DocxIndexedTextPosition(offset: 0) }
    public func position(from position: UITextPosition, in direction: UITextLayoutDirection, offset: Int) -> UITextPosition? { DocxIndexedTextPosition(offset: 0) }
    public func compare(_ position: UITextPosition, to other: UITextPosition) -> ComparisonResult { .orderedSame }
    public func offset(from: UITextPosition, to toPosition: UITextPosition) -> Int { 0 }
    public var tokenizer: UITextInputTokenizer { inputToken }
    public func position(within range: UITextRange, farthestIn direction: UITextLayoutDirection) -> UITextPosition? { DocxIndexedTextPosition(offset: 0) }
    public func characterRange(byExtending position: UITextPosition, in direction: UITextLayoutDirection) -> UITextRange? { DocxIndexedTextRange(start: 0, end: 0) }
    public func baseWritingDirection(for position: UITextPosition, in direction: UITextStorageDirection) -> NSWritingDirection { .natural }
    public func setBaseWritingDirection(_ writingDirection: NSWritingDirection, for range: UITextRange) {}
    public func selectionRects(for range: UITextRange) -> [UITextSelectionRect] { [] }
    public func closestPosition(to point: CGPoint) -> UITextPosition? { DocxIndexedTextPosition(offset: 0) }
    public func closestPosition(to point: CGPoint, within range: UITextRange) -> UITextPosition? { DocxIndexedTextPosition(offset: 0) }
    public func characterRange(at point: CGPoint) -> UITextRange? { DocxIndexedTextRange(start: 0, end: 0) }

    public func firstRect(for range: UITextRange) -> CGRect { caretRect(for: beginningOfDocument) }

    public func caretRect(for position: UITextPosition) -> CGRect {
        guard let caret = store.selection.caret,
              let placement = placedPages.first(where: { $0.pageIndex == Int(caret.pageIndex) }) else {
            return CGRect(x: 96, y: 96, width: 2, height: 18)
        }
        let pageW = (try? store.session.pageSizePt(pageIndex: caret.pageIndex).widthPt) ?? 612.0
        let scale = placement.rectPx.width / CGFloat(pageW)
        let ptRect = CGRect(
            x: placement.rectPx.minX + CGFloat(caret.xPt) * scale,
            y: placement.rectPx.minY + CGFloat(caret.topPt) * scale,
            width: 2,
            height: max(12, CGFloat(caret.heightPt) * scale)
        )
        return pageCanvasView.convert(ptRect, to: self)
    }
}

final class DocxRenderedPageView: UIView {
    private var store: IOSDocxStore?
    private var placements: [IOSCanvasPagePlacement] = []
    private var scaleFactor: CGFloat = 1.0
    private var isDark: Bool = false

    func update(store: IOSDocxStore, placements: [IOSCanvasPagePlacement], scale: CGFloat, isDark: Bool) {
        self.store = store
        self.placements = placements
        self.scaleFactor = scale
        self.isDark = isDark
        setNeedsDisplay()
    }

    override func draw(_ rect: CGRect) {
        guard let store, let ctx = UIGraphicsGetCurrentContext() else { return }
        let renderScale = scaleFactor * UIScreen.main.scale

        for placement in placements where placement.rectPx.intersects(rect) {
            ctx.saveGState()
            ctx.setShadow(offset: CGSize(width: 0, height: 2), blur: 6, color: UIColor.black.withAlphaComponent(0.12).cgColor)
            UIColor.white.setFill()
            ctx.fill(placement.rectPx)
            ctx.restoreGState()

            let tileSize = 512
            let fullW = max(1, Int((placement.rectPx.width * UIScreen.main.scale).rounded()))
            let fullH = max(1, Int((placement.rectPx.height * UIScreen.main.scale).rounded()))
            var ty = 0
            while ty < fullH {
                let th = min(tileSize, fullH - ty)
                var tx = 0
                while tx < fullW {
                    let tw = min(tileSize, fullW - tx)
                    let tileRectPx = CGRect(x: tx, y: ty, width: tw, height: th)
                    if let cg = store.renderTileImage(
                        pageIndex: placement.pageIndex,
                        scale: renderScale,
                        tileRectPx: tileRectPx,
                        darkPage: isDark
                    ) {
                        let dst = CGRect(
                            x: placement.rectPx.minX + CGFloat(tx) / UIScreen.main.scale,
                            y: placement.rectPx.minY + CGFloat(ty) / UIScreen.main.scale,
                            width: CGFloat(tw) / UIScreen.main.scale,
                            height: CGFloat(th) / UIScreen.main.scale
                        )
                        ctx.draw(cg, in: dst)
                    }
                    tx += tileSize
                }
                ty += tileSize
            }

            // Selection highlights
            ctx.setFillColor(UIColor.systemBlue.withAlphaComponent(0.25).cgColor)
            for selRect in store.selection.selectionRects where Int(selRect.pageIndex) == placement.pageIndex {
                let drawR = CGRect(
                    x: placement.rectPx.minX + CGFloat(selRect.xPt) * scaleFactor,
                    y: placement.rectPx.minY + CGFloat(selRect.yPt) * scaleFactor,
                    width: CGFloat(selRect.widthPt) * scaleFactor,
                    height: CGFloat(selRect.heightPt) * scaleFactor
                )
                ctx.fill(drawR)
            }

            // Caret
            if store.selection.isCollapsed, let caret = store.selection.caret, Int(caret.pageIndex) == placement.pageIndex {
                ctx.setFillColor(UIColor(red: 0.094, green: 0.353, blue: 0.741, alpha: 1).cgColor)
                let cr = CGRect(
                    x: placement.rectPx.minX + CGFloat(caret.xPt) * scaleFactor,
                    y: placement.rectPx.minY + CGFloat(caret.topPt) * scaleFactor,
                    width: 1.8,
                    height: max(12, CGFloat(caret.heightPt) * scaleFactor)
                )
                ctx.fill(cr)
            }
        }
    }
}
