// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingContext;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

public interface FormattingNotificationService {
  static @NotNull FormattingNotificationService getInstance(@NotNull Project project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return HeadlessNotificationService.INSTANCE;
    }
    else {
      return project.getService(FormattingNotificationService.class);
    }
  }

  void reportError(@NotNull String groupId,
                   @NotNull @NlsContexts.NotificationTitle String title,
                   @NotNull @NlsContexts.NotificationContent String message);

  default void reportError(@NotNull String groupId,
                   @NotNull @NlsContexts.NotificationTitle String title,
                   @NotNull @NlsContexts.NotificationContent String message, AnAction... actions) {
    reportError(groupId, title, message);
  }

  void reportErrorAndNavigate(@NotNull String groupId,
                              @NotNull @NlsContexts.NotificationTitle String title,
                              @NotNull @NlsContexts.NotificationContent String message,
                              @NotNull FormattingContext context,
                              int offset);
}
