// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.IconManager;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class XBreakpointClassGroup extends XBreakpointGroup {
  private final String myPackageName;
  private final String myClassName;

  public XBreakpointClassGroup(@Nullable String packageName, String className) {
    myPackageName = packageName != null ? packageName : getDefaultPackageName();
    myClassName = className;
  }

  @Override
  public Icon getIcon(boolean isOpen) {
    return IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class);
  }

  @Override
  public @NotNull String getName() {
    return getClassName();
  }

  public @NotNull @NlsSafe String getPackageName() {
    return myPackageName;
  }

  public @NotNull @NlsSafe String getClassName() {
    return myClassName;
  }

  private static String getDefaultPackageName() {
    return JavaDebuggerBundle.message("default.package.name");
  }
}
