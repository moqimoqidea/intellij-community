// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef;

import com.intellij.idea.AppMode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.jcef.JBCefApp;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider;
import org.intellij.plugins.markdown.ui.preview.SourceTextPreprocessor;
import org.jetbrains.annotations.NotNull;

public final class JCEFHtmlPanelProvider extends MarkdownHtmlPanelProvider {

  @NotNull
  @Override
  public MarkdownHtmlPanel createHtmlPanel() {
    return new MarkdownJCEFHtmlPanel();
  }

  @Override
  public @NotNull MarkdownHtmlPanel createHtmlPanel(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    if (!canBeUsed()) {
      throw new IllegalStateException("Tried to create a JCEF panel, but JCEF is not supported in the current environment");
    }
    return new MarkdownJCEFHtmlPanel(project, virtualFile);
  }

  @NotNull
  @Override
  public AvailabilityInfo isAvailable() {
    return canBeUsed() ? AvailabilityInfo.AVAILABLE : AvailabilityInfo.UNAVAILABLE;
  }

  @NotNull
  @Override
  public ProviderInfo getProviderInfo() {
    return new ProviderInfo("JCEF Browser", JCEFHtmlPanelProvider.class.getName());
  }

  @Override
  public @NotNull SourceTextPreprocessor getSourceTextPreprocessor() {
    return new HtmlSourceTextPreprocessor();
  }

  public static boolean canBeUsed() {
    return !AppMode.isRemoteDevHost() && JBCefApp.isSupported();
  }
}
