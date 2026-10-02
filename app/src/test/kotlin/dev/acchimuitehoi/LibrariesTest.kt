package dev.acchimuitehoi

import com.mikepenz.aboutlibraries.entity.Developer
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import com.mikepenz.aboutlibraries.entity.Organization
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrariesTest {
    private val apache = License("Apache License 2.0", "https://spdx.org/licenses/Apache-2.0.html", spdxId = "Apache-2.0", hash = "Apache-2.0")
    private val mit = License("MIT License", "https://spdx.org/licenses/MIT.html", spdxId = "MIT", hash = "MIT")

    private fun library(id: String, version: String?, name: String, org: String?, devs: List<String>, licenses: Set<License>) = Library(
        uniqueId = id,
        artifactVersion = version,
        name = name,
        description = null,
        website = null,
        developers = devs.map { Developer(it, null) },
        organization = org?.let { Organization(it, null) },
        scm = null,
        licenses = licenses,
    )

    @Test
    fun listsEachLibraryWithVersionLicenseAndAuthors() {
        val libraries = listOf(
            library("androidx.activity:activity-compose", "1.10.0", "Activity Compose", "The Android Open Source Project", listOf("The Android Open Source Project"), setOf(apache)),
            library("org.slf4j:slf4j-api", "2.0.17", "SLF4J API Module", "QOS.ch", listOf("Ceki Gulcu"), setOf(mit)),
        )
        val expected = """
            このアプリには次の 2 個のライブラリが入っている。

            Activity Compose 1.10.0
              Apache License 2.0
              作者: The Android Open Source Project
              androidx.activity:activity-compose

            SLF4J API Module 2.0.17
              MIT License
              作者: QOS.ch・Ceki Gulcu
              org.slf4j:slf4j-api
        """.trimIndent()
        assertEquals(expected, formatLibraries(libraries))
    }

    @Test
    fun omitsMissingVersionAndAuthors() {
        val libraries = listOf(library("com.example:lib", null, "Lib", null, listOf(""), setOf(apache, mit)))
        val expected = """
            このアプリには次の 1 個のライブラリが入っている。

            Lib
              Apache License 2.0・MIT License
              com.example:lib
        """.trimIndent()
        assertEquals(expected, formatLibraries(libraries))
    }
}
