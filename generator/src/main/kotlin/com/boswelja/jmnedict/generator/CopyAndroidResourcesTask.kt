package com.boswelja.jmnedict.generator

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class CopyAndroidResourcesTask : DefaultTask() {

    @get:InputFile
    abstract val jmneDictFile: RegularFileProperty

    /**
     * The directory to store generated source files in.
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copyJmDictToResources() {
        project.copy(Action { t ->
            t.from(jmneDictFile)
            t.into(outputDirectory.dir("raw/"))
        })
    }
}
