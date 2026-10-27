package vn.thangmaytaman.crm

import android.content.Context

data class Pending(val cid: Int, val phone: String, val name: String, val startMs: Long)

/** Lưu cấu hình và cuộc gọi đang chờ (khách nào vừa bấm gọi). */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("tamancrm", Context.MODE_PRIVATE)

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) { sp.edit().putString("token", v).apply() }

    var folder: String
        get() = sp.getString("folder", "") ?: ""
        set(v) { sp.edit().putString("folder", v).apply() }

    var offhook: Boolean
        get() = sp.getBoolean("offhook", false)
        set(v) { sp.edit().putBoolean("offhook", v).apply() }

    fun setPending(p: Pending) {
        sp.edit()
            .putInt("p_cid", p.cid)
            .putString("p_phone", p.phone)
            .putString("p_name", p.name)
            .putLong("p_start", p.startMs)
            .apply()
    }

    fun peekPending(): Pending? {
        val cid = sp.getInt("p_cid", 0)
        if (cid <= 0) return null
        return Pending(cid, sp.getString("p_phone", "") ?: "", sp.getString("p_name", "") ?: "", sp.getLong("p_start", 0L))
    }

    fun clearPending() {
        sp.edit().remove("p_cid").remove("p_phone").remove("p_name").remove("p_start").apply()
    }

    @Synchronized
    fun isUploaded(key: String): Boolean {
        val s = sp.getString("uploaded", "") ?: ""
        return s.split("\n").contains(key)
    }

    @Synchronized
    fun markUploaded(key: String) {
        val list = (sp.getString("uploaded", "") ?: "").split("\n").filter { it.isNotEmpty() }.toMutableList()
        list.add(key)
        val cut = if (list.size > 300) list.subList(list.size - 300, list.size) else list
        sp.edit().putString("uploaded", cut.joinToString("\n")).apply()
    }
}
