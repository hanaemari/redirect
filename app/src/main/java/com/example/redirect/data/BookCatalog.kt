package com.example.redirect.data

/**
 * Source of discoverable books. Today this is the bundled public-domain set plus a
 * metadata-only sample list; a licensed catalog or metadata API (e.g. Open Library)
 * would implement the same interface.
 */
interface BookCatalog {
    suspend fun search(query: String): List<SeedData.CatalogEntry>
    fun byId(id: String): SeedData.CatalogEntry?
}

class LocalSampleCatalog : BookCatalog {
    override suspend fun search(query: String): List<SeedData.CatalogEntry> {
        val q = query.trim()
        if (q.isEmpty()) return SeedData.allCatalog
        return SeedData.allCatalog.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.author.contains(q, ignoreCase = true) ||
                it.subjects.any { s -> s.contains(q, ignoreCase = true) }
        }
    }

    override fun byId(id: String) = SeedData.allCatalog.firstOrNull { it.id == id }
}
