package com.voilalex.dockerurls

import com.intellij.openapi.project.Project
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString

data class DockerComposeProject(
    val composeFile: Path,
    val directory: Path,
    val displayName: String
)

object DockerComposeLocator {
    private val composeFileNames = setOf(
        "docker-compose.yml",
        "docker-compose.yaml",
        "compose.yml",
        "compose.yaml"
    )

    private val ignoredDirectories = setOf(
        ".git",
        ".idea",
        ".gradle",
        ".venv",
        "venv",
        "node_modules",
        "build",
        "dist",
        "target",
        "out"
    )

    fun findComposeProjects(project: Project): List<DockerComposeProject> {
        val basePath = project.basePath ?: return emptyList()
        val root = Path.of(basePath)
        if (!root.exists() || !root.isDirectory()) {
            return emptyList()
        }

        val composeFiles = mutableListOf<Path>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != root && dir.name in ignoredDirectories) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (file.name in composeFileNames) {
                    composeFiles.add(file)
                }
                return FileVisitResult.CONTINUE
            }
        })

        return composeFiles
            .sortedBy { root.relativize(it).pathString }
            .map { composeFile ->
                val directory = composeFile.parent
                val relativeDirectory = root.relativize(directory).pathString.ifBlank { "." }
                DockerComposeProject(
                    composeFile = composeFile,
                    directory = directory,
                    displayName = if (relativeDirectory == ".") {
                        composeFile.fileName.toString()
                    } else {
                        "$relativeDirectory (${composeFile.fileName})"
                    }
                )
            }
    }
}
