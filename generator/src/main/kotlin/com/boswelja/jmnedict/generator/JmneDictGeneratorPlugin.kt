package com.boswelja.jmnedict.generator

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.jvm.tasks.Jar
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI
import kotlin.reflect.KProperty0

internal const val ExtensionName: String = "jmneDict"

interface JmneDictExtension {

    /**
     * The URL for the full JMneDict archive. Defaults to `ftp://ftp.edrdg.org/pub/Nihongo/JMdict.gz`.
     */
    val jmneDictUrl: Property<URI>

    /**
     * The package name for the generated sources.
     */
    val packageName: Property<String>

    /**
     * Whether additional metadata, such as entry count, date information, and changelog should be
     * captured. When set to `true`, a `data class Metadata` is generated alongside jmdict content.
     * Defaults to `true`.
     */
    val generateMetadata: Property<Boolean>
}

class JmneDictGeneratorPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Create the Gradle extension for configuration
        val config = target.extensions.create(
            ExtensionName,
            JmneDictExtension::class.java
        )
        config.generateMetadata.convention(true)
        config.jmneDictUrl.convention(URI("ftp://ftp.edrdg.org/pub/Nihongo/JMnedict.xml.gz"))

        val targetGeneratedSourcesDir = target.layout.buildDirectory.dir("generated/jmnedict/kotlin")
        val targetJmneDictResDir = target.layout.buildDirectory.dir("generated/jmnedict/composeResources/")
        val jmneDictFile = target.layout.buildDirectory.file("resources/jmnedict/jmnedict.xml")
        val relNotesFile = target.layout.buildDirectory.file("resources/jmnedict/changelog.xml")
        val dtdFile = target.layout.buildDirectory.file("resources/jmnedict/dtd.xml")
        val metadataFile = target.layout.buildDirectory.file("resources/jmnedict/metadata.properties")

        // Register the download task
        val downloadJmneDictTask = target.tasks.register(
            "downloadJmneDict",
            DownloadJmneDictTask::class.java
        ) {
            requireProperty(config::jmneDictUrl, "ftp://ftp.edrdg.org/pub/Nihongo/JMnedict.xml.gz")

            it.jmneDictUrl.set(config.jmneDictUrl)
            it.outputJmneDict.set(jmneDictFile)
            it.outputDtd.set(dtdFile)
            it.outputReleaseNotes.set(relNotesFile)
            it.outputMetadata.set(metadataFile)
        }

        // Register the generation tasks
        val generateDataClassTask = target.tasks.register(
            "generateJmneDictDataClasses",
            GenerateDataClassesTask::class.java
        ) {
            requireProperty(config::packageName, "\"com.my.package\"")

            it.dependsOn(downloadJmneDictTask)

            it.outputDirectory.set(targetGeneratedSourcesDir)
            it.packageName.set(config.packageName)
            it.dtdFile.set(downloadJmneDictTask.get().outputDtd)
        }
        val generateMetadataTask = target.tasks.register(
            "generateJmneDictMetadataObject",
            GenerateMetadataObjectTask::class.java
        ) {
            requireProperty(config::packageName, "\"com.my.package\"")

            it.dependsOn(downloadJmneDictTask)

            it.outputDirectory.set(targetGeneratedSourcesDir)
            it.packageName.set(config.packageName)
            it.metadataFile.set(downloadJmneDictTask.get().outputMetadata)
        }

        // Configure Compose resources
        target.extensions.findByType(ComposeExtension::class.java)?.extensions?.findByType(ResourcesExtension::class.java)?.apply {
            val copyResourcesTask = target.tasks.register(
                "copyJmneDictResource",
                CopyComposeResourcesTask::class.java
            ) {
                it.dependsOn(downloadJmneDictTask)
                it.jmneDictFile.set(jmneDictFile)
                it.outputDirectory.set(targetJmneDictResDir)
            }
            customDirectory(
                sourceSetName = "commonMain",
                directoryProvider = copyResourcesTask.map { it.outputDirectory.get() }
            )
        }

        // Configure KMP projects
        target.extensions.findByType(KotlinMultiplatformExtension::class.java)?.apply {
            // Add generation task as a dependency for build tasks
            target.tasks.withType(KotlinCompile::class.java).configureEach {
                if (config.generateMetadata.get()) {
                    it.dependsOn(generateMetadataTask)
                }
                it.dependsOn(generateDataClassTask)
            }

            // Add generation task as a dependency for source jar tasks
            target.tasks.withType(Jar::class.java).configureEach {
                if (it.archiveClassifier.get() == "sources") {
                    if (config.generateMetadata.get()) {
                        it.dependsOn(generateMetadataTask)
                    }
                    it.dependsOn(generateDataClassTask)
                }
            }

            // Add the generated source dir to the common source set
            sourceSets.commonMain.configure {
                it.kotlin.srcDir(targetGeneratedSourcesDir)
            }
        }
    }
}

private fun requireProperty(property: KProperty0<Property<*>>, exampleValue: String) {
    require(property.get().isPresent) {
        """$ExtensionName.${property.name} must be specified.
               |$ExtensionName {
               |    ${property.name} = $exampleValue
               |}""".trimMargin()
    }
}
