package vn.thangmaytaman.crm

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore

data class Rec(val key: String, val name: String, val size: Long, val modifiedMs: Long, val uri: Uri, val mime: String)

object RecordingFinder {
    val EXTS = setOf("m4a", "mp3", "amr", "3gp", "aac", "wav", "ogg", "opus", "flac", "mp4")

    fun ext(name: String): String = name.substringAfterLast('.', "").lowercase()

    private fun isAudio(name: String, mime: String): Boolean =
        ext(name) in EXTS || mime.startsWith("audio/")

    /** Các file ghi âm mới hơn sinceMs, mới nhất trước. Ưu tiên thư mục người dùng chọn, không có thì quét MediaStore. */
    fun find(ctx: Context, sinceMs: Long, limit: Int = 10): List<Rec> {
        val out = mutableListOf<Rec>()
        val folder = Prefs(ctx).folder
        if (folder.isNotEmpty()) {
            try { out.addAll(fromFolder(ctx, Uri.parse(folder), sinceMs)) } catch (e: Exception) { /* thư mục mất quyền → bỏ qua */ }
        }
        if (out.isEmpty()) {
            try { out.addAll(fromMediaStore(ctx, sinceMs, limit)) } catch (e: Exception) { }
        }
        return out.sortedByDescending { it.modifiedMs }.take(limit)
    }

    private fun fromFolder(ctx: Context, tree: Uri, sinceMs: Long): List<Rec> {
        val res = mutableListOf<Rec>()
        fun walk(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val cols = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            val sub = mutableListOf<String>()
            ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: ""
                    val mime = c.getString(2) ?: ""
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    val mod = if (c.isNull(4)) 0L else c.getLong(4)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < 2) sub.add(id)
                    } else if (isAudio(name, mime) && mod >= sinceMs) {
                        res.add(Rec("doc:$id", name, size, mod, DocumentsContract.buildDocumentUriUsingTree(tree, id), mime))
                    }
                }
            }
            for (s in sub) walk(s, depth + 1)
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        return res
    }

    private fun fromMediaStore(ctx: Context, sinceMs: Long, limit: Int): List<Rec> {
        val res = mutableListOf<Rec>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val sinceSec = sinceMs / 1000
        ctx.contentResolver.query(
            base, cols,
            MediaStore.Audio.Media.DATE_MODIFIED + " >= ? OR " + MediaStore.Audio.Media.DATE_ADDED + " >= ?",
            arrayOf(sinceSec.toString(), sinceSec.toString()),
            MediaStore.Audio.Media.DATE_MODIFIED + " DESC"
        )?.use { c ->
            while (c.moveToNext() && res.size < limit) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: "ghi_am"
                val size = c.getLong(2)
                val mod = maxOf(c.getLong(3), c.getLong(4)) * 1000
                val mime = c.getString(5) ?: ""
                res.add(Rec("ms:$id", name, size, mod, ContentUris.withAppendedId(base, id), mime))
            }
        }
        return res
    }
}
