package io.skobinsky.manifest

import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.work.InputChanges
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.io.File
import javax.inject.Inject

abstract class EmbedJPackageTask @Inject constructor(
    targetFormat: TargetFormat,
    enabledProvider: Provider<Boolean>,
    manifestFileProvider: Provider<File>,
    exeDirectoryProvider: Provider<Directory>,
    mtExecutableProvider: Provider<File>,
) : AbstractJPackageTask(targetFormat) {

    init {
        group = "compose desktop"
        description = "Embeds a manifest file in the app exe"
    }

    override fun isEnabled(): Boolean = enabled.get()

    // -----------------------------
    // Inputs
    // -----------------------------

    @get:Input
    val enabled: Provider<Boolean> = enabledProvider

    @get:InputFile
    val manifestFile: Provider<File> = manifestFileProvider

    @get:InputDirectory
    val exeDirectory: Provider<Directory> = exeDirectoryProvider

    @get:InputFile
    val mtExecutable: Provider<File> = mtExecutableProvider

    // -----------------------------
    // Outputs
    // -----------------------------

    @get:Optional
    @get:OutputFile
    val outputExeFile: File?
        get() = exeDirectory.get()
            .asFile
            .walk()
            .maxDepth(2)
            .firstOrNull { it.extension.equals("exe", true) }

    @get:Optional
    @get:OutputFile
    val outputManifestFile: File?
        get() = outputExeFile?.resolveSibling("${outputExeFile!!.name}.manifest")

    // -----------------------------
    // Compose task override
    // -----------------------------

    override fun prepareWorkingDir(inputChanges: InputChanges) {
        super.prepareWorkingDir(inputChanges)

        val resourcesDir = jpackageResources.ioFile
        resourcesDir.mkdirs()

        val targetManifest = resourcesDir.resolve("app.manifest")

        manifestFile.get().copyTo(targetManifest, overwrite = true)

        logger.info("Copied manifest to jpackage resources: $targetManifest")
    }

    // -----------------------------
    // Helpers
    // -----------------------------

    private fun File.temporaryWritable(function: (File) -> Unit) {
        val previous = canWrite()
        setWritable(true)
        try {
            function(this)
        } finally {
            setWritable(previous)
        }
    }

    private fun embedManifestIn(exe: File) {

        val process = ProcessBuilder(
            mtExecutable.get().absolutePath,
            "-nologo",
            "-manifest",
            manifestFile.get().absolutePath,
            "-outputresource:${exe.absolutePath};#1"
        )
            .directory(exe.parentFile)
            .redirectErrorStream(true)
            .start()

        process.inputStream.bufferedReader().forEachLine {
            logger.info(it)
        }

        val exit = process.waitFor()

        if (exit != 0) {
            throw RuntimeException("mt.exe failed with exit code $exit")
        }
    }

    private val <T : FileSystemLocation> Provider<T>.ioFile: File
        get() = get().asFile
}