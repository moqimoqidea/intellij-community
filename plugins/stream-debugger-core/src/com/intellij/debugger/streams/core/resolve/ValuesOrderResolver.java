// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.resolve;

import com.intellij.debugger.streams.core.trace.TraceElement;
import com.intellij.debugger.streams.core.trace.TraceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public interface ValuesOrderResolver {
  @NotNull
  Result resolve(@NotNull TraceInfo info);

  interface Result {
    @NotNull
    Map<TraceElement, List<TraceElement>> getDirectOrder();

    @NotNull
    Map<TraceElement, List<TraceElement>> getReverseOrder();

    static Result of(@NotNull Map<TraceElement, List<TraceElement>> direct, @NotNull Map<TraceElement, List<TraceElement>> reverse) {
      return new Result() {
        @Override
        public @NotNull Map<TraceElement, List<TraceElement>> getDirectOrder() {
          return direct;
        }

        @Override
        public @NotNull Map<TraceElement, List<TraceElement>> getReverseOrder() {
          return reverse;
        }
      };
    }
  }
}
