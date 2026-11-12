package app.ofiofi.docxdocx

import android.content.Context
import org.json.JSONObject

data class RibbonControlSpec(
    val commandId: String,
    val kind: String,
    val row: String,
    val icon: String,
    val labelEsCl: String,
    val labelEnUs: String,
    val submenu: List<RibbonControlSpec> = emptyList(),
)

data class RibbonGroupSpec(
    val id: String,
    val labelEsCl: String,
    val labelEnUs: String,
    val controls: List<RibbonControlSpec>,
    val dialogCommandId: String? = null,
)

data class RibbonTabSpec(
    val id: String,
    val labelEsCl: String,
    val labelEnUs: String,
    val groups: List<RibbonGroupSpec>,
    val contextualTrigger: String? = null,
)

data class RibbonSchemaCatalog(
    val coreTabs: List<RibbonTabSpec>,
    val contextualTabs: List<RibbonTabSpec>,
    val qat: List<RibbonControlSpec>,
    val smartphoneQuickBar: List<RibbonControlSpec>,
)

object RibbonCatalogLoader {
    fun loadFromAssets(context: Context): RibbonSchemaCatalog {
        val raw = context.assets.open("ribbon-schema.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)

        val coreTabs = buildList {
            val arr = root.getJSONArray("tabs")
            for (i in 0 until arr.length()) {
                add(parseTab(arr.getJSONObject(i), contextual = false))
            }
        }
        val contextualTabs = buildList {
            val arr = root.getJSONArray("contextual_tabs")
            for (i in 0 until arr.length()) {
                add(parseTab(arr.getJSONObject(i), contextual = true))
            }
        }
        val qat = parseControlArray(root.getJSONObject("title_bar").getJSONArray("qat"))
        val quickBar = parseControlArray(
            root.getJSONObject("smartphone_quick_bar").getJSONArray("pinned_actions"),
        )
        return RibbonSchemaCatalog(
            coreTabs = coreTabs,
            contextualTabs = contextualTabs,
            qat = qat,
            smartphoneQuickBar = quickBar,
        )
    }

    private fun parseTab(obj: JSONObject, contextual: Boolean): RibbonTabSpec {
        val labels = obj.getJSONObject("labels")
        val groupsArr = obj.getJSONArray("groups")
        val groups = buildList {
            for (i in 0 until groupsArr.length()) {
                val g = groupsArr.getJSONObject(i)
                val gLabels = g.getJSONObject("labels")
                add(
                    RibbonGroupSpec(
                        id = g.getString("id"),
                        labelEsCl = gLabels.getString("es-CL"),
                        labelEnUs = gLabels.getString("en-US"),
                        controls = parseControlArray(g.getJSONArray("controls")),
                        dialogCommandId = g.optString("dialog_command_id").takeIf { it.isNotEmpty() },
                    ),
                )
            }
        }
        return RibbonTabSpec(
            id = obj.getString("id"),
            labelEsCl = labels.getString("es-CL"),
            labelEnUs = labels.getString("en-US"),
            groups = groups,
            contextualTrigger = if (contextual) obj.optString("context_trigger").takeIf { it.isNotEmpty() } else null,
        )
    }

    private fun parseControlArray(arr: org.json.JSONArray): List<RibbonControlSpec> =
        buildList {
            for (i in 0 until arr.length()) {
                add(parseControl(arr.getJSONObject(i)))
            }
        }

    private fun parseControl(obj: JSONObject): RibbonControlSpec {
        val labels = obj.getJSONObject("labels")
        val sub = if (obj.has("submenu")) parseControlArray(obj.getJSONArray("submenu")) else emptyList()
        return RibbonControlSpec(
            commandId = obj.getString("command_id"),
            kind = obj.getString("kind"),
            row = obj.opt("row")?.toString() ?: "0",
            icon = obj.getString("icon"),
            labelEsCl = labels.getString("es-CL"),
            labelEnUs = labels.getString("en-US"),
            submenu = sub,
        )
    }
}
