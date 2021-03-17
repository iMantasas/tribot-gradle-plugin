package org.tribot.wastedbro.gradle.plugin

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import java.io.File

class TribotPlugin : Plugin<Project> {
    companion object {
        val lock = Object()
    }

    override fun apply(project: Project) {
        project.pluginManager.apply("java")

        project.tasks.create("deleteBin") { task ->
            task.group = "tribot"
            task.doLast {
                getTribotDirectory()?.resolve("bin")?.resolve("scripts")
                    ?.takeIf { it.exists() }
                    ?.deleteRecursively()
            }
        }

        project.tasks.create("repoPackage") { task ->
            task.group = "tribot"

            task.onlyIf {
                project.tasks
                    .filter { it.name == "compileJava" || it.name == "compileKotlin" }
                    .any { it.didWork }
            }

            task.doLast {

                val projectDir = project.projectDir
                val dirsToPackage = mutableListOf(projectDir.resolve("src"))

                fun getDependenciesRecursive(config: Configuration?, dirs: MutableList<File>): Unit? =
                    config?.dependencies
                        ?.mapNotNull { it as? DefaultProjectDependency }
                        ?.forEach { d ->
                            dirs += d.dependencyProject.projectDir.resolve("src")
                            getDependenciesRecursive(d.dependencyProject.configurations.asMap["implementation"], dirs)
                        }

                getDependenciesRecursive(project.configurations.asMap["implementation"], dirsToPackage)

                val zipFile = project.buildDir
                    .resolve("repo-deploy")
                    .also { it.mkdirs() }
                    .resolve("${project.name}.zip")

                if (zipFile.exists())
                    zipFile.delete()

                ZipFile(zipFile).also { zip ->
                    dirsToPackage.filter { it.exists() }.distinctBy { it.canonicalPath }.forEach { srcDir ->
                        srcDir.listFiles()?.forEach {
                            if (it.isFile)
                                zip.addFile(it, ZipParameters().apply { isOverrideExistingFilesInZip = false })
                            else
                                zip.addFolder(it, ZipParameters().apply { isOverrideExistingFilesInZip = false })
                        }
                    }
                }
            }
        }

        project.tasks.create("copyToBin") { task ->
            task.group = "tribot"

            task.onlyIf {
                project.tasks
                    .filter { it.name == "compileJava" || it.name == "compileKotlin" }
                    .any { it.didWork }
            }

            task.doLast {

                val projectDir = project.projectDir
                val dirsToPackage = mutableListOf(projectDir.resolve("build/classes/java/main"),
                        projectDir.resolve("build/classes/kotlin/main"))

                fun getDependenciesRecursive(config: Configuration?, dirs: MutableList<File>): Unit? =
                        config?.dependencies
                                ?.mapNotNull { it as? DefaultProjectDependency }
                                ?.forEach { d ->
                                    dirs += d.dependencyProject.projectDir.resolve("build/classes/java/main")
                                    dirs += d.dependencyProject.projectDir.resolve("build/classes/kotlin/main")
                                    getDependenciesRecursive(d.dependencyProject.configurations.asMap["implementation"], dirs)
                                }

                getDependenciesRecursive(project.configurations.asMap["implementation"], dirsToPackage)

                val zipFile = getTribotDirectory()
                        ?.resolve("bin/scripts")
                        ?.also { it.mkdirs() }
                        ?.resolve("${project.name}.tribot") ?: return@doLast

                if (zipFile.exists())
                    zipFile.delete()

                ZipFile(zipFile).also {  zip ->
                    dirsToPackage.filter { it.exists() }.distinctBy { it.canonicalPath }.forEach { srcDir ->
                        srcDir.listFiles()?.forEach {
                            if (it.isFile)
                                zip.addFile(it, ZipParameters().apply { isOverrideExistingFilesInZip = false })
                            else
                                zip.addFolder(it, ZipParameters().apply { isOverrideExistingFilesInZip = false })
                        }
                    }
                }
            }
        }

        project.tasks.create("copyClassesToBin") { task ->
            task.group = "tribot"

            task.onlyIf {
                project.tasks
                    .filter { it.name == "compileJava" || it.name == "compileKotlin" }
                    .any { it.didWork }
            }

            task.doLast {

                val projectDir = project.projectDir
                val dirsToPackage = mutableListOf(
                    projectDir.resolve("build/classes/java/main"),
                    projectDir.resolve("build/classes/kotlin/main")
                )

                fun getDependenciesRecursive(config: Configuration?, dirs: MutableList<File>): Unit? =
                    config?.dependencies
                        ?.mapNotNull { it as? DefaultProjectDependency }
                        ?.forEach { d ->
                            dirs += d.dependencyProject.projectDir.resolve("build/classes/java/main")
                            dirs += d.dependencyProject.projectDir.resolve("build/classes/kotlin/main")
                            getDependenciesRecursive(d.dependencyProject.configurations.asMap["implementation"], dirs)
                        }

                getDependenciesRecursive(project.configurations.asMap["implementation"], dirsToPackage)

                getTribotDirectory()
                    ?.resolve("bin")
                    ?.also { tribotBinDir ->
                        tribotBinDir.mkdirs()

                        dirsToPackage.filter { it.exists() }.distinctBy { it.canonicalPath }.forEach {
                            synchronized(lock) {
                                it.copyRecursively(
                                    tribotBinDir,
                                    overwrite = true
                                )
                            }
                        }
                    }
            }
        }
    }
}
