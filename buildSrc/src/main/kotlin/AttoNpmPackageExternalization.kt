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

        if (internalDependencyProjectNames.isNotEmpty()) {
            addInternalDependencies(packageJsonFile, internalDependencyProjectNames)
        }
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

    private fun packageName(projectName: String) = "@attocash/$projectName"

    private fun moduleFileName(
        rootProjectName: String,
        projectName: String,
    ) = "$rootProjectName-$projectName.mjs"
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
    val externalizeCommonsJsPackageDependencies =
        tasks.register(
            "externalizeCommonsJsPackageDependencies",
            ExternalizeCommonsNpmPackageDependencies::class.java,
            Action { task ->
                task.group = "build"
                task.description = "Rewrites internal Commons JS module imports to npm package imports before packing."

                task.dependsOn("assembleJsPackage")

                task.packageDirectory.set(packageDir)
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
            },
        )

    tasks
        .matching { it.name == "packJsPackage" || it.name.startsWith("publishJsPackage") }
        .configureEach(
            Action { task ->
                task.dependsOn(externalizeCommonsJsPackageDependencies)
                if (task.name == "packJsPackage") {
                    task.outputs.upToDateWhen { false }
                }
            },
        )
}
