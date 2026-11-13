import Foundation
import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

public struct RibbonControlSpec: Identifiable, Hashable, Sendable {
    public var id: String { "\(commandId)::\(labelEsCl)" }
    public let commandId: String
    public let kind: String
    public let row: String
    public let icon: String
    public let labelEsCl: String
    public let labelEnUs: String
    public let submenu: [RibbonControlSpec]

    public var isTall: Bool {
        row == "full" || kind == "large_button" || kind == "style_gallery" || kind == "table_grid"
    }
}

public struct RibbonGroupSpec: Identifiable, Hashable, Sendable {
    public let id: String
    public let labelEsCl: String
    public let labelEnUs: String
    public let controls: [RibbonControlSpec]
    public let dialogCommandId: String?
}

public struct RibbonTabSpec: Identifiable, Hashable, Sendable {
    public let id: String
    public let labelEsCl: String
    public let labelEnUs: String
    public let groups: [RibbonGroupSpec]
    public let contextualTrigger: String?
}

public struct RibbonSchemaCatalog: Sendable {
    public let coreTabs: [RibbonTabSpec]
    public let contextualTabs: [RibbonTabSpec]
    public let qat: [RibbonControlSpec]
    public let smartphoneQuickBar: [RibbonControlSpec]

    public init(
        coreTabs: [RibbonTabSpec],
        contextualTabs: [RibbonTabSpec],
        qat: [RibbonControlSpec],
        smartphoneQuickBar: [RibbonControlSpec]
    ) {
        self.coreTabs = coreTabs
        self.contextualTabs = contextualTabs
        self.qat = qat
        self.smartphoneQuickBar = smartphoneQuickBar
    }

    public static func loadBundled() throws -> RibbonSchemaCatalog {
        guard let url = Bundle.module.url(forResource: "ribbon-schema", withExtension: "json") else {
            throw NSError(domain: "OfficeCoreKit", code: 404, userInfo: [
                NSLocalizedDescriptionKey: "Missing ribbon-schema.json in OfficeCoreKit bundle",
            ])
        }
        let data = try Data(contentsOf: url)
        return try decode(from: data)
    }

    public static func decode(from data: Data) throws -> RibbonSchemaCatalog {
        let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
        let tabsRaw = obj["tabs"] as? [[String: Any]] ?? []
        let contextRaw = obj["contextual_tabs"] as? [[String: Any]] ?? []
        let titleBar = obj["title_bar"] as? [String: Any] ?? [:]
        let qatRaw = titleBar["qat"] as? [[String: Any]] ?? []
        let quickRaw = (obj["smartphone_quick_bar"] as? [String: Any])?["pinned_actions"] as? [[String: Any]] ?? []

        return RibbonSchemaCatalog(
            coreTabs: tabsRaw.compactMap { parseTab($0, contextual: false) },
            contextualTabs: contextRaw.compactMap { parseTab($0, contextual: true) },
            qat: qatRaw.compactMap(parseControl),
            smartphoneQuickBar: quickRaw.compactMap(parseControl)
        )
    }

    private static func parseTab(_ dict: [String: Any], contextual: Bool) -> RibbonTabSpec? {
        guard let id = dict["id"] as? String else { return nil }
        let labels = dict["labels"] as? [String: String] ?? [:]
        let groupsRaw = dict["groups"] as? [[String: Any]] ?? []
        return RibbonTabSpec(
            id: id,
            labelEsCl: labels["es-CL"] ?? id,
            labelEnUs: labels["en-US"] ?? id,
            groups: groupsRaw.compactMap(parseGroup),
            contextualTrigger: contextual ? (dict["context_trigger"] as? String) : nil
        )
    }

    private static func parseGroup(_ dict: [String: Any]) -> RibbonGroupSpec? {
        guard let id = dict["id"] as? String else { return nil }
        let labels = dict["labels"] as? [String: String] ?? [:]
        let ctrlsRaw = dict["controls"] as? [[String: Any]] ?? []
        return RibbonGroupSpec(
            id: id,
            labelEsCl: labels["es-CL"] ?? id,
            labelEnUs: labels["en-US"] ?? id,
            controls: ctrlsRaw.compactMap(parseControl),
            dialogCommandId: dict["dialog_command_id"] as? String
        )
    }

    private static func parseControl(_ dict: [String: Any]) -> RibbonControlSpec? {
        guard let commandId = dict["command_id"] as? String else { return nil }
        let labels = dict["labels"] as? [String: String] ?? [:]
        let subRaw = dict["submenu"] as? [[String: Any]] ?? []
        let rowVal: String
        if let s = dict["row"] as? String {
            rowVal = s
        } else if let n = dict["row"] as? Int {
            rowVal = String(n)
        } else {
            rowVal = "0"
        }
        return RibbonControlSpec(
            commandId: commandId,
            kind: dict["kind"] as? String ?? "small_button",
            row: rowVal,
            icon: dict["icon"] as? String ?? "",
            labelEsCl: labels["es-CL"] ?? commandId,
            labelEnUs: labels["en-US"] ?? commandId,
            submenu: subRaw.compactMap(parseControl)
        )
    }
}

public enum RibbonGroupSlot: Identifiable, Sendable {
    case tall(RibbonControlSpec)
    case stackedPair(top: RibbonControlSpec, bottom: RibbonControlSpec?)

    public var id: String {
        switch self {
        case .tall(let c):
            return "tall:\(c.id)"
        case .stackedPair(let top, let bottom):
            return "pair:\(top.id)+\(bottom?.id ?? "none")"
        }
    }
}

public struct RibbonSearchMatch: Identifiable, Hashable, Sendable {
    public var id: String { "\(tabId):\(control.id)" }
    public let tabId: String
    public let tabLabelEsCl: String
    public let groupLabelEsCl: String
    public let control: RibbonControlSpec
}

public enum AdaptiveRibbonHelper {
    public static func layoutColumns(for group: RibbonGroupSpec) -> [RibbonGroupSlot] {
        var slots: [RibbonGroupSlot] = []
        var pendingTop: RibbonControlSpec?
        for ctrl in group.controls {
            if ctrl.isTall {
                if let top = pendingTop {
                    slots.append(.stackedPair(top: top, bottom: nil))
                    pendingTop = nil
                }
                slots.append(.tall(ctrl))
            } else if let top = pendingTop {
                slots.append(.stackedPair(top: top, bottom: ctrl))
                pendingTop = nil
            } else {
                pendingTop = ctrl
            }
        }
        if let top = pendingTop {
            slots.append(.stackedPair(top: top, bottom: nil))
        }
        return slots
    }

    public static func visibleTabs(catalog: RibbonSchemaCatalog, activeContexts: Set<String>) -> [RibbonTabSpec] {
        let ctxTabs = catalog.contextualTabs.filter { tab in
            guard let trigger = tab.contextualTrigger else { return false }
            return activeContexts.contains(trigger)
        }
        return catalog.coreTabs + ctxTabs
    }

    public static func searchCommands(catalog: RibbonSchemaCatalog, query: String, limit: Int = 36) -> [RibbonSearchMatch] {
        let needle = query.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "es_CL"))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !needle.isEmpty else { return [] }

        var results: [RibbonSearchMatch] = []
        for tab in catalog.coreTabs + catalog.contextualTabs {
            for group in tab.groups {
                for ctrl in group.controls {
                    collect(tab: tab, group: group, control: ctrl, needle: needle, into: &results, limit: limit)
                    if results.count >= limit { return results }
                }
            }
        }
        return results
    }

    private static func collect(
        tab: RibbonTabSpec,
        group: RibbonGroupSpec,
        control: RibbonControlSpec,
        needle: String,
        into results: inout [RibbonSearchMatch],
        limit: Int
    ) {
        guard results.count < limit else { return }
        let fields = [control.labelEsCl, control.labelEnUs, control.commandId, group.labelEsCl]
        if fields.contains(where: {
            $0.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "es_CL")).contains(needle)
        }) {
            results.append(RibbonSearchMatch(
                tabId: tab.id,
                tabLabelEsCl: tab.labelEsCl,
                groupLabelEsCl: group.labelEsCl,
                control: control
            ))
        }
        for sub in control.submenu {
            collect(tab: tab, group: group, control: sub, needle: needle, into: &results, limit: limit)
            if results.count >= limit { return }
        }
    }

    public static func parseActiveToggleIds(formatStateJson: String) -> Set<String> {
        guard let data = formatStateJson.data(using: .utf8),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return []
        }
        var toggles = Set<String>()
        if (dict["bold"] as? Bool) == true { toggles.insert("format.bold") }
        if (dict["italic"] as? Bool) == true { toggles.insert("format.italic") }
        if (dict["underline"] as? Bool) == true { toggles.insert("format.underline") }
        if (dict["strike"] as? Bool) == true || (dict["strikethrough"] as? Bool) == true {
            toggles.insert("format.strikethrough")
            toggles.insert("format.strike")
        }
        if (dict["superscript"] as? Bool) == true { toggles.insert("format.superscript") }
        if (dict["subscript"] as? Bool) == true { toggles.insert("format.subscript") }
        switch dict["align"] as? String {
        case "left": toggles.insert("para.alignLeft")
        case "center": toggles.insert("para.alignCenter")
        case "right": toggles.insert("para.alignRight")
        case "justify": toggles.insert("para.justify")
        default: break
        }
        if (dict["bullets"] as? Bool) == true { toggles.insert("para.bullets") }
        if (dict["numbering"] as? Bool) == true { toggles.insert("para.numbering") }
        if (dict["marks"] as? Bool) == true { toggles.insert("view.marks") }
        return toggles
    }

    public static func parseFormatSummary(formatStateJson: String) -> (font: String, size: Double, style: String, colorHex: String) {
        guard let data = formatStateJson.data(using: .utf8),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return ("Aptos", 12.0, "Normal", "auto")
        }
        let font = (dict["font"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let sizeNum = (dict["size"] as? NSNumber)?.doubleValue
        let style = (dict["styleName"] as? String) ?? (dict["style"] as? String)
        let colorHex = dict["color"] as? String
        return (
            font: (font?.isEmpty == false ? font! : "Aptos"),
            size: (sizeNum ?? 12.0),
            style: (style?.isEmpty == false ? style! : "Normal"),
            colorHex: colorHex ?? "auto"
        )
    }

    public static func parseActiveContexts(formatStateJson: String) -> Set<String> {
        guard let data = formatStateJson.data(using: .utf8),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return []
        }
        var ctx = Set<String>()
        if (dict["inTable"] as? Bool) == true || (dict["in_table"] as? Bool) == true { ctx.insert("in_table") }
        if (dict["inHeaderFooter"] as? Bool) == true || (dict["in_header_footer"] as? Bool) == true { ctx.insert("in_header_footer") }
        if (dict["inEquation"] as? Bool) == true || (dict["in_equation"] as? Bool) == true { ctx.insert("in_equation") }
        if (dict["imageSelected"] as? Bool) == true || (dict["image_selected"] as? Bool) == true { ctx.insert("image_selected") }
        if (dict["shapeSelected"] as? Bool) == true || (dict["shape_selected"] as? Bool) == true { ctx.insert("shape_selected") }
        return ctx
    }

    public static func officeIconImage(named iconName: String) -> Image {
        #if canImport(UIKit)
        if UIImage(named: iconName, in: .module, compatibleWith: nil) != nil {
            return Image(iconName, bundle: .module)
        }
        let lower = iconName.lowercased()
        if UIImage(named: lower, in: .module, compatibleWith: nil) != nil {
            return Image(lower, bundle: .module)
        }
        #elseif canImport(AppKit)
        if Bundle.module.image(forResource: iconName) != nil {
            return Image(iconName, bundle: .module)
        }
        let lower = iconName.lowercased()
        if Bundle.module.image(forResource: lower) != nil {
            return Image(lower, bundle: .module)
        }
        #endif
        return Image(iconName, bundle: .module)
    }
}
