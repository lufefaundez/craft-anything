import Foundation

public enum DocTemplateId: String, CaseIterable, Identifiable, Sendable {
    case blank
    case formalLetter
    case oficioChile
    case executiveReport

    public var id: String { rawValue }

    public var titleEsCl: String {
        switch self {
        case .blank: return "Documento en blanco"
        case .formalLetter: return "Carta Formal"
        case .oficioChile: return "Oficio Ordinario Chile"
        case .executiveReport: return "Informe Ejecutivo"
        }
    }

    public var subtitleEsCl: String {
        switch self {
        case .blank: return "Tamaño Carta • Márgenes normales (es-CL)"
        case .formalLetter: return "Encabezado formal, fecha, destinatario y firma"
        case .oficioChile: return "Estructura N° / ANT. / MAT. / DE / A"
        case .executiveReport: return "Portada resumida, análisis y conclusiones"
        }
    }

    public var seedParagraphs: [String] {
        switch self {
        case .blank:
            return []
        case .formalLetter:
            return [
                "Santiago, 12 de noviembre de 2026",
                "",
                "Señor(a):",
                "Presente",
                "",
                "De mi consideración:",
                "",
                "Por medio de la presente me dirijo a usted con el objeto de exponer lo siguiente...",
                "",
                "Saluda atentamente,",
            ]
        case .oficioChile:
            return [
                "OFICIO ORDINARIO N° ____ / 2026",
                "ANT.: Sin antecedentes.",
                "MAT.: Lo que indica.",
                "SANTIAGO, ",
                "",
                "DE : REMITENTE",
                "A  : DESTINATARIO",
                "",
                "1. Informo a usted que...",
                "",
                "DISTRIBUCIÓN:",
                "1.- Destinatario.",
                "2.- Archivo.",
            ]
        case .executiveReport:
            return [
                "INFORME EJECUTIVO",
                "1. Resumen Ejecutivo",
                "Describa aquí los hallazgos principales del documento.",
                "2. Antecedentes y Análisis",
                "Detalle técnico y presupuestario.",
                "3. Conclusiones y Próximos Pasos",
            ]
        }
    }

    public func makeSession(locale: String = "es-CL") throws -> MobileDocumentSession {
        let session = try DocxCoreBridge.makeBlankSession(locale: locale)
        for (idx, line) in seedParagraphs.enumerated() {
            if !line.isEmpty {
                _ = try session.imeCommitText(text: line, joinWithPrevious: false)
            }
            if idx < seedParagraphs.count - 1 {
                _ = try session.executeCommand(cmdId: "text.newParagraph", paramsJson: "{}")
            }
        }
        return session
    }
}

public struct WebDavConfig: Sendable {
    public let baseUrl: String
    public let username: String
    public let password: String

    public init(baseUrl: String, username: String, password: String) {
        self.baseUrl = baseUrl
        self.username = username
        self.password = password
    }

    public func resolveChildUrl(_ relativePath: String) throws -> URL {
        let trimmed = baseUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("https://") || trimmed.hasPrefix("http://") else {
            throw NSError(domain: "OfficeCoreKit.WebDAV", code: 400, userInfo: [
                NSLocalizedDescriptionKey: "La URL WebDAV debe comenzar con https:// o http://",
            ])
        }
        let clean = relativePath.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !clean.contains("..") else {
            throw NSError(domain: "OfficeCoreKit.WebDAV", code: 400, userInfo: [
                NSLocalizedDescriptionKey: "Ruta inválida: no se permiten segmentos '..'",
            ])
        }
        let baseNormalized = trimmed.hasSuffix("/") ? trimmed : "\(trimmed)/"
        guard let base = URL(string: baseNormalized),
              let resolved = URL(string: clean.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? clean, relativeTo: base) else {
            throw NSError(domain: "OfficeCoreKit.WebDAV", code: 400, userInfo: [
                NSLocalizedDescriptionKey: "URL WebDAV inválida",
            ])
        }
        return resolved.absoluteURL
    }

    public func basicAuthHeader() -> String {
        let creds = Data("\(username):\(password)".utf8).base64EncodedString()
        return "Basic \(creds)"
    }
}

public struct WebDavEntry: Identifiable, Hashable, Sendable {
    public var id: String { href }
    public let href: String
    public let displayName: String
    public let isDirectory: Bool
    public let contentLengthBytes: Int64?
}

public final class AppleWebDavClient: Sendable {
    public static let docxMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    public init() {}

    public func listDirectory(config: WebDavConfig, relativeDir: String = "") async throws -> [WebDavEntry] {
        let url = try config.resolveChildUrl(relativeDir)
        var req = URLRequest(url: url)
        req.httpMethod = "PROPFIND"
        req.setValue(config.basicAuthHeader(), forHTTPHeaderField: "Authorization")
        req.setValue("1", forHTTPHeaderField: "Depth")
        req.setValue("application/xml; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.httpBody = Data("""
        <?xml version="1.0" encoding="utf-8" ?>
        <D:propfind xmlns:D="DAV:">
          <D:prop><D:displayname/><D:resourcetype/><D:getcontentlength/></D:prop>
        </D:propfind>
        """.utf8)

        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode) || http.statusCode == 207 else {
            throw NSError(domain: "OfficeCoreKit.WebDAV", code: (resp as? HTTPURLResponse)?.statusCode ?? 500, userInfo: [
                NSLocalizedDescriptionKey: "Error WebDAV PROPFIND (\((resp as? HTTPURLResponse)?.statusCode ?? 0))",
            ])
        }
        return Self.parsePropfindXml(String(decoding: data, as: UTF8.self), requestPath: url.path)
    }

    public func downloadBytes(config: WebDavConfig, hrefOrRelativePath: String) async throws -> Data {
        let url: URL
        if let abs = URL(string: hrefOrRelativePath), abs.scheme == "https" || abs.scheme == "http" {
            url = abs
        } else {
            url = try config.resolveChildUrl(hrefOrRelativePath)
        }
        var req = URLRequest(url: url)
        req.httpMethod = "GET"
        req.setValue(config.basicAuthHeader(), forHTTPHeaderField: "Authorization")
        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw NSError(domain: "OfficeCoreKit.WebDAV", code: (resp as? HTTPURLResponse)?.statusCode ?? 500, userInfo: [
                NSLocalizedDescriptionKey: "Error WebDAV GET (\((resp as? HTTPURLResponse)?.statusCode ?? 0))",
            ])
        }
        return data
    }

    public func uploadBytes(config: WebDavConfig, relativePath: String, data: Data) async throws {
        let url = try config.resolveChildUrl(relativePath)
        var req = URLRequest(url: url)
        req.httpMethod = "PUT"
        req.setValue(config.basicAuthHeader(), forHTTPHeaderField: "Authorization")
        req.setValue(Self.docxMime, forHTTPHeaderField: "Content-Type")
        req.httpBody = data
        let (_, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw NSError(domain: "OfficeCoreKit.WebDAV", code: (resp as? HTTPURLResponse)?.statusCode ?? 500, userInfo: [
                NSLocalizedDescriptionKey: "Error WebDAV PUT (\((resp as? HTTPURLResponse)?.statusCode ?? 0))",
            ])
        }
    }

    public static func parsePropfindXml(_ xml: String, requestPath: String = "") -> [WebDavEntry] {
        let responsePattern = #"<(?:[a-zA-Z0-9_-]+:)?response\b[^>]*>([\s\S]*?)</(?:[a-zA-Z0-9_-]+:)?response>"#
        let hrefPattern = #"<(?:[a-zA-Z0-9_-]+:)?href\b[^>]*>([\s\S]*?)</(?:[a-zA-Z0-9_-]+:)?href>"#
        let namePattern = #"<(?:[a-zA-Z0-9_-]+:)?displayname\b[^>]*>([\s\S]*?)</(?:[a-zA-Z0-9_-]+:)?displayname>"#
        let lenPattern = #"<(?:[a-zA-Z0-9_-]+:)?getcontentlength\b[^>]*>([\s\S]*?)</(?:[a-zA-Z0-9_-]+:)?getcontentlength>"#
        let colPattern = #"<(?:[a-zA-Z0-9_-]+:)?collection\s*/?>"#

        guard let respRegex = try? NSRegularExpression(pattern: responsePattern, options: [.caseInsensitive]),
              let hrefRegex = try? NSRegularExpression(pattern: hrefPattern, options: [.caseInsensitive]),
              let nameRegex = try? NSRegularExpression(pattern: namePattern, options: [.caseInsensitive]),
              let lenRegex = try? NSRegularExpression(pattern: lenPattern, options: [.caseInsensitive]),
              let colRegex = try? NSRegularExpression(pattern: colPattern, options: [.caseInsensitive]) else {
            return []
        }

        let trimmedReqPath = requestPath.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let fullRange = NSRange(xml.startIndex..., in: xml)
        return respRegex.matches(in: xml, range: fullRange).compactMap { match -> WebDavEntry? in
            guard let blockRange = Range(match.range(at: 1), in: xml) else { return nil }
            let block = String(xml[blockRange])
            let bRange = NSRange(block.startIndex..., in: block)
            guard let hrefMatch = hrefRegex.firstMatch(in: block, range: bRange),
                  let hRange = Range(hrefMatch.range(at: 1), in: block) else {
                return nil
            }
            let href = String(block[hRange]).trimmingCharacters(in: .whitespacesAndNewlines)
            let cleanedHref = href.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            if !trimmedReqPath.isEmpty && cleanedHref == trimmedReqPath {
                return nil
            }
            var displayName = ""
            if let nm = nameRegex.firstMatch(in: block, range: bRange),
               let nr = Range(nm.range(at: 1), in: block) {
                displayName = String(block[nr]).trimmingCharacters(in: .whitespacesAndNewlines)
            }
            if displayName.isEmpty {
                displayName = URL(string: href)?.lastPathComponent ?? href
            }
            let isDir = colRegex.firstMatch(in: block, range: bRange) != nil || href.hasSuffix("/")
            var len: Int64?
            if let lm = lenRegex.firstMatch(in: block, range: bRange),
               let lr = Range(lm.range(at: 1), in: block) {
                len = Int64(String(block[lr]).trimmingCharacters(in: .whitespacesAndNewlines))
            }
            return WebDavEntry(href: href, displayName: displayName, isDirectory: isDir, contentLengthBytes: len)
        }
    }
}
