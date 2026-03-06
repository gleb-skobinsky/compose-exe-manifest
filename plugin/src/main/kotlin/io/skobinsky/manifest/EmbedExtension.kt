package io.skobinsky.manifest

import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import java.io.File
import javax.inject.Inject
import org.gradle.api.provider.Property

abstract class EmbedExtension @Inject constructor(project: Project) {
    /**
     * Whether the embedding/copying is enabled.
     *
     * Defaults to `true`.
     */
    val enabled: Property<Boolean> = project.objects.property(Boolean::class.java).value(true)

    /**
     * The manifest file to embed in app exe.
     * Its content is not validated by the plugin.
     *
     * Defaults to `app.manifest` at the project/module directory.
     */
    val manifestFile: RegularFileProperty = project.objects.fileProperty().value { File("app.manifest") }
}