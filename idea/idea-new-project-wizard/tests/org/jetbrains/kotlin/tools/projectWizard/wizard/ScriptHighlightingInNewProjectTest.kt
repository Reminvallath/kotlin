/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.importing.ImportSpec
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.ExternalSystemImportingTestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.TestImportSpecBuilder
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.utils.getKtFile
import org.jetbrains.kotlin.idea.framework.GRADLE_SYSTEM_ID
import org.jetbrains.kotlin.idea.scripting.gradle.getGradleProjectSettings
import org.jetbrains.kotlin.tools.projectWizard.cli.BuildSystem
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class ScriptHighlightingInNewProjectTest : AbstractProjectTemplateNewWizardProjectImportTest() {

    @Test
    fun testScriptHighlightingGradleWrapped() {
        doTest(DistributionType.WRAPPED)
    }

    @Test
    fun testScriptHighlightingGradleDefaultWrapped() {
        doTest(DistributionType.DEFAULT_WRAPPED)
    }

    @Test
    fun testScriptHighlightingGradleBundled() {
        doTest(DistributionType.BUNDLED)
    }

    private fun doTest(distributionType: DistributionType) {
        val directory = Paths.get("backendApplication")
        val tempDirectory = Files.createTempDirectory(null)

        prepareGradleBuildSystem(tempDirectory, distributionType)

        runWizard(directory, BuildSystem.GRADLE_KOTLIN_DSL, tempDirectory)

        importGradleProject()
        checkConfigurations()
    }

    private fun checkConfigurations() {
        val settings = getGradleProjectSettings(project).firstOrNull() ?: error("Cannot find linked gradle project")
        val scripts = File(settings.externalProjectPath).walkTopDown().filter {
            it.name.endsWith("gradle.kts")
        }
        scripts.forEach {
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(it)!!
            val psiFile = project.getKtFile(virtualFile) ?: error("Cannot find KtFile for $it")
            ExternalSystemImportingTestCase.assertTrue(
                "Configuration for ${it.path} is missing",
                project.service<ScriptConfigurationManager>().hasConfiguration(psiFile)
            )
        }
    }

    private fun importGradleProject() {
        var error: Pair<String, String>? = null
        val importSpec: ImportSpec = TestImportSpecBuilder(ImportSpecBuilder(project, GRADLE_SYSTEM_ID).build())
            .use(ProgressExecutionMode.MODAL_SYNC)
            .forceWhenUptodate()
            .withArguments("--stacktrace")
            .callback(object : ExternalProjectRefreshCallback {
                override fun onSuccess(externalProject: DataNode<ProjectData>?) {
                    if (externalProject == null) {
                        System.err.println("Got null External project after import")
                        return
                    }

                    service<ProjectDataManager>().importData(externalProject, project, true)
                    println("External project was successfully imported")
                }

                override fun onFailure(errorMessage: String, errorDetails: String?) {
                    error = errorMessage to (errorDetails ?: "")
                }
            }).build()

        val notificationManager = service<ExternalSystemProgressNotificationManager>()
        val listener: ExternalSystemTaskNotificationListenerAdapter = object : ExternalSystemTaskNotificationListenerAdapter() {
            override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
                if (StringUtil.isEmptyOrSpaces(text)) return
                (if (stdOut) System.out else System.err).print(text)
            }
        }
        notificationManager.addNotificationListener(listener)

        try {
            ExternalSystemUtil.refreshProjects(importSpec)
        } finally {
            notificationManager.removeNotificationListener(listener)
        }

        if (error != null) {
            var failureMsg = "Import failed: ${error?.first}"
            if (error?.second?.isNotBlank() == false) {
                failureMsg += "\nError details: \n${error!!.second}"
            }
            TestCase.fail(failureMsg)
        }
    }

}