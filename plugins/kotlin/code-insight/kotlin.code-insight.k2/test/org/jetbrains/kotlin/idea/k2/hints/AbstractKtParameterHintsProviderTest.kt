// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import org.jetbrains.kotlin.idea.codeInsight.hints.AbstractKotlinInlayHintsProviderTest
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_COMPILED_PARAMETERS
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_EXCLUDED_PARAMETERS
import org.jetbrains.kotlin.idea.k2.codeinsight.hints.KtParameterHintsProvider

abstract class AbstractKtParameterHintsProviderTest: AbstractKotlinInlayHintsProviderTest() {

    override fun inlayHintsProvider(): InlayHintsProvider =
        KtParameterHintsProvider()

    override fun calculateOptions(fileContents: String): Map<String, Boolean> =
        buildMap {
            put(SHOW_EXCLUDED_PARAMETERS.name, false)
            put(SHOW_COMPILED_PARAMETERS.name, true)
        }
}