// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures

import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinSyncTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess
import org.jetbrains.plugins.gradle.service.project.open.createLinkSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

object LinkedProjectPathsTestsFeature : TestFeature<LinkedProjectPaths> {
    override fun createDefaultConfiguration(): LinkedProjectPaths = LinkedProjectPaths(mutableSetOf())

    override fun KotlinSyncTestsContext.beforeImport() {
        testConfiguration.getConfiguration(LinkedProjectPathsTestsFeature).linkedProjectPaths.forEach {
            GradleProjectsLinker.linkGradleProject(it, testProjectRoot, testProject)
        }
    }
}

class LinkedProjectPaths(val linkedProjectPaths: MutableSet<String>)

interface GradleProjectsLinkingDsl {
    fun TestConfigurationDslScope.linkProject(projectPath: String) {
        writeAccess.getConfiguration(LinkedProjectPathsTestsFeature).linkedProjectPaths.add(projectPath)
    }
}

object GradleProjectsLinker {
    fun linkGradleProject(relativeProjectPath: String, projectPath: File, project: Project) {
        val absoluteProjectPath = projectPath.resolve(relativeProjectPath).absolutePath
        val localFileSystem = LocalFileSystem.getInstance()
        val projectFile = localFileSystem.refreshAndFindFileByPath(absoluteProjectPath)
            ?: error("Failed to find projectFile: $absoluteProjectPath")

        val settings = createLinkSettings(projectFile.toNioPath(), project)

        ExternalSystemUtil.linkExternalProject(
            /* externalSystemId = */ GradleConstants.SYSTEM_ID,
            /* projectSettings = */ settings,
            /* project = */ project,
            /* importResultCallback = */ null,
            /* isPreviewMode = */ false,
            /* progressExecutionMode = */ ProgressExecutionMode.MODAL_SYNC
        )
    }
}
