// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.java;

import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class JavaIdeLanguageCustomization extends IdeLanguageCustomization {
  @Override
  public @NotNull List<Language> getPrimaryIdeLanguages() {
    return ContainerUtil.addAllNotNull(
      new ArrayList<>(),
      JavaLanguage.INSTANCE,
      Language.findLanguageByID("kotlin")
    );
  }
}
