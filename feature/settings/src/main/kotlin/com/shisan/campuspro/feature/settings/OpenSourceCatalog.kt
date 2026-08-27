package com.shisan.campuspro.feature.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class OpenSourceLicense(
    val id: String,
    val name: String,
    val url: String?,
    val content: String,
)

data class OpenSourceLibrary(
    val uniqueId: String,
    val name: String,
    val version: String?,
    val description: String?,
    val website: String?,
    val developers: List<String>,
    val licenses: List<OpenSourceLicense>,
)

data class OpenSourceCatalog(
    val libraries: List<OpenSourceLibrary>,
)

object OpenSourceCatalogParser {
    fun parse(json: String): OpenSourceCatalog {
        val root = Json.parseToJsonElement(json).jsonObject
        val licensesById = root["licenses"]
            ?.jsonObject
            .orEmpty()
            .mapValues { (id, element) ->
                val value = element.jsonObject
                OpenSourceLicense(
                    id = value.string("spdxId") ?: id,
                    name = value.string("name") ?: id,
                    url = value.string("url"),
                    content = value.string("content").orEmpty(),
                )
            }

        val libraries = root["libraries"]
            ?.jsonArray
            .orEmpty()
            .map { element ->
                val value = element.jsonObject
                OpenSourceLibrary(
                    uniqueId = value.string("uniqueId").orEmpty(),
                    name = value.string("name") ?: value.string("uniqueId").orEmpty(),
                    version = value.string("artifactVersion"),
                    description = value.string("description"),
                    website = value.string("website"),
                    developers = value["developers"]
                        ?.jsonArray
                        .orEmpty()
                        .mapNotNull { it.jsonObject.string("name") },
                    licenses = value["licenses"]
                        ?.jsonArray
                        .orEmpty()
                        .mapNotNull { licenseId -> licensesById[licenseId.jsonPrimitive.content] },
                )
            }
            .sortedBy { it.name.lowercase() }

        return OpenSourceCatalog(libraries = libraries)
    }
}

private fun JsonObject.string(key: String): String? =
    get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
