package org.samillimed.app.data.stockcheck

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A count in progress, kept on this phone so nothing is lost if the app closes. */
object StockCheckDraft {
    private const val PREFS = "stock_check_draft"

    /** counts: product id to (quantity in the smallest unit, the time it was counted). */
    class Draft(val type: String, val productIds: List<String>, val counts: Map<String, Pair<Long, Long>>)

    fun load(context: Context): Draft? {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("draft", null) ?: return null
        return runCatching {
            val o = JSONObject(text)
            val a = o.getJSONArray("products")
            val ids = (0 until a.length()).map { a.getString(it) }
            val counts = HashMap<String, Pair<Long, Long>>()
            val c = o.getJSONObject("counts")
            c.keys().forEach { k ->
                val pair = c.getJSONArray(k)
                counts[k] = pair.getLong(0) to pair.getLong(1)
            }
            Draft(o.getString("type"), ids, counts)
        }.getOrNull()
    }

    fun save(context: Context, draft: Draft) {
        val o = JSONObject()
        o.put("type", draft.type)
        o.put("products", JSONArray(draft.productIds))
        val c = JSONObject()
        draft.counts.forEach { (k, v) -> c.put(k, JSONArray(listOf(v.first, v.second))) }
        o.put("counts", c)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("draft", o.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("draft").apply()
    }
}
