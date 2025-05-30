// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.polySymbols.references.impl

import com.intellij.lang.Language
import com.intellij.lang.MetaLanguage
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PsiPolySymbolReferenceProviders {
  private val EP_NAME = ExtensionPointName<PsiPolySymbolReferenceProviderBean>("com.intellij.polySymbols.psiReferenceProvider")

  /**
   * Given language of a host element returns list of providers that could provide references from this language.
   */
  internal fun byLanguage(language: Language): PolySymbolLanguageReferenceProviders {
    return EP_NAME.computeIfAbsent(language, PsiPolySymbolReferenceProviders::class.java) { byLanguageInner(it) }
  }

  private fun byLanguageInner(language: Language): PolySymbolLanguageReferenceProviders {
    val result = mutableListOf<PsiPolySymbolReferenceProviderBean>()
    for (bean in EP_NAME.extensionList) {
      val hostLanguage = bean.getHostLanguage()
      val matches = if (hostLanguage is MetaLanguage)
        hostLanguage.matchesLanguage(language)
      else
        hostLanguage === Language.ANY || language.isKindOf(hostLanguage)
      if (matches) {
        result.add(bean)
      }
    }
    return PolySymbolLanguageReferenceProviders(result)
  }
}
