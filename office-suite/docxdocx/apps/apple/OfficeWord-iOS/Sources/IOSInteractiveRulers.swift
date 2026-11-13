import OfficeCoreKit
import SwiftUI

private enum IOSHorizontalDragTarget {
    case leftMargin
    case rightMargin
    case firstLineIndent
    case leftIndent
    case rightIndent
}

private enum IOSVerticalDragTarget {
    case topMargin
    case bottomMargin
}

/// Interactive Word-style horizontal ruler for iPad/iOS with draggable margins and paragraph indent triangles.
public struct IOSHorizontalRulerView: View {
    public let rulerMetrics: RulerMetrics
    public let viewportMetrics: OfficeRulerViewportMetrics
    public let zoom: CGFloat
    public let surfaces: OfficeTokens.SurfacePalette
    public let accent: Color
    public let onCommitMargins: (_ leftPt: Double?, _ rightPt: Double?) -> Void
    public let onCommitIndents: (_ leftPt: Double, _ rightPt: Double, _ firstLinePt: Double) -> Void

    @State private var dragTarget: IOSHorizontalDragTarget?
    @State private var draftLeftMarginPt: Double = 72
    @State private var draftRightMarginPt: Double = 72
    @State private var draftParaLeftPt: Double = 0
    @State private var draftParaRightPt: Double = 0
    @State private var draftFirstLinePt: Double = 0
    @State private var dragStartValuePt: Double = 0
    @State private var hasInitialized = false

    public init(
        rulerMetrics: RulerMetrics,
        viewportMetrics: OfficeRulerViewportMetrics,
        zoom: CGFloat,
        surfaces: OfficeTokens.SurfacePalette,
        accent: Color,
        onCommitMargins: @escaping (_ leftPt: Double?, _ rightPt: Double?) -> Void,
        onCommitIndents: @escaping (_ leftPt: Double, _ rightPt: Double, _ firstLinePt: Double) -> Void
    ) {
        self.rulerMetrics = rulerMetrics
        self.viewportMetrics = viewportMetrics
        self.zoom = zoom
        self.surfaces = surfaces
        self.accent = accent
        self.onCommitMargins = onCommitMargins
        self.onCommitIndents = onCommitIndents
    }

    private var pageWidthPt: Double { max(200.0, Double(rulerMetrics.widthPt)) }

    private func layoutValues(sizeWidth: CGFloat) -> (startX: CGFloat, pxPerPt: CGFloat) {
        let pxPerPt: CGFloat
        if viewportMetrics.pageWidthPx > 10, viewportMetrics.scalePxPerPt > 0 {
            pxPerPt = viewportMetrics.scalePxPerPt
        } else {
            pxPerPt = min(2.5 * max(0.5, zoom), max(0.3, (sizeWidth - 28.0) / CGFloat(pageWidthPt)))
        }
        let startX: CGFloat
        if viewportMetrics.pageWidthPx > 10 {
            startX = viewportMetrics.pageLeftPx
        } else {
            let rulerW = CGFloat(pageWidthPt) * pxPerPt
            startX = max(14.0, (sizeWidth - rulerW) / 2.0)
        }
        return (startX, pxPerPt)
    }

    private func syncFromMetrics() {
        guard dragTarget == nil else { return }
        draftLeftMarginPt = Double(rulerMetrics.marginLeftPt)
        draftRightMarginPt = Double(rulerMetrics.marginRightPt)
        draftParaLeftPt = Double(rulerMetrics.paraLeftPt)
        draftParaRightPt = Double(rulerMetrics.paraRightPt)
        draftFirstLinePt = Double(rulerMetrics.paraFirstLinePt)
        hasInitialized = true
    }

    public var body: some View {
        let activeLeftMargin = hasInitialized ? draftLeftMarginPt : Double(rulerMetrics.marginLeftPt)
        let activeRightMargin = hasInitialized ? draftRightMarginPt : Double(rulerMetrics.marginRightPt)
        let activeParaLeft = hasInitialized ? draftParaLeftPt : Double(rulerMetrics.paraLeftPt)
        let activeParaRight = hasInitialized ? draftParaRightPt : Double(rulerMetrics.paraRightPt)
        let activeFirstLine = hasInitialized ? draftFirstLinePt : Double(rulerMetrics.paraFirstLinePt)
        let ticks = OfficeRulerMath.buildTicks(totalPoints: pageWidthPt, marginStartPt: activeLeftMargin)

        GeometryReader { geo in
            let (startX, pxPerPt) = layoutValues(sizeWidth: geo.size.width)
            let rulerWidthPx = CGFloat(pageWidthPt) * pxPerPt
            let leftMarginPx = CGFloat(activeLeftMargin) * pxPerPt
            let rightMarginPx = CGFloat(activeRightMargin) * pxPerPt
            let leftBoundaryX = startX + leftMarginPx
            let rightBoundaryX = startX + rulerWidthPx - rightMarginPx
            let firstLineX = startX + CGFloat(activeLeftMargin + activeParaLeft + activeFirstLine) * pxPerPt
            let leftIndentX = startX + CGFloat(activeLeftMargin + activeParaLeft) * pxPerPt
            let rightIndentX = startX + CGFloat(pageWidthPt - activeRightMargin - activeParaRight) * pxPerPt

            Canvas { ctx, size in
                let h = size.height
                // Margin shaded regions + printable center strip
                let pageRect = CGRect(x: startX, y: 2, width: rulerWidthPx, height: h - 4)
                ctx.fill(Path(pageRect), with: .color(surfaces.ribbonCard))

                let leftShade = CGRect(x: startX, y: 2, width: max(0, leftMarginPx), height: h - 4)
                ctx.fill(Path(leftShade), with: .color(surfaces.textDim.opacity(0.14)))

                let rightShade = CGRect(x: rightBoundaryX, y: 2, width: max(0, rightMarginPx), height: h - 4)
                ctx.fill(Path(rightShade), with: .color(surfaces.textDim.opacity(0.14)))

                var borderPath = Path()
                borderPath.addRect(pageRect)
                ctx.stroke(borderPath, with: .color(surfaces.ribbonBorder), lineWidth: 0.75)

                // Ruler ticks and centimeter numbers
                for tick in ticks {
                    let x = startX + CGFloat(tick.positionPt) * pxPerPt
                    let topY = tick.isMajor ? h * 0.42 : h * 0.65
                    var tp = Path()
                    tp.move(to: CGPoint(x: x, y: topY))
                    tp.addLine(to: CGPoint(x: x, y: h - 2))
                    ctx.stroke(tp, with: .color(surfaces.textDim.opacity(0.65)), lineWidth: tick.isMajor ? 1.0 : 0.6)
                    if let lbl = tick.label {
                        ctx.draw(
                            Text(lbl).font(.system(size: 8.5, weight: .medium)).foregroundStyle(surfaces.textDim),
                            at: CGPoint(x: x, y: h * 0.25)
                        )
                    }
                }

                // Active margin drag guides
                if dragTarget == .leftMargin {
                    var gp = Path()
                    gp.move(to: CGPoint(x: leftBoundaryX, y: 0))
                    gp.addLine(to: CGPoint(x: leftBoundaryX, y: h))
                    ctx.stroke(gp, with: .color(accent), lineWidth: 1.5)
                } else if dragTarget == .rightMargin {
                    var gp = Path()
                    gp.move(to: CGPoint(x: rightBoundaryX, y: 0))
                    gp.addLine(to: CGPoint(x: rightBoundaryX, y: h))
                    ctx.stroke(gp, with: .color(accent), lineWidth: 1.5)
                }

                // First-line indent (top triangle pointing down)
                var flp = Path()
                flp.move(to: CGPoint(x: firstLineX - 5.5, y: 2))
                flp.addLine(to: CGPoint(x: firstLineX + 5.5, y: 2))
                flp.addLine(to: CGPoint(x: firstLineX, y: 9.5))
                flp.closeSubpath()
                ctx.fill(flp, with: .color(dragTarget == .firstLineIndent ? accent : surfaces.text))

                // Left indent (bottom triangle pointing up)
                var lip = Path()
                lip.move(to: CGPoint(x: leftIndentX, y: h - 10.5))
                lip.addLine(to: CGPoint(x: leftIndentX - 5.5, y: h - 2))
                lip.addLine(to: CGPoint(x: leftIndentX + 5.5, y: h - 2))
                lip.closeSubpath()
                ctx.fill(lip, with: .color(dragTarget == .leftIndent ? accent : surfaces.text))

                // Right indent (bottom triangle pointing up)
                var rip = Path()
                rip.move(to: CGPoint(x: rightIndentX, y: h - 10.5))
                rip.addLine(to: CGPoint(x: rightIndentX - 5.5, y: h - 2))
                rip.addLine(to: CGPoint(x: rightIndentX + 5.5, y: h - 2))
                rip.closeSubpath()
                ctx.fill(rip, with: .color(dragTarget == .rightIndent ? accent : accent.opacity(0.85)))
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if dragTarget == nil {
                            syncFromMetrics()
                            let hitSlop: CGFloat = 14.0
                            let sx = value.startLocation.x
                            let sy = value.startLocation.y
                            let midY = geo.size.height * 0.48
                            if sy < midY && abs(sx - firstLineX) <= hitSlop {
                                dragTarget = .firstLineIndent
                                dragStartValuePt = draftFirstLinePt
                            } else if sy >= midY && abs(sx - leftIndentX) <= hitSlop {
                                dragTarget = .leftIndent
                                dragStartValuePt = draftParaLeftPt
                            } else if abs(sx - rightIndentX) <= hitSlop {
                                dragTarget = .rightIndent
                                dragStartValuePt = draftParaRightPt
                            } else if abs(sx - leftBoundaryX) <= hitSlop {
                                dragTarget = .leftMargin
                                dragStartValuePt = draftLeftMarginPt
                            } else if abs(sx - rightBoundaryX) <= hitSlop {
                                dragTarget = .rightMargin
                                dragStartValuePt = draftRightMarginPt
                            } else {
                                return
                            }
                        }

                        guard let target = dragTarget else { return }
                        let deltaPt = Double(value.translation.width / max(0.1, pxPerPt))
                        switch target {
                        case .leftMargin:
                            draftLeftMarginPt = min(pageWidthPt * 0.4, max(14.0, dragStartValuePt + deltaPt))
                        case .rightMargin:
                            draftRightMarginPt = min(pageWidthPt * 0.4, max(14.0, dragStartValuePt - deltaPt))
                        case .firstLineIndent:
                            draftFirstLinePt = min(pageWidthPt * 0.45, max(-draftParaLeftPt, dragStartValuePt + deltaPt))
                        case .leftIndent:
                            draftParaLeftPt = min(pageWidthPt * 0.45, max(0.0, dragStartValuePt + deltaPt))
                        case .rightIndent:
                            draftParaRightPt = min(pageWidthPt * 0.45, max(0.0, dragStartValuePt - deltaPt))
                        }
                    }
                    .onEnded { _ in
                        defer { dragTarget = nil }
                        switch dragTarget {
                        case .leftMargin:
                            onCommitMargins(draftLeftMarginPt, nil)
                        case .rightMargin:
                            onCommitMargins(nil, draftRightMarginPt)
                        case .firstLineIndent, .leftIndent, .rightIndent:
                            onCommitIndents(draftParaLeftPt, draftParaRightPt, draftFirstLinePt)
                        case nil:
                            break
                        }
                    }
            )
            .overlay(alignment: .topTrailing) {
                if let label = activeDragBadgeText {
                    Text(label)
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(accent)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                        .padding(.trailing, 8)
                }
            }
        }
        .frame(height: 22)
        .background(surfaces.panel)
        .onAppear { syncFromMetrics() }
        .onChange(of: rulerMetrics.marginLeftPt) { _, _ in syncFromMetrics() }
        .onChange(of: rulerMetrics.marginRightPt) { _, _ in syncFromMetrics() }
        .onChange(of: rulerMetrics.paraLeftPt) { _, _ in syncFromMetrics() }
        .onChange(of: rulerMetrics.paraRightPt) { _, _ in syncFromMetrics() }
        .onChange(of: rulerMetrics.paraFirstLinePt) { _, _ in syncFromMetrics() }
    }

    private var activeDragBadgeText: String? {
        guard let dragTarget else { return nil }
        let pt: Double
        let prefix: String
        switch dragTarget {
        case .leftMargin: prefix = "Margen izq."; pt = draftLeftMarginPt
        case .rightMargin: prefix = "Margen der."; pt = draftRightMarginPt
        case .firstLineIndent: prefix = "1ª línea"; pt = draftFirstLinePt
        case .leftIndent: prefix = "Sangría izq."; pt = draftParaLeftPt
        case .rightIndent: prefix = "Sangría der."; pt = draftParaRightPt
        }
        let cm = pt / OfficeRulerMath.pointsPerCm
        return String(format: "%@: %.1f cm", prefix, cm)
    }
}

/// Interactive Word-style vertical ruler for iPad with draggable top and bottom page margins.
public struct IOSVerticalRulerView: View {
    public let rulerMetrics: RulerMetrics
    public let viewportMetrics: OfficeRulerViewportMetrics
    public let surfaces: OfficeTokens.SurfacePalette
    public let accent: Color
    public let onCommitMargins: (_ topPt: Double?, _ bottomPt: Double?) -> Void

    @State private var dragTarget: IOSVerticalDragTarget?
    @State private var draftTopMarginPt: Double = 72
    @State private var draftBottomMarginPt: Double = 72
    @State private var dragStartValuePt: Double = 0
    @State private var hasInitialized = false

    public init(
        rulerMetrics: RulerMetrics,
        viewportMetrics: OfficeRulerViewportMetrics,
        surfaces: OfficeTokens.SurfacePalette,
        accent: Color,
        onCommitMargins: @escaping (_ topPt: Double?, _ bottomPt: Double?) -> Void
    ) {
        self.rulerMetrics = rulerMetrics
        self.viewportMetrics = viewportMetrics
        self.surfaces = surfaces
        self.accent = accent
        self.onCommitMargins = onCommitMargins
    }

    private var pageHeightPt: Double { max(300.0, Double(rulerMetrics.heightPt)) }

    private func layoutValues(sizeHeight: CGFloat) -> (startY: CGFloat, pxPerPt: CGFloat) {
        let pxPerPt: CGFloat
        if viewportMetrics.pageHeightPx > 10, viewportMetrics.scalePxPerPt > 0 {
            pxPerPt = viewportMetrics.scalePxPerPt
        } else {
            pxPerPt = max(0.3, sizeHeight / CGFloat(pageHeightPt))
        }
        let startY: CGFloat = viewportMetrics.pageHeightPx > 10 ? viewportMetrics.pageTopPx : 18.0
        return (startY, pxPerPt)
    }

    private func syncFromMetrics() {
        guard dragTarget == nil else { return }
        draftTopMarginPt = Double(rulerMetrics.marginTopPt)
        draftBottomMarginPt = Double(rulerMetrics.marginBottomPt)
        hasInitialized = true
    }

    public var body: some View {
        let activeTopMargin = hasInitialized ? draftTopMarginPt : Double(rulerMetrics.marginTopPt)
        let activeBottomMargin = hasInitialized ? draftBottomMarginPt : Double(rulerMetrics.marginBottomPt)
        let ticks = OfficeRulerMath.buildTicks(totalPoints: pageHeightPt, marginStartPt: activeTopMargin)

        GeometryReader { geo in
            let (startY, pxPerPt) = layoutValues(sizeHeight: geo.size.height)
            let rulerHeightPx = CGFloat(pageHeightPt) * pxPerPt
            let topMarginPx = CGFloat(activeTopMargin) * pxPerPt
            let bottomMarginPx = CGFloat(activeBottomMargin) * pxPerPt
            let topBoundaryY = startY + topMarginPx
            let bottomBoundaryY = startY + rulerHeightPx - bottomMarginPx

            Canvas { ctx, size in
                let w = size.width
                let pageRect = CGRect(x: 2, y: startY, width: w - 4, height: rulerHeightPx)
                ctx.fill(Path(pageRect), with: .color(surfaces.ribbonCard))

                let topShade = CGRect(x: 2, y: startY, width: w - 4, height: max(0, topMarginPx))
                ctx.fill(Path(topShade), with: .color(surfaces.textDim.opacity(0.14)))

                let bottomShade = CGRect(x: 2, y: bottomBoundaryY, width: w - 4, height: max(0, bottomMarginPx))
                ctx.fill(Path(bottomShade), with: .color(surfaces.textDim.opacity(0.14)))

                var borderPath = Path()
                borderPath.addRect(pageRect)
                ctx.stroke(borderPath, with: .color(surfaces.ribbonBorder), lineWidth: 0.75)

                for tick in ticks {
                    let y = startY + CGFloat(tick.positionPt) * pxPerPt
                    guard y >= -8 && y <= size.height + 8 else { continue }
                    let leftX = tick.isMajor ? w * 0.42 : w * 0.65
                    var tp = Path()
                    tp.move(to: CGPoint(x: leftX, y: y))
                    tp.addLine(to: CGPoint(x: w - 2, y: y))
                    ctx.stroke(tp, with: .color(surfaces.textDim.opacity(0.65)), lineWidth: tick.isMajor ? 1.0 : 0.6)
                    if let lbl = tick.label {
                        ctx.draw(
                            Text(lbl).font(.system(size: 8, weight: .medium)).foregroundStyle(surfaces.textDim),
                            at: CGPoint(x: w * 0.25, y: y)
                        )
                    }
                }

                // Visual margin boundary handles
                var topLine = Path()
                topLine.move(to: CGPoint(x: 2, y: topBoundaryY))
                topLine.addLine(to: CGPoint(x: w - 2, y: topBoundaryY))
                ctx.stroke(topLine, with: .color(dragTarget == .topMargin ? accent : accent.opacity(0.55)), lineWidth: dragTarget == .topMargin ? 2.0 : 1.0)

                var botLine = Path()
                botLine.move(to: CGPoint(x: 2, y: bottomBoundaryY))
                botLine.addLine(to: CGPoint(x: w - 2, y: bottomBoundaryY))
                ctx.stroke(botLine, with: .color(dragTarget == .bottomMargin ? accent : accent.opacity(0.55)), lineWidth: dragTarget == .bottomMargin ? 2.0 : 1.0)
            }
            .clipped()
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if dragTarget == nil {
                            syncFromMetrics()
                            let hitSlop: CGFloat = 16.0
                            let sy = value.startLocation.y
                            if abs(sy - topBoundaryY) <= hitSlop {
                                dragTarget = .topMargin
                                dragStartValuePt = draftTopMarginPt
                            } else if abs(sy - bottomBoundaryY) <= hitSlop {
                                dragTarget = .bottomMargin
                                dragStartValuePt = draftBottomMarginPt
                            } else {
                                return
                            }
                        }

                        guard let target = dragTarget else { return }
                        let deltaPt = Double(value.translation.height / max(0.1, pxPerPt))
                        switch target {
                        case .topMargin:
                            draftTopMarginPt = min(pageHeightPt * 0.4, max(14.0, dragStartValuePt + deltaPt))
                        case .bottomMargin:
                            draftBottomMarginPt = min(pageHeightPt * 0.4, max(14.0, dragStartValuePt - deltaPt))
                        }
                    }
                    .onEnded { _ in
                        defer { dragTarget = nil }
                        switch dragTarget {
                        case .topMargin:
                            onCommitMargins(draftTopMarginPt, nil)
                        case .bottomMargin:
                            onCommitMargins(nil, draftBottomMarginPt)
                        case nil:
                            break
                        }
                    }
            )
        }
        .frame(width: 22)
        .background(surfaces.panel)
        .onAppear { syncFromMetrics() }
        .onChange(of: rulerMetrics.marginTopPt) { _, _ in syncFromMetrics() }
        .onChange(of: rulerMetrics.marginBottomPt) { _, _ in syncFromMetrics() }
    }
}
