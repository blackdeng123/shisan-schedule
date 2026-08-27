package com.shisan.campuspro.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenSourceCatalogParserTest {
    @Test
    fun `parser links libraries to generated license details`() {
        val json =
            """
            {
              "libraries": [
                {
                  "uniqueId": "org.example:beta",
                  "developers": [{"name": "Example Team"}],
                  "artifactVersion": "2.0.0",
                  "description": "Beta library",
                  "name": "Beta",
                  "website": "https://example.org/beta",
                  "licenses": ["MIT"]
                },
                {
                  "uniqueId": "org.example:alpha",
                  "developers": [],
                  "name": "Alpha",
                  "licenses": ["Apache-2.0"]
                }
              ],
              "licenses": {
                "Apache-2.0": {
                  "content": "Apache text",
                  "url": "https://spdx.org/licenses/Apache-2.0.html",
                  "spdxId": "Apache-2.0",
                  "name": "Apache License 2.0"
                },
                "MIT": {
                  "content": "MIT text",
                  "url": "https://spdx.org/licenses/MIT.html",
                  "spdxId": "MIT",
                  "name": "MIT License"
                }
              }
            }
            """.trimIndent()

        val catalog = OpenSourceCatalogParser.parse(json)

        assertEquals(listOf("Alpha", "Beta"), catalog.libraries.map { it.name })
        assertEquals("Example Team", catalog.libraries[1].developers.single())
        assertEquals("2.0.0", catalog.libraries[1].version)
        assertEquals("MIT License", catalog.libraries[1].licenses.single().name)
        assertEquals("MIT text", catalog.libraries[1].licenses.single().content)
        assertNull(catalog.libraries[0].version)
    }
}
