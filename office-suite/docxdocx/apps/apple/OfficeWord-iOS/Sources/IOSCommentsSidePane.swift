import OfficeCoreKit
import SwiftUI

/// Review & Comments side panel (for iPad) or modal sheet (for iPhone) matching Word's comment thread model.
public struct IOSCommentsSidePane: View {
    @Bindable var store: IOSDocxStore
    public let surfaces: OfficeTokens.SurfacePalette
    public let accent: Color
    public let onDismiss: () -> Void

    @State private var draftComment: String = ""
    @State private var replyingToId: Int?
    @State private var replyDraft: String = ""

    public init(
        store: IOSDocxStore,
        surfaces: OfficeTokens.SurfacePalette,
        accent: Color,
        onDismiss: @escaping () -> Void
    ) {
        self.store = store
        self.surfaces = surfaces
        self.accent = accent
        self.onDismiss = onDismiss
    }

    public var body: some View {
        let rootComments = store.comments.filter { $0.parent == nil }
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Text("Comentarios (\(rootComments.count))")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(surfaces.text)
                Spacer()
                pillButton("‹ Ant.") { store.executeCommand("review.previousComment") }
                pillButton("Sig. ›") { store.executeCommand("review.nextComment") }
                pillButton("✕") { onDismiss() }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(surfaces.ribbonCard)

            Divider()

            // New comment composer
            VStack(alignment: .trailing, spacing: 6) {
                TextField("Escribir comentario de revisión...", text: $draftComment, axis: .vertical)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12.5))
                    .foregroundStyle(surfaces.text)
                    .lineLimit(2...4)
                    .padding(8)
                    .background(surfaces.canvas)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(surfaces.ribbonBorder, lineWidth: 1)
                    )
                Button {
                    let trimmed = draftComment.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !trimmed.isEmpty else { return }
                    store.executeCommand("review.newComment", paramsJson: OfficeDocCommentParser.newCommentPayload(trimmed))
                    draftComment = ""
                } label: {
                    Text("Comentar")
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 5)
                        .background(draftComment.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? surfaces.textDim.opacity(0.4) : accent)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                }
                .buttonStyle(.plain)
                .disabled(draftComment.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(10)
            .background(surfaces.panel)

            Divider()

            if rootComments.isEmpty {
                VStack(spacing: 8) {
                    Spacer()
                    Image(systemName: "text.bubble")
                        .font(.system(size: 28))
                        .foregroundStyle(surfaces.textDim.opacity(0.5))
                    Text("Sin comentarios en este documento")
                        .font(.system(size: 12.5, weight: .medium))
                        .foregroundStyle(surfaces.textDim)
                    Text("Ubica el cursor o selecciona texto y escribe arriba para agregar un hilo de revisión.")
                        .font(.system(size: 11))
                        .foregroundStyle(surfaces.textDim.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                    Spacer()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(rootComments) { comment in
                            let replies = store.comments.filter { $0.parent == comment.id }
                            commentThreadCard(comment: comment, replies: replies)
                        }
                    }
                    .padding(10)
                }
            }
        }
        .background(surfaces.panel)
        .overlay(alignment: .leading) {
            Rectangle()
                .fill(surfaces.ribbonBorder)
                .frame(width: 1)
        }
    }

    @ViewBuilder
    private func commentThreadCard(comment: OfficeDocCommentItem, replies: [OfficeDocCommentItem]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(comment.author.isEmpty ? "Usuario" : comment.author)
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(accent)
                Spacer()
                if comment.resolved {
                    Text("Resuelto")
                        .font(.system(size: 9.5, weight: .semibold))
                        .foregroundStyle(Color.green)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.green.opacity(0.14))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }
            }

            Text(comment.text)
                .font(.system(size: 12))
                .foregroundStyle(comment.resolved ? surfaces.textDim : surfaces.text)
                .strikethrough(comment.resolved)

            ForEach(replies) { reply in
                VStack(alignment: .leading, spacing: 2) {
                    Text(reply.author.isEmpty ? "Respuesta" : reply.author)
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(surfaces.textDim)
                    Text(reply.text)
                        .font(.system(size: 11.5))
                        .foregroundStyle(surfaces.text)
                }
                .padding(.leading, 8)
                .padding(.vertical, 4)
                .overlay(alignment: .leading) {
                    Rectangle().fill(accent.opacity(0.45)).frame(width: 2)
                }
            }

            HStack(spacing: 6) {
                pillButton("Responder") {
                    replyingToId = (replyingToId == comment.id) ? nil : comment.id
                    replyDraft = ""
                }
                pillButton(comment.resolved ? "Reabrir" : "Resolver") {
                    store.executeCommand("review.resolveComment", paramsJson: #"{"id":\#(comment.id)}"#)
                }
                pillButton("Eliminar") {
                    store.executeCommand("review.deleteComment", paramsJson: #"{"id":\#(comment.id)}"#)
                }
            }

            if replyingToId == comment.id {
                HStack(spacing: 6) {
                    TextField("Responder...", text: $replyDraft)
                        .textFieldStyle(.plain)
                        .font(.system(size: 11.5))
                        .foregroundStyle(surfaces.text)
                        .padding(6)
                        .background(surfaces.canvas)
                        .overlay(
                            RoundedRectangle(cornerRadius: 5)
                                .stroke(surfaces.ribbonBorder, lineWidth: 1)
                        )
                    Button("Enviar") {
                        let txt = replyDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !txt.isEmpty else { return }
                        store.executeCommand("review.reply", paramsJson: OfficeDocCommentParser.replyCommentPayload(id: comment.id, text: txt))
                        replyDraft = ""
                        replyingToId = nil
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(accent)
                    .clipShape(RoundedRectangle(cornerRadius: 5))
                    .buttonStyle(.plain)
                }
                .padding(.top, 2)
            }
        }
        .padding(10)
        .background(surfaces.ribbonCard)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(surfaces.ribbonBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func pillButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 10.5, weight: .medium))
                .foregroundStyle(surfaces.textDim)
                .padding(.horizontal, 7)
                .padding(.vertical, 4)
                .background(surfaces.panel)
                .overlay(
                    RoundedRectangle(cornerRadius: 5)
                        .stroke(surfaces.ribbonBorder, lineWidth: 0.8)
                )
                .clipShape(RoundedRectangle(cornerRadius: 5))
        }
        .buttonStyle(.plain)
    }
}
