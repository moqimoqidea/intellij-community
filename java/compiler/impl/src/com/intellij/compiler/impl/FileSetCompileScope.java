// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.ExportableUserDataHolderBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class FileSetCompileScope extends ExportableUserDataHolderBase implements CompileScope {
  private final Set<VirtualFile> myRootFiles = new HashSet<>();
  private final Set<String> myDirectoryUrls = new HashSet<>();
  private Set<String> myUrls; // urls caching
  private final Module[] myAffectedModules;

  public FileSetCompileScope(@NotNull Collection<VirtualFile> files, Module @NotNull [] modules) {
    myAffectedModules = modules;
    ApplicationManager.getApplication().runReadAction(
      () -> {
        for (VirtualFile file : files) {
          assert file != null;
          addFile(file);
        }
      }
    );
  }

  @Override
  public Module @NotNull [] getAffectedModules() {
    return myAffectedModules;
  }

  public @NotNull Collection<VirtualFile> getRootFiles() {
    return Collections.unmodifiableCollection(myRootFiles);
  }

  @Override
  public VirtualFile @NotNull [] getFiles(final FileType fileType, boolean inSourceOnly) {
    final List<VirtualFile> files = new ArrayList<>();
    for (Iterator<VirtualFile> it = myRootFiles.iterator(); it.hasNext();) {
      VirtualFile file = it.next();
      if (!file.isValid()) {
        it.remove();
        continue;
      }
      if (file.isDirectory()) {
        addRecursively(files, file, fileType);
      }
      else {
        if (fileType == null || FileTypeRegistry.getInstance().isFileOfType(file, fileType)) {
          files.add(file);
        }
      }
    }
    return VfsUtilCore.toVirtualFileArray(files);
  }

  @Override
  public boolean belongs(@NotNull String url) {
    //url = CompilerUtil.normalizePath(url, '/');
    if (getUrls().contains(url)) {
      return true;
    }
    for (String directoryUrl : myDirectoryUrls) {
      if (FileUtil.startsWith(url, directoryUrl)) {
        return true;
      }
    }
    return false;
  }

  private @NotNull Set<String> getUrls() {
    if (myUrls == null) {
      myUrls = new HashSet<>();
      for (VirtualFile file : myRootFiles) {
        String url = file.getUrl();
        myUrls.add(url);
      }
    }
    return myUrls;
  }

  private void addFile(@NotNull VirtualFile file) {
    if (file.isDirectory()) {
      myDirectoryUrls.add(file.getUrl() + "/");
    }
    myRootFiles.add(file);
    myUrls = null;
  }

  private static void addRecursively(@NotNull Collection<? super VirtualFile> container, @NotNull VirtualFile fromDirectory, @Nullable FileType fileType) {
    VfsUtilCore.visitChildrenRecursively(fromDirectory, new VirtualFileVisitor<Void>(VirtualFileVisitor.SKIP_ROOT) {
      @Override
      public boolean visitFile(@NotNull VirtualFile child) {
        if (!child.isDirectory() && (fileType == null || FileTypeRegistry.getInstance().isFileOfType(child, fileType))) {
          container.add(child);
        }
        return true;
      }
    });
  }
}
