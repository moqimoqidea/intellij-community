// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.synthetic

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.configuration.ExperimentalFeatures
import org.jetbrains.kotlin.j2k.AbstractJavaToKotlinCopyPasteConversionTest
import org.jetbrains.kotlin.j2k.copyPaste.ConvertJavaCopyPasteProcessor
import org.jetbrains.kotlin.idea.testFramework.Stats
import org.jetbrains.kotlin.idea.testFramework.Stats.Companion.WARM_UP
import org.jetbrains.kotlin.idea.testFramework.performanceTest
import org.jetbrains.kotlin.idea.performance.tests.utils.commitAllDocuments
import org.jetbrains.kotlin.idea.performance.tests.utils.dispatchAllInvocationEvents
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.util.slashedPath
import java.io.File

abstract class AbstractPerformanceJavaToKotlinCopyPasteConversionTest(private val newJ2K: Boolean = false) :
    AbstractJavaToKotlinCopyPasteConversionTest() {

    companion object {
        @JvmStatic
        val warmedUp: Array<Boolean> = arrayOf(false, false)

        val stats: Array<Stats> = arrayOf(Stats("old j2k"), Stats("new j2k"))
    }

    override fun setUp() {
        super.setUp()

        ExperimentalFeatures.NewJ2k.isEnabled = newJ2K
        val index = j2kIndex()

        if (!warmedUp[index]) {
            doWarmUpPerfTest()
            warmedUp[index] = true
        }
    }

    override fun tearDown() {
        RunAll(
            ThrowableRunnable { super.tearDown() }
        ).run()
    }

    private fun doWarmUpPerfTest() {
        performanceTest<Unit, Unit> {
            name(WARM_UP)
            stats(stats())
            setUp {
                with(myFixture) {
                    configureByText(JavaFileType.INSTANCE, "<selection>public class Foo {\nprivate String value;\n}</selection>")
                    performEditorAction(IdeActions.ACTION_CUT)
                    configureByText(KotlinFileType.INSTANCE, "<caret>")
                }
                ConvertJavaCopyPasteProcessor.Util.conversionPerformed = false
            }
            test {
                myFixture.performEditorAction(IdeActions.ACTION_PASTE)
                dispatchAllInvocationEvents()
            }
            tearDown {
                val document = myFixture.getDocument(myFixture.file)
                PsiDocumentManager.getInstance(project).commitDocument(document)
                kotlin.test.assertFalse(!ConvertJavaCopyPasteProcessor.Util.conversionPerformed, "No conversion to Kotlin suggested")
                assertEquals("class Foo {\n    private val value: String? = null\n}", myFixture.file.text)
                deleteFixtureFile()
            }
        }
    }

    private fun j2kIndex(): Int {
        return if (newJ2K) 1 else 0
    }

    fun doPerfTest(path: String) {
        val testName = getTestName(false)

        myFixture.testDataPath = testDataDirectory.slashedPath
        myFixture.configureByFiles("$testName.java")
        configureByDependencyIfExists("$testName.dependency.java")

        val fileText = myFixture.editor.document.text
        val noConversionExpected = InTextDirectivesUtils.findListWithPrefixes(fileText, "// NO_CONVERSION_EXPECTED").isNotEmpty()

        performanceTest<Unit, Unit> {
            name(testName)
            stats(stats())
            setUp {
                myFixture.configureByFiles("$testName.java")

                myFixture.performEditorAction(IdeActions.ACTION_COPY)

                configureByDependencyIfExists("$testName.dependency.kt")

                configureTargetFile("$testName.to.kt")

                ConvertJavaCopyPasteProcessor.Util.conversionPerformed = false
            }
            test {
                perfTestCore()
            }
            tearDown {
                commitAllDocuments()
                validate(path, noConversionExpected)
                deleteFixtureFile()
            }
        }
    }

    private fun deleteFixtureFile() {
        runWriteAction {
            myFixture.file.delete()
        }
    }

    private fun perfTestCore() {
        myFixture.performEditorAction(IdeActions.ACTION_PASTE)
        dispatchAllInvocationEvents()
    }

    private fun stats() = stats[j2kIndex()]

    open fun validate(path: String, noConversionExpected: Boolean) {
        kotlin.test.assertEquals(
            noConversionExpected, !ConvertJavaCopyPasteProcessor.Util.conversionPerformed,
            if (noConversionExpected) "Conversion to Kotlin should not be suggested" else "No conversion to Kotlin suggested"
        )
        KotlinTestUtils.assertEqualsToFile(File(path.replace(".java", ".expected.kt")), myFixture.file.text)
    }
}