// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

public interface PresentableEnum {
  @NotNull
  @NlsContexts.Label String getPresentableText();
}
