package com.example.redirect.data

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Real-network timing of the discovery code path. Skipped unless explicitly enabled:
 *   ./gradlew testDebugUnitTest --tests '*DiscoveryLatencyProbe*' -Pprobe
 * Results are printed to the test's stdout (build/test-results/.../TEST-*DiscoveryLatencyProbe.xml).
 */
class DiscoveryLatencyProbe {

    @Test fun measure() = runBlocking {
        assumeTrue(System.getProperty("probe") == "true")
        for (query in listOf("", "dickens", "sherlock")) {
            repeat(2) { run ->
                val catalog = FreeBookCatalog() // fresh instance: no in-memory cache
                val start = System.nanoTime()
                val result = runCatching { catalog.browse(query) }
                val ms = (System.nanoTime() - start) / 1_000_000
                val page = result.getOrNull()
                println(
                    "PROBE query='$query' run=${run + 1} ms=$ms " +
                        (page?.let { "source=${it.source} books=${it.books.size}" } ?: "error=${result.exceptionOrNull()?.message}"),
                )
            }
        }
    }
}
