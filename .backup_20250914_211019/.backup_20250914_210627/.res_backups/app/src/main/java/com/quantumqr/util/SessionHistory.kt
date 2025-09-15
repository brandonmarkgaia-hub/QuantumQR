package com.quantumqr.util


import com.quantumqr.R

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SessionHistory {
    private const val PREF = "session_history_v1"
    private const val KEY = "rows"

    data class Row(val ts: Long, val format: String, val isUrl: Boolean, val content: String)

    fun append(ctx: Context, ts: Long, format: String, isUrl: Boolean, content: String) {
        val arr = readArray(ctx)
        val obj = JSONObject()
            .put("ts", ts)
            .put("format", format)
            .put("isUrl", isUrl)
            .put("content", content)
        arr.put(obj)
        writeArray(ctx, arr)
    }

    fun clear(ctx: Context) = writeArray(ctx, JSONArray())

    fun list(ctx: Context): List<Row> {
        val arr = readArray(ctx)
        val out = ArrayList<Row>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Row(
                    ts = o.optLong("ts"),
                    format = o.optString("format"),
                    isUrl = o.optBoolean("isUrl"),
                    content = o.optString("content")
                )
            )
        }
        return out
    }

    fun exportCsv(ctx: Context, dest: Uri): Int {
        val rows = list(ctx)
        if (rows.isEmpty()) return 0
        val tz = TimeZone.getTimeZone("UTC")
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = tz }

        ctx.contentResolver.openOutputStream(dest, "w")!!.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { w ->
                w.write("timestamp_utc,format,is_url,content\n")
                rows.forEach { r ->
                    val ts = sdf.format(Date(r.ts))
                    val content = r.content
                        .replace("\"", "\"\"")               // escape quotes
                        .replace("\r", " ")
                        .replace("\n", " ")
                    w.write("\"$ts\",\"${r.format}\",\"${r.isUrl}\",\"$content\"\n")
                }
                w.flush()
            }
        }
        return rows.size
    }

    private fun readArray(ctx: Context): JSONArray {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val s = p.getString(KEY, "[]") ?: "[]"
        return try { JSONArray(s) } catch (_: Exception) { JSONArray() }
    }

    private fun writeArray(ctx: Context, arr: JSONArray) {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        p.edit().putString(KEY, arr.toString()).apply()
    }
}