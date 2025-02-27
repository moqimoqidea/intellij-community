// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.debugger.test.sequence.psi.java

import com.intellij.debugger.streams.core.wrapper.StreamChainBuilder
import org.jetbrains.kotlin.idea.debugger.sequence.lib.java.JavaStandardLibrarySupportProvider
import org.jetbrains.kotlin.idea.debugger.test.sequence.KotlinPsiChainBuilderTestCase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class NegativeJavaStreamTest : KotlinPsiChainBuilderTestCase.Negative("streams/negative") {
    override val kotlinChainBuilder: StreamChainBuilder = JavaStandardLibrarySupportProvider().chainBuilder

    fun testFakeStream() = doTest()

    fun testWithoutTerminalOperation() = doTest()

    fun testNoBreakpoint() = doTest()

    fun testBreakpointOnMethod() = doTest()
    fun testBreakpointOnIfCondition() = doTest()
    fun testBreakpointOnNewScope() = doTest()
    fun testBreakpointOnElseBranch() = doTest()

    fun testInLambda() = doTest()
    fun testInLambdaWithBody() = doTest()
    fun testInAnonymous() = doTest()

    fun testAfterStatement() = doTest()

    fun testInPreviousStatement() = doTest()
    fun testInNextStatement() = doTest()

    fun testIdea173415() = doTest()
}