package app.ofiofi.docxdocx

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.ofiofi.docxdocx.ui.theme.OfficeTokens
import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTokenTest {
    @Test
    fun wordAccentMatchesMicrosoftWordBlue() {
        assertEquals("1.0.0", OfficeTokens.VERSION)
        assertEquals(0xFF185ABD.toInt(), OfficeTokens.AppAccents.Word.accentLight.toArgb())
        assertEquals(0xFF107C41.toInt(), OfficeTokens.AppAccents.Excel.accentLight.toArgb())
        assertEquals(0xFFB7472A.toInt(), OfficeTokens.AppAccents.Powerpoint.accentLight.toArgb())
        assertEquals(44.dp, OfficeTokens.TouchTargets.phoneMin)
        assertEquals(8.dp, OfficeTokens.Radii.ribbonCard)
    }
}
