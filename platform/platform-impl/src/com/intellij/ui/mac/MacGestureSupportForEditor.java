// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.apple.eawt.event.GestureUtilities;
import com.apple.eawt.event.PressureEvent;
import com.apple.eawt.event.PressureListener;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.PressureShortcut;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

@ApiStatus.Internal
public final class MacGestureSupportForEditor {

  public MacGestureSupportForEditor(JComponent component, AWTEventListener listener) {
    GestureUtilities.addGestureListenerTo(component, new PressureListener() {
      @Override
      public void pressure(PressureEvent e) {
        if (e.getStage() != 2) return;
        InputEvent inputEvent = new ForceTouchEvent(component, e);
        ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(()-> {
          if (listener != null) listener.eventDispatched(inputEvent);
          IdeEventQueue.getInstance().getThreadingSupport().runPreventiveWriteIntentReadAction(() -> {
            IdeEventQueue.getInstance().getMouseEventDispatcher().processEvent(
              inputEvent, 0, ActionPlaces.FORCE_TOUCH, new PressureShortcut(e.getStage()), component, false);
            return null;
          });
        });
      }
    });
  }

  private static final class ForceTouchEvent extends MouseEvent {
    final PressureEvent pressureEvent;

    ForceTouchEvent(Component source, PressureEvent pressureEvent) {
      super(source, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false);
      this.pressureEvent = pressureEvent;
    }

    @Override
    public void consume() {
      super.consume();
      pressureEvent.consume();
    }
  }
}
