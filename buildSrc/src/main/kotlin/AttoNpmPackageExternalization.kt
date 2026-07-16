import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files

abstract class ExternalizeCommonsNpmPackageDependencies : DefaultTask() {
    @get:Input
    abstract val npmPublicationProjects: ListProperty<String>

    @get:Input
    abstract val apiDependencyProjectNames: ListProperty<String>

    @get:Input
    abstract val currentProjectName: Property<String>

    @get:Input
    abstract val rootProjectName: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

    @get:Internal
    abstract val packageDirectory: DirectoryProperty

    @get:Internal
    abstract val canonicalPackageDirectory: DirectoryProperty

    @get:Internal
    abstract val rootProjectDirectory: DirectoryProperty

    @TaskAction
    fun externalize() {
        val stagingDirectory = packageDirectory.get().asFile
        if (!stagingDirectory.isDirectory) {
            return
        }

        val packageJsonFile = stagingDirectory.resolve("package.json")
        require(packageJsonFile.isFile) {
            "package.json was not found at ${packageJsonFile.absolutePath}."
        }

        val packagePath = stagingDirectory.toPath()
        val publishedProjects = npmPublicationProjects.get().toSet()
        val currentProjectName = currentProjectName.get()
        val internalModuleFiles =
            publishedProjects
                .filterNot { it == currentProjectName }
                .associateWith { moduleFileName(rootProjectName.get(), it) }
        val internalModuleFileNames = internalModuleFiles.values.toSet()
        val internalDependencyProjectNames =
            apiDependencyProjectNames
                .get()
                .filterTo(mutableSetOf()) { it != currentProjectName && it in publishedProjects }

        val runtimeModuleFilesByOwner = runtimeModuleFilesByOwner()
        replaceWithCanonicalGeneratedFiles(stagingDirectory, currentProjectName, runtimeModuleFilesByOwner)

        runtimeModuleFilesByOwner.forEach { (ownerProjectName, moduleFileNames) ->
            if (currentProjectName == ownerProjectName) {
                return@forEach
            }

            val importsExternalized =
                externalizeRuntimeModuleImports(
                    stagingDirectory,
                    ownerProjectName,
                    moduleFileNames,
                )
            if (importsExternalized || ownerProjectName == COMMONS_CORE_PROJECT_NAME) {
                internalDependencyProjectNames.add(ownerProjectName)
            }
        }

        stagingDirectory
            .walkTopDown()
            .filter { it.isFile && it.extension == "mjs" && it.name !in internalModuleFileNames }
            .forEach { moduleFile ->
                var moduleText = moduleFile.readText()
                var changed = false

                internalModuleFiles.forEach { (dependencyProjectName, moduleFileName) ->
                    val localSpecifier = "./$moduleFileName"
                    val packageSpecifier = "${packageName(dependencyProjectName)}/$moduleFileName"
                    val updatedModuleText =
                        moduleText
                            .replace("'$localSpecifier'", "'$packageSpecifier'")
                            .replace("\"$localSpecifier\"", "\"$packageSpecifier\"")

                    if (updatedModuleText != moduleText) {
                        changed = true
                        internalDependencyProjectNames.add(dependencyProjectName)
                        moduleText = updatedModuleText
                    }
                }

                if (changed) {
                    moduleFile.writeText(moduleText)
                }
            }

        internalModuleFiles.values.forEach { moduleFileName ->
            Files.deleteIfExists(packagePath.resolve(moduleFileName))
            Files.deleteIfExists(packagePath.resolve("$moduleFileName.map"))
        }
        runtimeModuleFilesByOwner.forEach { (ownerProjectName, moduleFileNames) ->
            if (currentProjectName != ownerProjectName) {
                moduleFileNames.forEach { moduleFileName ->
                    Files.deleteIfExists(packagePath.resolve(moduleFileName))
                    Files.deleteIfExists(packagePath.resolve("$moduleFileName.map"))
                }
            }
        }

        val unresolvedInternalImports =
            stagingDirectory
                .walkTopDown()
                .filter { it.isFile && it.extension == "mjs" }
                .flatMap { moduleFile ->
                    val moduleText = moduleFile.readText()
                    internalModuleFiles.values
                        .filter { moduleFileName ->
                            moduleText.contains("'./$moduleFileName'") ||
                                moduleText.contains("\"./$moduleFileName\"")
                        }.map { moduleFileName -> "${moduleFile.name} imports ./$moduleFileName" }
                }.toList()
        check(unresolvedInternalImports.isEmpty()) {
            "Generated npm package still has relative internal Commons imports: " +
                unresolvedInternalImports.joinToString()
        }
        runtimeModuleFilesByOwner.forEach { (ownerProjectName, moduleFileNames) ->
            if (currentProjectName != ownerProjectName) {
                val unresolvedRuntimeImports =
                    stagingDirectory
                        .walkTopDown()
                        .filter { it.isFile && it.extension == "mjs" }
                        .flatMap { moduleFile ->
                            val moduleText = moduleFile.readText()
                            moduleFileNames
                                .filter { moduleFileName ->
                                    moduleText.contains("'./$moduleFileName'") ||
                                        moduleText.contains("\"./$moduleFileName\"")
                                }.map { moduleFileName -> "${moduleFile.name} imports ./$moduleFileName" }
                        }.toList()
                check(unresolvedRuntimeImports.isEmpty()) {
                    "Generated npm package still has relative runtime imports owned by $ownerProjectName: " +
                        unresolvedRuntimeImports.joinToString()
                }
            }
        }

        internalDependencyProjectNames.addAll(externalizeTypeDeclarations(stagingDirectory, currentProjectName))
        if (internalDependencyProjectNames.isNotEmpty()) {
            addInternalDependencies(packageJsonFile, internalDependencyProjectNames)
        }
    }

    private fun runtimeModuleFilesByOwner(): Map<String, Set<String>> {
        val canonicalRuntimeFiles =
            canonicalPackageDirectory.asFile.orNull
                ?.takeIf(File::isDirectory)
                ?.listFiles()
                .orEmpty()
                .asSequence()
                .filter { it.isFile && it.extension == "mjs" }
                .map(File::getName)
                .toSet()

        return mapOf(
            COMMONS_CORE_PROJECT_NAME to coreRuntimeModuleFileNames,
            COMMONS_TRANSPORT_PROJECT_NAME to
                canonicalRuntimeFiles.filterTo(transportRuntimeModuleFileNames.toMutableSet()) {
                    it.startsWith(KTOR_RUNTIME_MODULE_PREFIX)
                },
        )
    }

    private fun replaceWithCanonicalGeneratedFiles(
        stagingDirectory: File,
        currentProjectName: String,
        runtimeModuleFilesByOwner: Map<String, Set<String>>,
    ) {
        val canonicalDirectory = canonicalPackageDirectory.asFile.orNull ?: return
        if (!canonicalDirectory.isDirectory) {
            return
        }

        val ownedRuntimeModuleFileNames = runtimeModuleFilesByOwner[currentProjectName].orEmpty()
        canonicalDirectory
            .walkTopDown()
            .filter { it.isFile && (it.extension == "mjs" || it.name.endsWith(".mjs.map")) }
            .filter { canonicalFile ->
                val moduleFileName = canonicalFile.name.removeSuffix(".map")
                stagingDirectory.resolve(canonicalFile.name).isFile ||
                    moduleFileName in ownedRuntimeModuleFileNames
            }
            .forEach { canonicalFile ->
                canonicalFile.copyTo(stagingDirectory.resolve(canonicalFile.name), overwrite = true)
            }
    }

    private fun externalizeRuntimeModuleImports(
        stagingDirectory: File,
        ownerProjectName: String,
        moduleFileNames: Set<String>,
    ): Boolean {
        var importsExternalized = false
        stagingDirectory
            .walkTopDown()
            .filter { it.isFile && it.extension == "mjs" }
            .forEach { moduleFile ->
                var moduleText = moduleFile.readText()
                var changed = false

                moduleFileNames.forEach { moduleFileName ->
                    val localSpecifier = "./$moduleFileName"
                    val packageSpecifier = "${packageName(ownerProjectName)}/$moduleFileName"
                    val updatedModuleText =
                        moduleText
                            .replace("'$localSpecifier'", "'$packageSpecifier'")
                            .replace("\"$localSpecifier\"", "\"$packageSpecifier\"")

                    if (updatedModuleText != moduleText) {
                        changed = true
                        importsExternalized = true
                        moduleText = updatedModuleText
                    }
                }

                if (changed) {
                    moduleFile.writeText(moduleText)
                }
            }
        return importsExternalized
    }

    private fun addInternalDependencies(
        packageJsonFile: File,
        internalDependencyProjectNames: Set<String>,
    ) {
        @Suppress("UNCHECKED_CAST")
        val packageJson = JsonSlurper().parse(packageJsonFile) as Map<String, Any?>
        val existingDependencies = packageJson["dependencies"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val updatedDependencies = linkedMapOf<String, Any?>()

        internalDependencyProjectNames
            .sorted()
            .forEach { dependencyProjectName ->
                updatedDependencies[packageName(dependencyProjectName)] = packageVersion.get()
            }
        existingDependencies.forEach { (dependencyName, dependencyVersion) ->
            if (dependencyName is String) {
                updatedDependencies.putIfAbsent(dependencyName, dependencyVersion)
            }
        }

        val updatedPackageJson = linkedMapOf<String, Any?>()
        packageJson.forEach { (key, value) ->
            updatedPackageJson[key] = if (key == "dependencies") updatedDependencies else value
        }
        if ("dependencies" !in updatedPackageJson) {
            updatedPackageJson["dependencies"] = updatedDependencies
        }

        packageJsonFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(updatedPackageJson)) + "\n")
    }

    private fun externalizeTypeDeclarations(
        stagingDirectory: File,
        currentProjectName: String,
    ): Set<String> {
        if (currentProjectName == COMMONS_CORE_PROJECT_NAME || currentProjectName == COMMONS_JS_PROJECT_NAME) {
            return emptySet()
        }

        val declarationFile =
            stagingDirectory.resolve(declarationFileName(rootProjectName.get(), currentProjectName))
        if (!declarationFile.isFile) {
            return emptySet()
        }

        val declarationText = declarationFile.readText()
        val currentDeclarationNames = exportedDeclarationNames(declarationText)
        val declarationOwners = declarationOwners(currentProjectName, currentDeclarationNames)
        val dependencyImports =
            declarationOwners
                .entries
                .groupBy({ it.value }, { it.key })
                .toSortedMap(compareBy(::publicationProjectPriority).thenBy { it })
                .map { (dependencyProjectName, names) -> dependencyProjectName to names.sorted() }
        if (dependencyImports.isEmpty()) {
            return emptySet()
        }

        val externalizedDeclarationText = removeExportedDeclarations(declarationText, declarationOwners.keys)
        val importText =
            dependencyImports.joinToString(separator = "\n") { (dependencyProjectName, names) ->
                buildString {
                    append("import type {\n")
                    names.forEach { name -> append("  $name,\n") }
                    append("} from \"${packageName(dependencyProjectName)}\";")
                }
            }

        declarationFile.writeText("$importText\n\n${externalizedDeclarationText.trimStart()}")

        return dependencyImports.map { it.first }.toSet()
    }

    private fun declarationOwners(
        currentProjectName: String,
        currentDeclarationNames: Set<String>,
    ): Map<String, String> {
        val assignedNames = mutableSetOf<String>()
        return npmPublicationProjects
            .get()
            .filterNot { it == COMMONS_JS_PROJECT_NAME }
            .sortedWith(compareBy(::publicationProjectPriority).thenBy { it })
            .flatMap { projectName ->
                val names = projectExportedDeclarationNames(projectName) - assignedNames
                assignedNames.addAll(names)
                if (projectName == currentProjectName) {
                    emptyList()
                } else {
                    names
                        .filter { it in currentDeclarationNames }
                        .map { name -> name to projectName }
                }
            }.toMap()
    }

    private fun projectExportedDeclarationNames(projectName: String): Set<String> {
        val declarationFile =
            rootProjectDirectory
                .get()
                .asFile
                .resolve(projectName)
                .resolve("build/packages/js")
                .resolve(declarationFileName(rootProjectName.get(), projectName))
        require(declarationFile.isFile) {
            "Type declarations for $projectName were not found at ${declarationFile.absolutePath}."
        }

        return exportedDeclarationNames(declarationFile.readText()) - nonExternalizedDeclarationNames
    }

    private fun exportedDeclarationNames(declarationText: String): Set<String> =
        declarationText
            .lineSequence()
            .mapNotNull(::exportedDeclarationName)
            .toSet()

    private fun removeExportedDeclarations(
        declarationText: String,
        declarationNames: Set<String>,
    ): String {
        val lines = declarationText.lines()
        val retainedLines = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val declarationName = exportedDeclarationName(lines[index])
            if (declarationName != null && declarationName in declarationNames) {
                index = declarationEndIndex(lines, index) + 1
            } else {
                retainedLines += lines[index]
                index += 1
            }
        }

        return retainedLines.joinToString("\n")
    }

    private fun exportedDeclarationName(line: String): String? =
        declarationExportRegex.matchEntire(line)?.groupValues?.get(1)

    private fun declarationEndIndex(
        lines: List<String>,
        startIndex: Int,
    ): Int {
        var depth = 0
        var sawBrace = false
        for (index in startIndex until lines.size) {
            lines[index].forEach { character ->
                when (character) {
                    '{' -> {
                        sawBrace = true
                        depth += 1
                    }
                    '}' -> depth -= 1
                }
            }

            if (!sawBrace && lines[index].trimEnd().endsWith(";")) {
                return index
            }
            if (sawBrace && depth == 0) {
                return index
            }
        }

        return startIndex
    }

    private fun packageName(projectName: String) = "@attocash/$projectName"

    private fun publicationProjectPriority(projectName: String): Int =
        publicationProjectPriorities[projectName] ?: Int.MAX_VALUE

    private fun declarationFileName(
        rootProjectName: String,
        projectName: String,
    ) = "${moduleFileName(rootProjectName, projectName).removeSuffix(".mjs")}.d.mts"

    private fun moduleFileName(
        rootProjectName: String,
        projectName: String,
    ) = "$rootProjectName-$projectName.mjs"

    private companion object {
        const val COMMONS_CORE_PROJECT_NAME = "commons-core"
        const val COMMONS_TRANSPORT_PROJECT_NAME = "commons-transport"
        const val COMMONS_JS_PROJECT_NAME = "commons-js"
        const val KTOR_RUNTIME_MODULE_PREFIX = "ktor-"

        val declarationExportRegex =
            Regex(
                "^export declare (?:(?:abstract )?class|interface|function|const|namespace|type) " +
                    "([A-Za-z_$][A-Za-z0-9_$]*).*$",
            )

        val nonExternalizedDeclarationNames = setOf("KtMap", "initHook")

        val publicationProjectPriorities =
            mapOf(
                "commons-core" to 0,
                "commons-transport" to 5,
                "commons-node" to 10,
                "commons-node-remote" to 20,
                "commons-worker" to 30,
                "commons-worker-remote" to 40,
                "commons-worker-web" to 50,
                "commons-wallet" to 60,
                "commons-test" to 70,
                "commons-js" to 80,
            )

        val coreRuntimeModuleFileNames =
            setOf(
                "Kotlin-DateTime-library-kotlinx-datetime.mjs",
                "kotlin-kotlin-stdlib.mjs",
                "kotlin_org_jetbrains_kotlin_kotlin_dom_api_compat.mjs",
                "kotlinx-io-kotlinx-io-bytestring.mjs",
                "kotlinx-io-kotlinx-io-core.mjs",
                "kotlinx-serialization-kotlinx-serialization-core.mjs",
            )

        val transportRuntimeModuleFileNames =
            setOf(
                "kotlinx-atomicfu.mjs",
                "kotlinx-coroutines-core.mjs",
            )
    }
}

fun Project.moduleDescriptionProvider(): Property<String> {
    val moduleDescription = objects.property(String::class.java)
    val configuredDescription =
        requireNotNull(description?.takeIf { it.isNotBlank() && !it.startsWith("Publishes ") }) {
            "$path must set description before applying the npm publish plugin."
        }
    moduleDescription.set(configuredDescription)
    moduleDescription.finalizeValue()
    return moduleDescription
}

fun Project.configureCommonsNpmPackageExternalization() {
    val apiConfigurationNames = listOf("commonMainApi", "jsMainApi", "webMainApi")
    val packageDir = layout.buildDirectory.dir("packages/js")
    val canonicalPackageDir =
        rootProject.layout.buildDirectory.dir("js/packages/${rootProject.name}-commons-npm-bundle/kotlin")
    val externalizeCommonsJsPackageDependencies =
        tasks.register(
            "externalizeCommonsJsPackageDependencies",
            ExternalizeCommonsNpmPackageDependencies::class.java,
            Action { task ->
                task.group = "build"
                task.description = "Rewrites internal Commons JS module imports to npm package imports before packing."

                task.dependsOn("assembleJsPackage")
                task.dependsOn(":commons-npm-bundle:compileProductionLibraryKotlinJs")

                task.packageDirectory.set(packageDir)
                task.canonicalPackageDirectory.set(canonicalPackageDir)
                task.rootProjectDirectory.set(rootProject.layout.projectDirectory)
                task.currentProjectName.set(project.name)
                task.rootProjectName.set(rootProject.name)
                task.packageVersion.set(provider { project.version.toString() })
                task.npmPublicationProjects.set(
                    provider {
                        rootProject.subprojects
                            .filter { it.plugins.hasPlugin("org.jetbrains.kotlin.npm-publish") }
                            .map { it.name }
                            .sorted()
                    },
                )
                task.apiDependencyProjectNames.set(
                    provider {
                        apiConfigurationNames
                            .flatMap { configurationName ->
                                configurations
                                    .findByName(configurationName)
                                    ?.dependencies
                                    ?.filterIsInstance<ProjectDependency>()
                                    ?.map {
                                        rootProject.findProject(it.path)?.name
                                            ?: it.path.substringAfterLast(":")
                                    }
                                    .orEmpty()
                            }.distinct()
                            .sorted()
                    },
                )

                task.inputs.dir(packageDir)
                task.outputs.dir(packageDir)
                task.outputs.upToDateWhen { false }
                if (project.name != "commons-core" && project.name != "commons-js") {
                    task.dependsOn(
                        provider {
                            task.npmPublicationProjects
                                .get()
                                .filter { it != project.name }
                                .map { ":$it:assembleJsPackage" }
                        },
                    )
                }
            },
        )

    tasks
        .matching { it.name == "packJsPackage" || it.name.startsWith("publishJsPackage") }
        .configureEach(
            Action { task ->
                task.dependsOn(externalizeCommonsJsPackageDependencies)
                if (task.name == "packJsPackage") {
                    task.outputs.upToDateWhen { false }
                    task.doFirst {
                        project.delete(task.temporaryDir)
                        task.temporaryDir.mkdirs()
                    }
                }
            },
        )
}
