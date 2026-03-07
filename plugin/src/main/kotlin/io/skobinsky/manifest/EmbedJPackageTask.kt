package io.skobinsky.manifest

import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
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
) : AbstractJPackageTask(targetFormat) {

    init {
        group = "compose desktop"
        description = "Embeds a manifest file in the app exe"
    }

    // -----------------------------
    // Inputs
    // -----------------------------

    @get:InputFile
    abstract val manifestFile: RegularFileProperty

    @get:InputFile
    abstract val mtExecutable: RegularFileProperty

    // -----------------------------
    // Outputs
    // -----------------------------

    @get:Optional
    @get:OutputFile
    val outputExeFile: File?
        get() = destinationDir.ioFile
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
        val appName = packageName.get()
        val wsfScript = getWsfScript(
            appName = appName,
            manifestFile = manifestFile.ioFile,
            mtExecutable = mtExecutable.ioFile,
        )
        val resourcesDir = jpackageResources.ioFile
        resourcesDir.mkdirs()
        val wsfScriptFile = resourcesDir.resolve("$appName-post-image.wsf")
        wsfScriptFile.writeText(wsfScript)
        println("jpackageResources after prep: \n${jpackageResources.ioFile.list().joinToString("\n")}")
    }

    // -----------------------------
    // Helper
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
            mtExecutable.ioFile.absolutePath,
            "-nologo",
            "-manifest",
            manifestFile.get().asFile.absolutePath,
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

private fun getWsfScript(
    appName: String,
    mtExecutable: File,
    manifestFile: File,
): String {
    val manifestPath = manifestFile.normalizedPath()
    val mtPath = mtExecutable.normalizedPath()

    return """
        <package>
          <job id="injectManifest">
            <script language="JScript">

        var shell = new ActiveXObject("WScript.Shell");
        var fso   = new ActiveXObject("Scripting.FileSystemObject");
        
        var logStream = fso.OpenTextFile("C:\\temp\\jpackage-postimage.log", 8, true);

        function log(msg) {
            logStream.WriteLine("[post-image] " + msg);
        }
        
        function dumpDir(path, indent) {
            try {
                var folder = fso.GetFolder(path);

                var files = new Enumerator(folder.Files);
                for (; !files.atEnd(); files.moveNext()) {
                    var file = files.item();
                    log(indent + "[FILE] " + file.Path);
                }

                var dirs = new Enumerator(folder.SubFolders);
                for (; !dirs.atEnd(); dirs.moveNext()) {
                    var sub = dirs.item();
                    log(indent + "[DIR ] " + sub.Path);
                    dumpDir(sub.Path, indent + "  ");
                }

            } catch (e) {
                log("Directory dump error at " + path + ": " + e.message);
            }
        }

        log("Script started");

        var scriptDir = fso.GetParentFolderName(WScript.ScriptFullName);
        var imageDir  = fso.GetParentFolderName(scriptDir);

        var exePath = imageDir + "\\images\\win-msi.image\\$appName\\$appName.exe";
        log("Expected launcher path = " + exePath);

        var manifestPath = "$manifestPath";
        log("Expected manifest path = " + manifestPath);

        if (!fso.FileExists(exePath)) {
            log("ERROR: launcher exe not found");
            WScript.Quit(11);
        }

        if (!fso.FileExists(manifestPath)) {
            log("ERROR: manifest not found");
            WScript.Quit(12);
        }

        log("Files located successfully");

        var cmd = "\"$mtPath\" -manifest \"" + manifestPath +
                  "\" -outputresource:\"" + exePath + "\";#1";

        log("Running command:");
        log(cmd);

        var result = shell.Run(cmd, 1, true);

        log("mt.exe exit code = " + result);

        if (result != 0) {
            log("ERROR: manifest injection failed");
            WScript.Quit(result);
        }

        log("Manifest successfully injected");
        logStream.Flush();

            </script>
          </job>
        </package>
    """.trimIndent()
}

private fun File.normalizedPath() = absolutePath
    .replace("\\", "\\\\")