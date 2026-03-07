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
        logger.debug("jpackageResources after prep: \n${jpackageResources.ioFile.list().joinToString("\n")}")
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
        var scriptDir = fso.GetParentFolderName(WScript.ScriptFullName);
        var imageDir  = fso.GetParentFolderName(scriptDir);
        var exePath = imageDir + "\\images\\win-msi.image\\$appName\\$appName.exe";
        var manifestPath = "$manifestPath";
        
        if (fso.FileExists(exePath)) {
            var exeFile = fso.GetFile(exePath);
            exeFile.Attributes &= ~1; // remove read-only attribute
        } else {
            WScript.Quit(11);
        }

        if (!fso.FileExists(manifestPath)) {
            WScript.Quit(12);
        }

        var cmd = "\"$mtPath\" -manifest \"" + manifestPath +
                  "\" -outputresource:\"" + exePath + "\";#1";

        var result = shell.Run(cmd, 1, true);
        if (result != 0) {
            WScript.Quit(result);
        }
            </script>
          </job>
        </package>
    """.trimIndent()
}

private fun File.normalizedPath() = absolutePath
    .replace("\\", "\\\\")