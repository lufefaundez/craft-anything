import Foundation

public struct OfficePaperPreset: Identifiable, Hashable, Sendable {
    public let id: String
    public let labelEsCl: String
    public let commandId: String
    public let paramsJson: String

    public init(id: String, labelEsCl: String, commandId: String, paramsJson: String) {
        self.id = id
        self.labelEsCl = labelEsCl
        self.commandId = commandId
        self.paramsJson = paramsJson
    }
}

public struct OfficeStyleTilePreset: Identifiable, Hashable, Sendable {
    public let id: String
    public let labelEsCl: String
    public let previewSize: Double
    public let isBold: Bool

    public init(id: String, labelEsCl: String, previewSize: Double, isBold: Bool) {
        self.id = id
        self.labelEsCl = labelEsCl
        self.previewSize = previewSize
        self.isBold = isBold
    }
}

public struct OfficeColorSwatchPreset: Identifiable, Hashable, Sendable {
    public let id: String
    public let nameEsCl: String
    public let hex: String?
    public let red: Double
    public let green: Double
    public let blue: Double

    public init(id: String, nameEsCl: String, hex: String?, red: Double, green: Double, blue: Double) {
        self.id = id
        self.nameEsCl = nameEsCl
        self.hex = hex
        self.red = red
        self.green = green
        self.blue = blue
    }
}

public enum OfficeRibbonPresets {
    public static let fonts: [String] = [
        "Aptos",
        "Calibri",
        "Carlito",
        "Times New Roman",
        "Caladea",
        "Arial",
        "Liberation Sans",
        "Courier New",
        "Georgia",
    ]

    public static let fontSizes: [Double] = [
        8, 9, 10, 10.5, 11, 12, 14, 16, 18, 20, 22, 24, 26, 28, 36, 48, 72,
    ]

    public static let styles: [OfficeStyleTilePreset] = [
        OfficeStyleTilePreset(id: "Normal", labelEsCl: "Normal", previewSize: 12, isBold: false),
        OfficeStyleTilePreset(id: "No Spacing", labelEsCl: "Sin espaciado", previewSize: 11, isBold: false),
        OfficeStyleTilePreset(id: "Heading 1", labelEsCl: "Título 1", previewSize: 15, isBold: true),
        OfficeStyleTilePreset(id: "Heading 2", labelEsCl: "Título 2", previewSize: 13, isBold: true),
        OfficeStyleTilePreset(id: "Title", labelEsCl: "Título", previewSize: 16, isBold: false),
        OfficeStyleTilePreset(id: "Subtitle", labelEsCl: "Subtítulo", previewSize: 12, isBold: false),
    ]

    public static let swatches: [OfficeColorSwatchPreset] = [
        OfficeColorSwatchPreset(id: "auto", nameEsCl: "Automático", hex: "auto", red: 0.12, green: 0.12, blue: 0.12),
        OfficeColorSwatchPreset(id: "black", nameEsCl: "Negro", hex: "000000", red: 0.0, green: 0.0, blue: 0.0),
        OfficeColorSwatchPreset(id: "wordBlue", nameEsCl: "Azul Word", hex: "185ABD", red: 0.094, green: 0.353, blue: 0.741),
        OfficeColorSwatchPreset(id: "navy", nameEsCl: "Azul marino", hex: "0F2C59", red: 0.06, green: 0.17, blue: 0.35),
        OfficeColorSwatchPreset(id: "red", nameEsCl: "Rojo", hex: "C0392B", red: 0.75, green: 0.22, blue: 0.17),
        OfficeColorSwatchPreset(id: "green", nameEsCl: "Verde", hex: "107C41", red: 0.06, green: 0.49, blue: 0.25),
        OfficeColorSwatchPreset(id: "orange", nameEsCl: "Naranjo", hex: "D35400", red: 0.83, green: 0.33, blue: 0.0),
        OfficeColorSwatchPreset(id: "gray", nameEsCl: "Gris oscuro", hex: "595959", red: 0.35, green: 0.35, blue: 0.35),
        OfficeColorSwatchPreset(id: "yellow", nameEsCl: "Amarillo", hex: "FFF000", red: 1.0, green: 0.94, blue: 0.0),
        OfficeColorSwatchPreset(id: "none", nameEsCl: "Sin relleno", hex: nil, red: 1.0, green: 1.0, blue: 1.0),
    ]

    public static let paperSizes: [OfficePaperPreset] = [
        OfficePaperPreset(id: "letter", labelEsCl: "Carta (21,59 × 27,94 cm)", commandId: "layout.size", paramsJson: #"{"name":"Letter"}"#),
        OfficePaperPreset(id: "oficio_cl", labelEsCl: "Oficio Chile (21,59 × 33,00 cm)", commandId: "layout.size", paramsJson: #"{"width":612,"height":935.43}"#),
        OfficePaperPreset(id: "legal", labelEsCl: "Legal EE.UU. (21,59 × 35,56 cm)", commandId: "layout.size", paramsJson: #"{"name":"Legal"}"#),
        OfficePaperPreset(id: "a4", labelEsCl: "A4 (21,00 × 29,70 cm)", commandId: "layout.size", paramsJson: #"{"name":"A4"}"#),
    ]

    public static let margins: [OfficePaperPreset] = [
        OfficePaperPreset(id: "normal", labelEsCl: "Normal", commandId: "layout.margins", paramsJson: #"{"preset":"normal"}"#),
        OfficePaperPreset(id: "narrow", labelEsCl: "Estrecho", commandId: "layout.margins", paramsJson: #"{"preset":"narrow"}"#),
        OfficePaperPreset(id: "moderate", labelEsCl: "Moderado", commandId: "layout.margins", paramsJson: #"{"preset":"moderate"}"#),
        OfficePaperPreset(id: "wide", labelEsCl: "Ancho", commandId: "layout.margins", paramsJson: #"{"preset":"wide"}"#),
        OfficePaperPreset(id: "mirrored", labelEsCl: "Reflejado", commandId: "layout.margins", paramsJson: #"{"preset":"mirrored"}"#),
    ]

    public static let orientations: [OfficePaperPreset] = [
        OfficePaperPreset(id: "portrait", labelEsCl: "Vertical", commandId: "layout.orientation", paramsJson: #"{"value":"portrait"}"#),
        OfficePaperPreset(id: "landscape", labelEsCl: "Horizontal", commandId: "layout.orientation", paramsJson: #"{"value":"landscape"}"#),
    ]

    public static func escapeJson(_ raw: String) -> String {
        raw.replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "\\r")
            .replacingOccurrences(of: "\t", with: "\\t")
    }

    public static func tablePayload(rows: Int, cols: Int) -> String {
        #"{"rows":\#(max(1, min(8, rows))),"cols":\#(max(1, min(8, cols)))}"#
    }

    public static func findPayload(query: String, matchCase: Bool = false, wholeWord: Bool = false) -> String {
        #"{"text":"\#(escapeJson(query))","matchCase":\#(matchCase ? "true" : "false"),"wholeWord":\#(wholeWord ? "true" : "false")}"#
    }

    public static func replacePayload(
        query: String,
        replacement: String,
        matchCase: Bool = false,
        wholeWord: Bool = false
    ) -> String {
        #"{"text":"\#(escapeJson(query))","with":"\#(escapeJson(replacement))","matchCase":\#(matchCase ? "true" : "false"),"wholeWord":\#(wholeWord ? "true" : "false")}"#
    }

    public static func colorPayload(commandId: String, hex: String?) -> String {
        switch commandId {
        case "format.color":
            return #"{"color":"\#(hex ?? "auto")"}"#
        case "para.shading", "format.shading", "design.pageColor":
            if let hex, hex != "auto" {
                return #"{"color":"\#(hex)"}"#
            }
            return #"{"color":null}"#
        default:
            return "{}"
        }
    }
}

public struct OfficeRulerTick: Hashable, Sendable {
    public let positionPt: Double
    public let label: String?
    public let isMajor: Bool
}

public struct OfficeRulerViewportMetrics: Equatable, Sendable {
    public var pageLeftPx: CGFloat
    public var pageWidthPx: CGFloat
    public var pageTopPx: CGFloat
    public var pageHeightPx: CGFloat
    public var scalePxPerPt: CGFloat

    public init(
        pageLeftPx: CGFloat = 0,
        pageWidthPx: CGFloat = 0,
        pageTopPx: CGFloat = 0,
        pageHeightPx: CGFloat = 0,
        scalePxPerPt: CGFloat = 1
    ) {
        self.pageLeftPx = pageLeftPx
        self.pageWidthPx = pageWidthPx
        self.pageTopPx = pageTopPx
        self.pageHeightPx = pageHeightPx
        self.scalePxPerPt = scalePxPerPt
    }
}

public enum OfficeRulerMath {
    public static let pointsPerCm: Double = 28.3465

    public static func buildTicks(totalPoints: Double, marginStartPt: Double = 72.0) -> [OfficeRulerTick] {
        guard totalPoints > 0 else { return [] }
        let halfCmPt = pointsPerCm / 2.0
        let maxSteps = min(120, Int(totalPoints / halfCmPt))
        var ticks: [OfficeRulerTick] = []
        ticks.reserveCapacity(maxSteps + 1)
        for step in 0...maxSteps {
            let pt = Double(step) * halfCmPt
            if pt > totalPoints { break }
            let isMajor = step % 2 == 0
            let cmRel = Int(((pt - marginStartPt) / pointsPerCm).rounded())
            let label = (isMajor && cmRel != 0) ? String(abs(cmRel)) : nil
            ticks.append(OfficeRulerTick(positionPt: pt, label: label, isMajor: isMajor))
        }
        return ticks
    }

    public static func marginsPayload(
        topPt: Double? = nil,
        bottomPt: Double? = nil,
        leftPt: Double? = nil,
        rightPt: Double? = nil
    ) -> String {
        var parts: [String] = []
        if let topPt { parts.append(String(format: "\"top\":%.1f", min(280.0, max(14.0, topPt)))) }
        if let bottomPt { parts.append(String(format: "\"bottom\":%.1f", min(280.0, max(14.0, bottomPt)))) }
        if let leftPt { parts.append(String(format: "\"left\":%.1f", min(280.0, max(14.0, leftPt)))) }
        if let rightPt { parts.append(String(format: "\"right\":%.1f", min(280.0, max(14.0, rightPt)))) }
        return "{\(parts.joined(separator: ","))}"
    }

    public static func indentsPayload(leftPt: Double, rightPt: Double, firstLinePt: Double) -> String {
        String(
            format: "{\"left\":%.1f,\"right\":%.1f,\"firstLine\":%.1f}",
            min(360.0, max(0.0, leftPt)),
            min(360.0, max(0.0, rightPt)),
            min(240.0, max(-180.0, firstLinePt))
        )
    }
}

public struct OfficeDocCommentItem: Identifiable, Equatable, Sendable {
    public let id: Int
    public let author: String
    public let date: String
    public let resolved: Bool
    public let parent: Int?
    public let text: String

    public init(id: Int, author: String, date: String, resolved: Bool, parent: Int?, text: String) {
        self.id = id
        self.author = author
        self.date = date
        self.resolved = resolved
        self.parent = parent
        self.text = text
    }
}

public enum OfficeDocCommentParser {
    public static func parseList(_ json: String?) -> [OfficeDocCommentItem] {
        guard let json = json?.trimmingCharacters(in: .whitespacesAndNewlines), !json.isEmpty,
              let data = json.data(using: .utf8),
              let parsed = try? JSONSerialization.jsonObject(with: data) else {
            return []
        }
        let array: [[String: Any]]
        if let direct = parsed as? [[String: Any]] {
            array = direct
        } else if let dict = parsed as? [String: Any], let inner = dict["comments"] as? [[String: Any]] {
            array = inner
        } else {
            return []
        }
        return array.compactMap { item in
            guard let id = item["id"] as? Int, id >= 0 else { return nil }
            let parent = (item["parent"] as? Int).flatMap { $0 >= 0 ? $0 : nil }
            return OfficeDocCommentItem(
                id: id,
                author: (item["author"] as? String) ?? "Usuario",
                date: (item["date"] as? String) ?? "",
                resolved: (item["resolved"] as? Bool) ?? false,
                parent: parent,
                text: (item["text"] as? String) ?? ""
            )
        }
    }

    public static func newCommentPayload(_ text: String) -> String {
        #"{"text":"\#(OfficeRibbonPresets.escapeJson(text))"}"#
    }

    public static func replyCommentPayload(id: Int, text: String) -> String {
        #"{"id":\#(id),"text":"\#(OfficeRibbonPresets.escapeJson(text))"}"#
    }
}

public enum AppleInkTool: String, Sendable, CaseIterable {
    case none
    case pen
    case pencil
    case highlighter
    case eraser
    case lasso

    public static func from(commandId: String) -> AppleInkTool? {
        switch commandId {
        case "draw.select": return AppleInkTool.none
        case "draw.pen", "draw.addPen": return .pen
        case "draw.pencil": return .pencil
        case "draw.highlighter": return .highlighter
        case "draw.eraser": return .eraser
        case "draw.lasso": return .lasso
        default: return nil
        }
    }
}
