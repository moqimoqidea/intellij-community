// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.dev.psiViewer.debug

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

internal class PsiViewerDebugSelectUIAction : ToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean = PsiViewerDebugSettings.getInstance().showDialogFromDebugAction

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    PsiViewerDebugSettings.getInstance().showDialogFromDebugAction = state
  }
}