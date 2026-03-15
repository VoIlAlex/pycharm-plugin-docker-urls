package com.voilalex.dockerurls

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class DockerComposeClient {
    private val json = Json { ignoreUnknownKeys = true }

    fun listServices(composeProject: DockerComposeProject): Result<List<DockerServiceRow>> {
        val commandLine = GeneralCommandLine(
            "docker",
            "compose",
            "-f",
            composeProject.composeFile.toString(),
            "ps",
            "--all",
            "--format",
            "json"
        ).withWorkDirectory(composeProject.directory.toFile())

        return runCatching {
            val output = CapturingProcessHandler(commandLine).runProcess(15_000)
            if (output.exitCode != 0) {
                error(output.stderr.ifBlank { "docker compose ps failed with exit code ${output.exitCode}" })
            }

            parseRows(output.stdout)
                .sortedWith(
                    compareBy<DockerServiceRow> { it.status.sortOrder }
                        .thenBy { it.serviceName.lowercase() }
                )
        }
    }

    private fun parseRows(rawJson: String): List<DockerServiceRow> {
        if (rawJson.isBlank()) {
            return emptyList()
        }

        val containers = runCatching {
            val element = json.parseToJsonElement(rawJson)
            when (element) {
                is JsonArray -> element.map { json.decodeFromJsonElement<ComposePsContainer>(it) }
                is JsonObject -> listOf(json.decodeFromJsonElement<ComposePsContainer>(element))
                else -> emptyList()
            }
        }.getOrElse {
            rawJson
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { json.decodeFromString<ComposePsContainer>(it) }
                .toList()
        }

        return containers.map { container ->
            val statusText = container.state
                ?.takeIf { it.isNotBlank() }
                ?: container.status.orEmpty().ifBlank { "unknown" }

            val urls = container.publishers
                .orEmpty()
                .asSequence()
                .filter { it.publishedPort != null && it.protocol.equals("tcp", ignoreCase = true) }
                .map { "http://localhost:${it.publishedPort}" }
                .distinct()
                .toList()

            DockerServiceRow(
                serviceName = container.service ?: container.name ?: "unknown",
                status = ServiceStatus.from(statusText),
                statusText = statusText,
                urls = urls
            )
        }
    }
}

@Serializable
private data class ComposePsContainer(
    @SerialName("Service")
    val service: String? = null,
    @SerialName("Name")
    val name: String? = null,
    @SerialName("State")
    val state: String? = null,
    @SerialName("Status")
    val status: String? = null,
    @SerialName("Publishers")
    val publishers: List<ComposePublisher>? = null
)

@Serializable
private data class ComposePublisher(
    @SerialName("PublishedPort")
    val publishedPort: Int? = null,
    @SerialName("Protocol")
    val protocol: String? = null
)
