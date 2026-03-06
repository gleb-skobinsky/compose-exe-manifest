package io.skobinsky.manifest

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction
import java.io.File

// The class is made abstract so that Gradle will handle many things automatically.
abstract class EmbedTask : DefaultTask() {

    init {
        group = "compose desktop"
        description = "Embeds a manifest file in the app exe"
    }

    @get:InputFile
    abstract val manifestFile: RegularFileProperty

    @get:InputFile
    abstract val mtExecutable: RegularFileProperty

    @get:InputDirectory
    abstract val exeDirectory: DirectoryProperty

    /**
     * The exe file that will be modified.
     * Optional because non-Windows builds won't produce one.
     */
    @get:Optional
    @get:InputFile
    abstract val inputExeFile: RegularFileProperty

    /**
     * Output exe after manifest embedding.
     * Usually identical to inputExeFile but Gradle needs explicit output.
     */
    @get:Optional
    @get:OutputFile
    abstract val outputExeFile: RegularFileProperty

    @TaskAction
    fun embedManifest() {

        val exe = resolveExeFile() ?: throw StopExecutionException("No .exe file found")

        val mt = mtExecutable.get().asFile
        val manifest = manifestFile.get().asFile

        exe.setWritable(true)

        val exit = try {
            val process = ProcessBuilder(
                mt.absolutePath,
                "-nologo",
                "-manifest", manifest.absolutePath,
                "-outputresource:${exe.absolutePath};#1"
            )
                .directory(exe.parentFile)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().forEachLine {
                logger.lifecycle(it)
            }

            process.waitFor()
        } finally {
            exe.setWritable(false)
        }

        if (exit != 0) {
            throw GradleException("mt.exe failed with exit code $exit")
        }

        logger.lifecycle("Manifest embedded into ${exe.name}")
    }

    /**
     * Resolve exe lazily at execution time.
     */
    private fun resolveExeFile(): File? {

        inputExeFile.orNull?.asFile?.let { return it }

        val dir = exeDirectory.get().asFile

        return dir.walk()
            .maxDepth(3)
            .firstOrNull { it.extension.equals("exe", true) }
    }
}