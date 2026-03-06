package io.skobinsky.manifest

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.compose.desktop.application.tasks.AbstractRunDistributableTask

// An easy and informative way to debug the tasks and why they are up to date or not is to add
// --info to the gradle command so that it shows the info logs (such as why a task was skipped)

@Suppress("unused")
abstract class EmbedPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val embedExtension = project.extensions.create(
            "composeExeManifest",
            EmbedExtension::class.java
        )

        val prepareMtTask = project.tasks.register(
            "prepareMtExeFile",
            PrepareMtTask::class.java
        )

        project.tasks
            .withType(AbstractJPackageTask::class.java)
            .configureEach { composePackagingTask ->
                if (!embedExtension.enabled.get()) return@configureEach

                val embedTask = project.tasks.register(
                    "${composePackagingTask.name}WithAppManifest",
                    EmbedJPackageTask::class.java,
                    composePackagingTask.targetFormat,
                    embedExtension.enabled,
                    embedExtension.manifestFile,
                    composePackagingTask.destinationDir,
                    prepareMtTask.flatMap { it.mtExeFile }
                )
                embedTask.configure { it.dependsOn(composePackagingTask) }
            }


        project.tasks
            .withType(AbstractRunDistributableTask::class.java)
            .configureEach {
                it.mustRunAfter(project.tasks.withType(EmbedJPackageTask::class.java))
            }
    }
}