// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class FileNameWithoutAllExtensions extends FileNameMacro {
  @Override
  public @NotNull String getName() {
    return "FileNameWithoutAllExtensions";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.file.name.without.all.extensions");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) {
      return null;
    }
    String fileName = file.getName();
    int index = fileName.indexOf('.');
    return index > 0 ? fileName.substring(0, index) : fileName;
  }
}
