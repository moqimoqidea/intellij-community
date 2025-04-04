// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.sequence.trace.impl.handler

import com.intellij.debugger.streams.core.trace.impl.handler.type.GenericType
import com.intellij.debugger.streams.core.wrapper.CallArgument
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.core.wrapper.StreamCallType
import com.intellij.debugger.streams.core.wrapper.impl.CallArgumentImpl
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.debugger.sequence.trace.dsl.KotlinSequenceTypes

class OnEachCall(private val elementsType: GenericType, lambda: String) : IntermediateStreamCall {
    private val args: List<CallArgument> = listOf(CallArgumentImpl(KotlinSequenceTypes.ANY.genericTypeName, lambda))

    override fun getArguments(): List<CallArgument> = args

    override fun getName(): String = "onEach"

    override fun getGenericArguments(): String = ""

    override fun getType(): StreamCallType = StreamCallType.INTERMEDIATE

    override fun getTextRange(): TextRange = TextRange.EMPTY_RANGE

    override fun getTypeBefore(): GenericType = elementsType

    override fun getTypeAfter(): GenericType = elementsType
}