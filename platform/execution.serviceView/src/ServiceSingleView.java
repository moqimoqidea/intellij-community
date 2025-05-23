// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView;

import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.project.Project;
import com.intellij.platform.execution.serviceView.ServiceModel.ServiceViewItem;
import com.intellij.platform.execution.serviceView.ServiceViewModel.ServiceViewModelListener;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

final class ServiceSingleView extends ServiceView {
  private final AtomicReference<ServiceViewItem> myRef = new AtomicReference<>();
  private boolean mySelected;
  private final ServiceViewModelListener myListener;

  ServiceSingleView(@NotNull Project project, @NotNull ServiceViewModel model, @NotNull ServiceViewUi ui) {
    super(new BorderLayout(), project, model, ui);
    ui.setServiceToolbar(ServiceViewActionProvider.getInstance());
    add(ui.getComponent(), BorderLayout.CENTER);
    myListener = this::updateItem;
    model.addModelListener(myListener);
    model.getInvoker().invokeLater(this::updateItem);
  }

  @NotNull
  @Override
  Promise<Void> select(@NotNull Object service,
                       @NotNull Class<?> contributorClass) {
    ServiceViewItem item = myRef.get();
    if (item == null || !item.getValue().equals(service)) {
      return Promises.rejectedPromise("Service not found");
    }

    showContent();
    return Promises.resolvedPromise();
  }

  @Override
  Promise<Void> expand(@NotNull Object service, @NotNull Class<?> contributorClass) {
    return matches(service);
  }

  @Override
  Promise<Void> extract(@NotNull Object service, @NotNull Class<?> contributorClass) {
    return matches(service);
  }

  private Promise<Void> matches(@NotNull Object service) {
    ServiceViewItem item = myRef.get();
    return item == null || !item.getValue().equals(service) ? Promises.rejectedPromise("Service not found") : Promises.resolvedPromise();
  }

  @Override
  void onViewSelected() {
    showContent();
  }

  @Override
  void onViewUnselected() {
    mySelected = false;
    ServiceViewItem item = myRef.get();
    if (item != null) {
      item.getViewDescriptor().onNodeUnselected();
    }
  }

  @NotNull
  @Override
  @Unmodifiable List<ServiceViewItem> getSelectedItems() {
    ServiceViewItem item = myRef.get();
    return ContainerUtil.createMaybeSingletonList(item);
  }

  @Override
  void jumpToServices() {
  }

  @Override
  public void dispose() {
    getModel().removeModelListener(myListener);
  }

  private void updateItem() {
    WeakReference<ServiceViewItem> oldValueRef = new WeakReference<>(myRef.get());
    ServiceViewItem newValue = ContainerUtil.getOnlyItem(getModel().getRoots());
    WeakReference<ServiceViewItem> newValueRef = new WeakReference<>(newValue);
    myRef.set(newValue);
    AppUIExecutor.onUiThread().expireWith(this).submit(() -> {
      if (mySelected) {
        ServiceViewItem value = newValueRef.get();
        if (value != null) {
          ServiceViewDescriptor descriptor = value.getViewDescriptor();
          if (oldValueRef.get() == null) {
            onViewSelected(descriptor);
          }
          myUi.setDetailsComponentVisible(descriptor.isContentPartVisible());
          myUi.setDetailsComponent(descriptor.getContentComponent());
        }
      }
    });
  }

  private void showContent() {
    if (mySelected) return;

    mySelected = true;
    ServiceViewItem item = myRef.get();
    if (item != null) {
      ServiceViewDescriptor descriptor = item.getViewDescriptor();
      onViewSelected(descriptor);

      myUi.setDetailsComponentVisible(descriptor.isContentPartVisible());
      myUi.setDetailsComponent(descriptor.getContentComponent());
    }
  }

  @Override
  List<Object> getChildrenSafe(@NotNull List<Object> valueSubPath, @NotNull Class<?> contributorClass) {
    return Collections.emptyList();
  }
}
