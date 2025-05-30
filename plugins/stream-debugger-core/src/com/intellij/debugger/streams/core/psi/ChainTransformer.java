// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.core.psi;

import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public interface ChainTransformer<T extends PsiElement> {
  @NotNull
  StreamChain transform(@NotNull List<T> callChain, @NotNull PsiElement context);


}
