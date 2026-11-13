import OfficeCoreKit
import SwiftUI

enum MacBackstageSection: String, CaseIterable, Identifiable {
    case home = "Inicio"
    case newDoc = "Nuevo"
    case open = "Abrir"
    case saveExport = "Guardar / PDF"
    case webdav = "WebDAV"
    case info = "Información"

    var id: String { rawValue }
}

struct MacBackstageView: View {
    @Bindable var model: DocxWorkspaceModel
    @Environment(\.colorScheme) private var colorScheme

    @State private var section: MacBackstageSection = .home
    @State private var davUrl: String = "https://"
    @State private var davUser: String = ""
    @State private var davPass: String = ""
    @State private var davEntries: [WebDavEntry] = []
    @State private var davStatus: String?

    var body: some View {
        let surfaces = colorScheme == .dark ? OfficeTokens.darkSurfaces : OfficeTokens.lightSurfaces
        let accent = colorScheme == .dark ? OfficeTokens.AppAccents.Word.accentDark : OfficeTokens.AppAccents.Word.accentLight

        HStack(spacing: 0) {
            // Word Blue Left Rail
            VStack(alignment: .leading, spacing: 6) {
                Button {
                    model.showBackstage = false
                } label: {
                    HStack(spacing: 6) {
                        Text("←")
                        Text("Volver al documento")
                    }
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.vertical, 8)
                    .padding(.horizontal, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)

                Divider().overlay(Color.white.opacity(0.25))

                ForEach(MacBackstageSection.allCases) { item in
                    Button {
                        section = item
                    } label: {
                        Text(item.rawValue)
                            .font(.system(size: 13, weight: section == item ? .bold : .regular))
                            .foregroundStyle(.white)
                            .padding(.vertical, 9)
                            .padding(.horizontal, 12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(section == item ? Color.white.opacity(0.22) : Color.clear)
                            .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.button))
                    }
                    .buttonStyle(.plain)
                }

                Spacer()
                Text("OfiOfi Free • Mac\nSin nube propietaria")
                    .font(.system(size: 11))
                    .foregroundStyle(.white.opacity(0.8))
                    .padding(.horizontal, 10)
            }
            .padding(12)
            .frame(width: 215)
            .frame(maxHeight: .infinity)
            .background(accent)

            // Right Content Pane
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    switch section {
                    case .home, .newDoc:
                        Text(section == .home ? "Plantillas y documentos" : "Crear documento nuevo")
                            .font(.title2.bold())
                            .foregroundStyle(surfaces.text)
                        ForEach(DocTemplateId.allCases) { tpl in
                            Button {
                                model.newFromTemplate(tpl)
                            } label: {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(tpl.titleEsCl)
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(accent)
                                    Text(tpl.subtitleEsCl)
                                        .font(.system(size: 12))
                                        .foregroundStyle(surfaces.textDim)
                                }
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(surfaces.ribbonCard)
                                .overlay(
                                    RoundedRectangle(cornerRadius: OfficeTokens.Radii.ribbonCard)
                                        .stroke(surfaces.ribbonBorder, lineWidth: 1)
                                )
                                .clipShape(RoundedRectangle(cornerRadius: OfficeTokens.Radii.ribbonCard))
                            }
                            .buttonStyle(.plain)
                        }

                    case .open:
                        Text("Abrir archivo (.docx, .doc, .odt, .rtf)")
                            .font(.title2.bold())
                            .foregroundStyle(surfaces.text)
                        Text("Compatible con carpetas locales, iCloud Drive y Google Drive sincronizado en Finder.")
                            .font(.system(size: 12))
                            .foregroundStyle(surfaces.textDim)
                        Button("Elegir archivo en Mac / iCloud / Google Drive...") {
                            model.presentOpenPanel()
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(accent)

                    case .saveExport:
                        Text("Guardar, Exportar o Imprimir")
                            .font(.title2.bold())
                            .foregroundStyle(surfaces.text)
                        HStack(spacing: 10) {
                            Button("Guardar (.docx)") { model.saveDocument() }
                                .buttonStyle(.borderedProminent)
                                .tint(accent)
                            Button("Guardar como...") { model.presentSaveAsPanel() }
                                .buttonStyle(.bordered)
                            Button("Exportar PDF") { model.presentExportPdfPanel() }
                                .buttonStyle(.bordered)
                            Button("Imprimir (⌘P)") { model.printDocument() }
                                .buttonStyle(.bordered)
                        }

                    case .webdav:
                        Text("Conector WebDAV propio")
                            .font(.title2.bold())
                            .foregroundStyle(surfaces.text)
                        Text("Conecta tu servidor Nextcloud, ownCloud o Synology DAV directamente, sin pasar por ninguna nube propietaria.")
                            .font(.system(size: 12))
                            .foregroundStyle(surfaces.textDim)

                        VStack(alignment: .leading, spacing: 8) {
                            TextField("URL WebDAV (https://...)", text: $davUrl)
                                .textFieldStyle(.roundedBorder)
                            TextField("Usuario", text: $davUser)
                                .textFieldStyle(.roundedBorder)
                            SecureField("Contraseña / Token de aplicación", text: $davPass)
                                .textFieldStyle(.roundedBorder)
                        }

                        HStack(spacing: 10) {
                            Button("Conectar y listar") {
                                Task {
                                    davStatus = "Conectando a WebDAV..."
                                    do {
                                        let cfg = WebDavConfig(baseUrl: davUrl, username: davUser, password: davPass)
                                        davEntries = try await AppleWebDavClient().listDirectory(config: cfg)
                                        davStatus = "\(davEntries.count) elementos encontrados"
                                    } catch {
                                        davStatus = error.localizedDescription
                                    }
                                }
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(accent)

                            Button("Subir \(model.documentTitle)") {
                                Task {
                                    davStatus = "Subiendo documento..."
                                    do {
                                        let cfg = WebDavConfig(baseUrl: davUrl, username: davUser, password: davPass)
                                        let data = try model.session.saveDocxBytes()
                                        let name = model.documentTitle.hasSuffix(".docx") ? model.documentTitle : "\(model.documentTitle).docx"
                                        try await AppleWebDavClient().uploadBytes(config: cfg, relativePath: name, data: data)
                                        davStatus = "Subido correctamente a WebDAV"
                                    } catch {
                                        davStatus = error.localizedDescription
                                    }
                                }
                            }
                            .buttonStyle(.bordered)
                        }

                        if let davStatus {
                            Text(davStatus)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(accent)
                        }

                        ForEach(davEntries) { entry in
                            Button {
                                guard !entry.isDirectory else { return }
                                Task {
                                    davStatus = "Descargando \(entry.displayName)..."
                                    do {
                                        let cfg = WebDavConfig(baseUrl: davUrl, username: davUser, password: davPass)
                                        let bytes = try await AppleWebDavClient().downloadBytes(config: cfg, hrefOrRelativePath: entry.href)
                                        model.openBytes(bytes, filename: entry.displayName)
                                    } catch {
                                        davStatus = error.localizedDescription
                                    }
                                }
                            } label: {
                                HStack {
                                    Text((entry.isDirectory ? "📁 " : "📄 ") + entry.displayName)
                                        .foregroundStyle(surfaces.text)
                                    Spacer()
                                    if let len = entry.contentLengthBytes {
                                        Text("\(len / 1024) KB")
                                            .font(.caption)
                                            .foregroundStyle(surfaces.textDim)
                                    }
                                }
                                .padding(10)
                                .background(surfaces.ribbonCard)
                                .overlay(
                                    RoundedRectangle(cornerRadius: OfficeTokens.Radii.button)
                                        .stroke(surfaces.ribbonBorder, lineWidth: 1)
                                )
                            }
                            .buttonStyle(.plain)
                        }

                    case .info:
                        Text("Información del documento")
                            .font(.title2.bold())
                            .foregroundStyle(surfaces.text)
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Archivo: \(model.documentTitle)")
                            Text("Páginas: \(model.pageCount)")
                            Text("Palabras: \(model.wordCount)")
                            Text("Región e idioma: Español (Chile) es-CL")
                            Text("Almacenamiento: Local / iCloud Drive / Google Drive / WebDAV")
                            Text("Suite OfiOfi 100 % gratuita • Motor nativo Rust + Metal/CoreGraphics")
                                .foregroundStyle(accent)
                                .fontWeight(.semibold)
                        }
                        .font(.system(size: 13))
                        .foregroundStyle(surfaces.text)
                    }
                }
                .padding(22)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(surfaces.panel)
        }
        .frame(width: 760, height: 520)
    }
}
