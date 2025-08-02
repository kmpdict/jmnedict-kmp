package com.boswelja.jmnedict.generator

import com.squareup.zstd.okio.zstdCompress
import okio.buffer
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Properties
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

abstract class DownloadJmneDictTask : DefaultTask() {

    /**
     * The URL for the full JMNEDict archive.
     */
    @get:Input
    abstract val jmneDictUrl: Property<URI>

    /**
     * The file to store the output jmnedict.
     */
    @get:OutputFile
    abstract val outputJmneDict: RegularFileProperty

    @get:OutputFile
    abstract val outputDtd: RegularFileProperty

    @get:OutputFile
    abstract val outputReleaseNotes: RegularFileProperty

    @get:OutputFile
    abstract val outputMetadata: RegularFileProperty

    @OptIn(ExperimentalTime::class)
    @TaskAction
    fun downloadAndUnpackJmDict() {
        val outputJmneDictFile = outputJmneDict.get().asFile
        // If the jmdict file exists and is not older than 24 hours, return
        if (outputJmneDictFile.exists()) {
            val lastModifiedInstant = Instant.fromEpochMilliseconds(outputJmneDictFile.lastModified())
            if (Clock.System.now() - lastModifiedInstant < 23.hours) {
                return
            }
        }

        val jmneDictStream = outputJmneDict.get().asFile.sink().zstdCompress().buffer()
        val releaseNotesOutputStream = outputReleaseNotes.get().asFile.outputStream().writer()
        val dtdOutputStream = outputDtd.get().asFile.outputStream().writer()

        val urlConnection = jmneDictUrl.get().toURL().openConnection()
        val inStream = GZIPInputStream(urlConnection.inputStream).bufferedReader()

        // Metadata fields
        var entryCount = 0

        try {
            // Download jmdict and write to separate files
            var finishedRelNotes = false
            var finishedDtd = false
            var line = inStream.readLine()
            while (line != null) {
                if (!finishedRelNotes) {
                    if (line.startsWith("<!DOCTYPE")) {
                        finishedRelNotes = true
                        dtdOutputStream.appendLine(line)
                    } else {
                        releaseNotesOutputStream.appendLine(line)
                    }
                } else if (!finishedDtd) {
                    dtdOutputStream.appendLine(line)
                    if (line.startsWith("]>")) {
                        finishedDtd = true
                    }
                } else {
                    if (line.startsWith("<entry>")) entryCount++
                    jmneDictStream.writeUtf8(line)
                    jmneDictStream.writeUtf8("\n")
                }
                line = inStream.readLine()
            }

            // Write captured metadata
            val props = Properties()
            props.setProperty("entryCount", entryCount.toString())
            props.setProperty("timeUtc", OffsetDateTime.now(ZoneId.of("UTC")).toString())
            outputMetadata.get().asFile.outputStream().use {
                props.store(it, null)
            }
        } finally {
            jmneDictStream.close()
            releaseNotesOutputStream.close()
            dtdOutputStream.close()
            inStream.close()
        }
    }
}
