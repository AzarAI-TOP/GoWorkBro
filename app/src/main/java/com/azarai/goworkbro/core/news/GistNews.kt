package com.azarai.goworkbro.core.news

import com.azarai.goworkbro.core.db.NewsCache
import com.azarai.goworkbro.core.util.Dates
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class NewsEdition(
    val date: String,
    val title: String,
    val markdown: String,
)

object GistNews {
    const val GIST_ID = "9eab46f314c078ed87dfb1fa9667df78"
    private const val API_URL = "https://api.github.com/gists/$GIST_ID"
    private val DATE_FILE = Regex("""^(\d{4}-\d{2}-\d{2})\.md$""")
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    class GistException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Fetches every dated markdown file from the public gist.
     * No authentication — the 60 req/h anonymous quota is ample for a
     * cache-first client that fetches at most once per hour.
     */
    suspend fun fetchAll(): List<NewsEdition> = withContext(Dispatchers.IO) {
        val connection = URL(API_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val code = connection.responseCode
            val body = if (code in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            if (code == 403 || code == 429) {
                throw GistException("rate_limited")
            }
            if (code !in 200..299) {
                throw GistException("HTTP $code: ${body.take(120)}")
            }
            parseGist(body)
        } catch (e: GistException) {
            throw e
        } catch (e: Exception) {
            throw GistException("network: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    /** Parses the gist API payload into dated editions (newest first). */
    fun parseGist(json: String): List<NewsEdition> {
        val files = JSONObject(json).optJSONObject("files") ?: return emptyList()
        val editions = mutableListOf<NewsEdition>()
        for (name in files.keys()) {
            val match = DATE_FILE.matchEntire(name) ?: continue
            val content = files.optJSONObject(name)?.optString("content") ?: continue
            if (content.isEmpty()) continue
            editions += fromMarkdown(match.groupValues[1], content)
        }
        return editions.sortedByDescending { it.date }
    }

    /** Strips YAML frontmatter and extracts the `# ` title (v1 semantics). */
    fun fromMarkdown(date: String, raw: String): NewsEdition {
        var content = raw
        if (content.startsWith("---")) {
            val end = content.indexOf("---", 3)
            if (end > 0) content = content.substring(end + 3)
        }
        content = content.trim()
        val title = content.lineSequence()
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")?.trim()
            ?.ifEmpty { null }
            ?: "USTC 每日要闻 — $date"
        return NewsEdition(date, title, content)
    }

    fun toCache(edition: NewsEdition): NewsCache = NewsCache(
        date = edition.date,
        title = edition.title,
        markdown = edition.markdown,
        cachedAt = Dates.nowIso(),
    )
}

/**
 * Cache-first news repository: Room is always the render source; the gist
 * refreshes in the background at most once per hour or on manual refresh.
 */
class NewsRepo(
    private val dao: com.azarai.goworkbro.core.db.NewsCacheDao,
    private val store: com.azarai.goworkbro.core.Store,
) {
    val MIN_FETCH_INTERVAL_MS = 30 * 60 * 1000L

    fun observeLatest(): Flow<NewsCache?> = dao.observeAll().map { it.firstOrNull() }

    fun observeAll(): Flow<List<NewsCache>> = dao.observeAll()

    suspend fun refresh(force: Boolean): Result<Int> {
        if (!force) {
            val last = store.get(com.azarai.goworkbro.core.Store.Keys.NEWS_LAST_FETCH)
                ?.toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - last < MIN_FETCH_INTERVAL_MS) {
                return Result.success(0)
            }
        }
        return runCatching {
            val editions = GistNews.fetchAll()
            dao.upsertAll(editions.map { GistNews.toCache(it) })
            store.set(
                com.azarai.goworkbro.core.Store.Keys.NEWS_LAST_FETCH,
                System.currentTimeMillis().toString(),
            )
            editions.size
        }
    }

    suspend fun stale(): Boolean {
        val last = store.get(com.azarai.goworkbro.core.Store.Keys.NEWS_LAST_FETCH)
            ?.toLongOrNull() ?: 0L
        return System.currentTimeMillis() - last >= MIN_FETCH_INTERVAL_MS
    }
}
