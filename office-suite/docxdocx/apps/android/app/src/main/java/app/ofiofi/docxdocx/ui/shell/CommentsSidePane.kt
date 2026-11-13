package app.ofiofi.docxdocx.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ofiofi.docxdocx.ui.ribbon.RibbonAdvancedPresets
import app.ofiofi.docxdocx.ui.theme.OfficeTokens

@Composable
fun CommentsSidePane(
    comments: List<DocCommentItem>,
    surfaces: OfficeTokens.SurfacePalette,
    accent: Color,
    onExecuteCommand: (commandId: String, paramsJson: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftComment by remember { mutableStateOf("") }
    var replyingToId by remember { mutableIntStateOf(-1) }
    var replyDraft by remember { mutableStateOf("") }

    Surface(
        modifier = modifier
            .width(300.dp)
            .fillMaxHeight()
            .border(1.dp, surfaces.ribbonBorder),
        color = surfaces.panel,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxHeight().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Comentarios (${comments.count { it.parent == null }})",
                    color = surfaces.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallActionPill("‹ Ant.", surfaces) { onExecuteCommand("review.previousComment", "{}") }
                    SmallActionPill("Sig. ›", surfaces) { onExecuteCommand("review.nextComment", "{}") }
                    SmallActionPill("✕", surfaces) { onDismiss() }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Composer for new comment
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surfaces.ribbonCard, RoundedCornerShape(8.dp))
                    .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(surfaces.canvas, RoundedCornerShape(6.dp))
                        .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(6.dp))
                        .padding(6.dp),
                ) {
                    if (draftComment.isEmpty()) {
                        Text("Agregar comentario sobre la selección...", color = surfaces.textDim, fontSize = 11.sp)
                    }
                    BasicTextField(
                        value = draftComment,
                        onValueChange = { draftComment = it },
                        textStyle = TextStyle(color = surfaces.text, fontSize = 12.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = draftComment.isNotBlank()) {
                                val text = draftComment.trim()
                                draftComment = ""
                                onExecuteCommand("review.newComment", RibbonAdvancedPresets.buildCommentPayload(text))
                            },
                        color = if (draftComment.isNotBlank()) accent else surfaces.ribbonBorder,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "Comentar",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = surfaces.ribbonBorder)
            Spacer(Modifier.height(8.dp))

            val topLevel = comments.filter { it.parent == null }
            val repliesByParent = comments.filter { it.parent != null }.groupBy { it.parent }

            if (topLevel.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Sin comentarios todavía.",
                        color = surfaces.textDim,
                        fontSize = 12.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(topLevel, key = { it.id }) { comment ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (comment.resolved) surfaces.canvas.copy(alpha = 0.7f) else surfaces.ribbonCard,
                                    RoundedCornerShape(8.dp),
                                )
                                .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = comment.author.ifBlank { "Usuario" },
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                )
                                if (comment.resolved) {
                                    Text(
                                        text = "Resuelto",
                                        color = Color(0xFF107C41),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            Text(
                                text = comment.text,
                                color = if (comment.resolved) surfaces.textDim else surfaces.text,
                                fontSize = 12.sp,
                                textDecoration = if (comment.resolved) TextDecoration.LineThrough else TextDecoration.None,
                            )

                            val replies = repliesByParent[comment.id].orEmpty()
                            replies.forEach { reply ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 10.dp, top = 2.dp)
                                        .background(surfaces.canvas, RoundedCornerShape(6.dp))
                                        .padding(6.dp),
                                ) {
                                    Text(reply.author, color = accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    Text(reply.text, color = surfaces.text, fontSize = 11.sp)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SmallActionPill("Responder", surfaces) {
                                    replyingToId = if (replyingToId == comment.id) -1 else comment.id
                                    replyDraft = ""
                                }
                                SmallActionPill(if (comment.resolved) "Reabrir" else "Resolver", surfaces) {
                                    onExecuteCommand("review.resolveComment", """{"id":${comment.id}}""")
                                }
                                SmallActionPill("Eliminar", surfaces) {
                                    onExecuteCommand("review.deleteComment", """{"id":${comment.id}}""")
                                }
                            }

                            if (replyingToId == comment.id) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(surfaces.canvas, RoundedCornerShape(4.dp))
                                            .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(4.dp))
                                            .padding(6.dp),
                                    ) {
                                        if (replyDraft.isEmpty()) {
                                            Text("Responder...", color = surfaces.textDim, fontSize = 11.sp)
                                        }
                                        BasicTextField(
                                            value = replyDraft,
                                            onValueChange = { replyDraft = it },
                                            textStyle = TextStyle(color = surfaces.text, fontSize = 11.sp),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    SmallActionPill("Enviar", surfaces) {
                                        val txt = replyDraft.trim()
                                        if (txt.isNotEmpty()) {
                                            val escaped = RibbonAdvancedPresets.escapeJsonString(txt)
                                            onExecuteCommand("review.reply", """{"id":${comment.id},"text":"$escaped"}""")
                                            replyDraft = ""
                                            replyingToId = -1
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallActionPill(
    label: String,
    surfaces: OfficeTokens.SurfacePalette,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = surfaces.text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(surfaces.canvas)
            .border(1.dp, surfaces.ribbonBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
