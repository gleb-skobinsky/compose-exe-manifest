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

        project.plugins.withId("org.jetbrains.compose") {

            project.tasks.withType(AbstractJPackageTask::class.java).whenTaskAdded { packageTask ->
                if (
                    packageTask is EmbedJPackageTask ||
                    !embedExtension.enabled.get() ||
                    !packageTask.targetFormat.isCompatibleWithCurrentOS
                ) {
                    return@whenTaskAdded
                }

                val embedTaskName = "${packageTask.name}WithAppManifest"

                if (project.tasks.findByName(embedTaskName) != null) return@whenTaskAdded

                val embedTask = project.tasks.register(
                    embedTaskName,
                    EmbedJPackageTask::class.java,
                    packageTask.targetFormat,
                )

                embedTask.configure {
                    it.manifestFile.set(embedExtension.manifestFile)
                    it.mtExecutable.set(prepareMtTask.flatMap { mtTask -> mtTask.mtExeFile })

                    it.populateFieldsFromBaseTask(packageTask)

                    val deps = packageTask.taskDependencies
                        .getDependencies(packageTask)
                    it.dependsOn(deps)
                    it.mustRunAfter(deps)
                }
            }

            project.tasks.withType(AbstractRunDistributableTask::class.java)
                .configureEach { runTask ->
                    runTask.mustRunAfter(project.tasks.withType(EmbedJPackageTask::class.java))
                }
        }
    }

    private fun EmbedJPackageTask.populateFieldsFromBaseTask(
        baseTask: AbstractJPackageTask,
    ) {
        files.setFrom(baseTask.files)
        mangleJarFilesNames.set(baseTask.mangleJarFilesNames)
        packageFromUberJar.set(baseTask.packageFromUberJar)
        wixToolsetDir.set(baseTask.wixToolsetDir)
        installationPath.set(baseTask.installationPath)
        licenseFile.set(baseTask.licenseFile)
        iconFile.set(baseTask.iconFile)
        launcherMainClass.set(baseTask.launcherMainClass)
        launcherMainJar.set(baseTask.launcherMainJar)
        launcherArgs.set(baseTask.launcherArgs)
        launcherJvmArgs.set(baseTask.launcherJvmArgs)
        packageName.set(baseTask.packageName)
        packageDescription.set(baseTask.packageDescription)
        packageCopyright.set(baseTask.packageCopyright)
        packageVendor.set(baseTask.packageVendor)
        packageVersion.set(baseTask.packageVersion)
        linuxShortcut.set(baseTask.linuxShortcut)
        linuxPackageName.set(baseTask.linuxPackageName)
        linuxAppRelease.set(baseTask.linuxAppRelease)
        linuxAppCategory.set(baseTask.linuxAppCategory)
        linuxDebMaintainer.set(baseTask.linuxDebMaintainer)
        linuxMenuGroup.set(baseTask.linuxMenuGroup)
        linuxRpmLicenseType.set(baseTask.linuxRpmLicenseType)
        macPackageName.set(baseTask.macPackageName)
        macDockName.set(baseTask.macDockName)
        macAppStore.set(baseTask.macAppStore)
        macAppCategory.set(baseTask.macAppCategory)
        macMinimumSystemVersion.set(baseTask.macMinimumSystemVersion)
        macEntitlementsFile.set(baseTask.macEntitlementsFile)
        macRuntimeEntitlementsFile.set(baseTask.macRuntimeEntitlementsFile)
        packageBuildVersion.set(baseTask.packageBuildVersion)
        macProvisioningProfile.set(baseTask.macProvisioningProfile)
        macRuntimeProvisioningProfile.set(baseTask.macRuntimeProvisioningProfile)
        winConsole.set(baseTask.winConsole)
        winDirChooser.set(baseTask.winDirChooser)
        winPerUserInstall.set(baseTask.winPerUserInstall)
        winShortcut.set(baseTask.winShortcut)
        winMenu.set(baseTask.winMenu)
        winMenuGroup.set(baseTask.winMenuGroup)
        winUpgradeUuid.set(baseTask.winUpgradeUuid)
        runtimeImage.set(baseTask.runtimeImage)
        appImage.set(baseTask.appImage)
        javaRuntimePropertiesFile.set(baseTask.javaRuntimePropertiesFile)
        appResourcesDir.set(baseTask.appResourcesDir)
        destinationDir.set(baseTask.destinationDir)
    }
}