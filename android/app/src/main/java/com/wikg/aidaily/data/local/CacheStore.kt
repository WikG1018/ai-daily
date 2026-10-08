package com.wikg.aidaily.data.local

import android.content.Context
import com.wikg.aidaily.data.model.DailyIndex
import com.wikg.aidaily.data.model.Issue
import com.wikg.aidaily.data.remote.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 离线缓存：index 和单期 JSON 原样存到 filesDir（不会被系统清理）。 */
class CacheStore(context: Context) {
    private val dir = File(context.filesDir, "daily").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    suspend fun readIndex(): DailyIndex? = read(indexFile) { AppJson.decodeFromString(DailyIndex.serializer(), it) }
    suspend fun writeIndex(index: DailyIndex) = write(indexFile, AppJson.encodeToString(DailyIndex.serializer(), index))
    fun indexSavedAt(): Long = indexFile.lastModified()

    suspend fun readIssue(date: String): Issue? = read(issueFile(date)) { AppJson.decodeFromString(Issue.serializer(), it) }
    suspend fun writeIssue(issue: Issue) = write(issueFile(issue.date), AppJson.encodeToString(Issue.serializer(), issue))
    fun hasIssue(date: String) = issueFile(date).exists()
    fun cachedDates(): Set<String> = dir.listFiles()?.mapNotNull { f ->
        f.name.removePrefix("issue-").removeSuffix(".json").takeIf { f.name.startsWith("issue-") }
    }?.toSet().orEmpty()

    private fun issueFile(date: String) = File(dir, "issue-${date.filter { it.isDigit() || it == '-' }}.json")

    private suspend fun <T> read(f: File, parse: (String) -> T): T? = withContext(Dispatchers.IO) {
        if (!f.exists()) null else runCatching { parse(f.readText()) }.getOrNull()
    }

    private suspend fun write(f: File, text: String) = withContext(Dispatchers.IO) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}
