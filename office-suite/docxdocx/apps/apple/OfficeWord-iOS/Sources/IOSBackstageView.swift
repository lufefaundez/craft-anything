import OfficeCoreKit
import SwiftUI
import UniformTypeIdentifiers

public struct IOSBackstageView: View {
    @Bindable var store: IOSDocxStore
    let isDark: Bool

    @State private var showFileImporter = false
    @State private var showDocxExporter = false
    @State private var showPdfExporter = false
    @State private var exportDocument: BinaryExportDocument?
    @State private var exportContentType: UTType = .docx
    @State private var exportFilename: String = "Documento1.docx"

    @State private var davUrl: String = ""
    @State private var davUser: String = ""
    @State private var davPass: String = ""
    @State private var davEntries: [WebDavEntry] = []
    @State private var davStatus: String = ""

    private var surfaces: OfficeTokens.SurfacePalette {
        isDark ? OfficeTokens.darkSurfaces : OfficeTokens.lightSurfaces
    }
    private var accent: Color {
        isDark ? OfficeTokens.AppAccents.Word.accentDark : OfficeTokens.AppAccents.Word.accentLight
    }

    public init(store: IOSDocxStore, isDark: Bool) {
        self.store = store
        self.isDark = isDark
    }

    public var body: some View {
        NavigationStack {
            List {
                templatesSection
                filesSection
                webDavSection
                recentsSection
            }
            .navigationTitle("Archivo")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Listo") { store.showBackstage = false }
                }
            }
        }
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.docx],
            allowsMultipleSelection: false
        ) { result in
            if case .success(let urls) = result, let url = urls.first {
                store.openSecurityScopedUrl(url)
            }
        }
        .fileExporter(
            isPresented: $showDocxExporter,
            document: exportDocument,
            contentType: exportContentType,
            defaultFilename: exportFilename
        ) { _ in }
        .fileExporter(
            isPresented: $showPdfExporter,
            document: exportDocument,
            contentType: .pdf,
            defaultFilename: exportFilename
        ) { _ in }
    }

    @ViewBuilder
    private var templatesSection: some View {
        Section("Nuevo desde plantilla") {
            ForEach(DocTemplateId.allCases) { tpl in
                Button {
                    store.newFromTemplate(tpl)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(tpl.titleEsCl)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(surfaces.text)
                        Text(tpl.subtitleEsCl)
                            .font(.system(size: 12))
                            .foregroundStyle(surfaces.textDim)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var filesSection: some View {
        Section("Local, iCloud Drive y Google Drive (Archivos)") {
            Button {
                showFileImporter = true
            } label: {
                Label("Abrir .docx desde Archivos / iCloud / Drive...", systemImage: "folder")
            }

            Button {
                if let data = try? store.saveDocxBytes() {
                    exportDocument = BinaryExportDocument(data: data)
                    exportContentType = .docx
                    exportFilename = store.documentTitle.hasSuffix(".docx") ? store.documentTitle : "\(store.documentTitle).docx"
                    showDocxExporter = true
                }
            } label: {
                Label("Guardar .docx como...", systemImage: "square.and.arrow.down")
            }

            Button {
                if let pdf = try? store.exportPdfBytes() {
                    exportDocument = BinaryExportDocument(data: pdf)
                    exportContentType = .pdf
                    exportFilename = store.documentTitle.replacingOccurrences(of: ".docx", with: "") + ".pdf"
                    showPdfExporter = true
                }
            } label: {
                Label("Exportar a PDF...", systemImage: "doc.richtext")
            }

            Button {
                store.showBackstage = false
                store.printActiveDocument()
            } label: {
                Label("Imprimir (AirPrint)", systemImage: "printer")
            }
        }
    }

    @ViewBuilder
    private var webDavSection: some View {
        Section("WebDAV propio (Nextcloud / OwnCloud / Servidor local)") {
            TextField("https://servidor/remote.php/dav/files/usuario", text: $davUrl)
                .textInputAutocapitalization(.never)
                .keyboardType(.URL)
            TextField("Usuario", text: $davUser)
                .textInputAutocapitalization(.never)
            SecureField("Contraseña", text: $davPass)

            HStack {
                Button("Conectar") {
                    Task { await listWebDav() }
                }
                .buttonStyle(.borderedProminent)
                .tint(accent)

                Spacer()

                Button("Subir actual") {
                    Task { await uploadCurrentWebDav() }
                }
                .buttonStyle(.bordered)
            }

            if !davStatus.isEmpty {
                Text(davStatus)
                    .font(.caption)
                    .foregroundStyle(surfaces.textDim)
            }

            ForEach(davEntries) { entry in
                Button {
                    Task { await downloadWebDav(entry) }
                } label: {
                    HStack {
                        Text(entry.displayName)
                        Spacer()
                        Text(entry.isDirectory ? "Carpeta" : "\(entry.contentLengthBytes ?? 0) B")
                            .font(.caption)
                            .foregroundStyle(surfaces.textDim)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var recentsSection: some View {
        if !store.recentDocs.isEmpty {
            Section("Recientes") {
                ForEach(store.recentDocs) { rec in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(rec.title).font(.subheadline.weight(.medium))
                        Text(rec.providerLabel).font(.caption).foregroundStyle(surfaces.textDim)
                    }
                }
            }
        }
    }

    private func listWebDav() async {
        do {
            let cfg = WebDavConfig(baseUrl: davUrl, username: davUser, password: davPass)
            davEntries = try await AppleWebDavClient().listDirectory(config: cfg)
            davStatus = "Conectado (\(davEntries.count) elementos)"
        } catch {
            davStatus = "Error WebDAV: \(error.localizedDescription)"
        }
    }

    private func downloadWebDav(_ entry: WebDavEntry) async {
        guard !entry.isDirectory else { return }
        do {
            let cfg = WebDavConfig(baseUrl: davUrl, username: davUser, password: davPass)
            let bytes = try await AppleWebDavClient().downloadBytes(config: cfg, hrefOrRelativePath: entry.href)
            store.openBytes(bytes, filename: entry.displayName, providerLabel: "WebDAV", origin: entry.href)
        } catch {
            davStatus = "Error descarga: \(error.localizedDescription)"
        }
    }

    private func uploadCurrentWebDav() async {
        do {
            let cfg = WebDavConfig(baseUrl: davUrl, username: davUser, password: davPass)
            let bytes = try store.saveDocxBytes()
            let remoteName = store.documentTitle.hasSuffix(".docx") ? store.documentTitle : "\(store.documentTitle).docx"
            try await AppleWebDavClient().uploadBytes(config: cfg, relativePath: remoteName, data: bytes)
            davStatus = "Subido a WebDAV: \(remoteName)"
        } catch {
            davStatus = "Error subida: \(error.localizedDescription)"
        }
    }
}

public extension UTType {
    static let docx = UTType(filenameExtension: "docx") ?? .data
}

public struct BinaryExportDocument: FileDocument {
    public static var readableContentTypes: [UTType] { [.docx, .pdf, .data] }
    public var data: Data

    public init(data: Data) {
        self.data = data
    }

    public init(configuration: ReadConfiguration) throws {
        self.data = configuration.file.regularFileContents ?? Data()
    }

    public func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
