// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.eventLog.connection.EventLogStatisticsService;
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataSettingsPersistence;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.fus.reporting.model.http.StatsConnectionSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import static com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider;

@ApiStatus.Internal
public class EventLogInternalApplicationInfo implements EventLogApplicationInfo {
  public static final String EVENT_LOG_SETTINGS_REGION_CODE = "not_set";

  private static final DataCollectorDebugLogger LOG =
    new InternalDataCollectorDebugLogger(Logger.getInstance(EventLogStatisticsService.class));

  private final boolean myIsTestSendEndpoint;
  private final boolean myIsTestConfig;
  private final DataCollectorSystemEventLogger myEventLogger;
  private final StatsConnectionSettings myConnectionSettings;

  public EventLogInternalApplicationInfo(boolean isTestConfig, boolean isTestSendEndpoint) {
    myIsTestConfig = isTestConfig;
    myIsTestSendEndpoint = isTestSendEndpoint;
    myConnectionSettings = new StatsAppConnectionSettings();
    myEventLogger = new DataCollectorSystemEventLogger() {
      @Override
      public void logLoadingConfigFailed(@NotNull String recorderId, @NotNull Throwable exception) {
        EventLogSystemCollector eventLogSystemCollector =
          getEventLogProvider(recorderId).getEventLogSystemLogger$intellij_platform_statistics();
        eventLogSystemCollector.logLoadingConfigFailed(exception.getClass().getName(), -1);
      }
    };
  }

  @Override
  public @NotNull String getRegionalCode() {
    final String regionCode = StatisticsRegionSettingsService.Companion.getInstance().getRegionCode();
    return regionCode == null ? EVENT_LOG_SETTINGS_REGION_CODE : regionCode;
  }

  @Override
  public @NotNull String getProductCode() {
    ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();
    String fullIdeProductCode = applicationInfo.getFullIdeProductCode();
    return fullIdeProductCode != null ? fullIdeProductCode : applicationInfo.getBuild().getProductCode();
  }

  @Override
  public @NotNull String getProductVersion() {
    final ApplicationInfo info = ApplicationInfo.getInstance();
    return info.getMajorVersion() + "." + info.getMinorVersion();
  }

  @Override
  public int getBaselineVersion() {
    final ApplicationInfo info = ApplicationInfo.getInstance();
    return info.getBuild().getBaselineVersion();
  }

  @Override
  public @NotNull StatsConnectionSettings getConnectionSettings() {
    return myConnectionSettings;
  }

  @Override
  public boolean isInternal() {
    // There is a small chance that this will be called before InternalFlagDetection is executed,
    // and the result will be false while actually it should be true.
    // But it seems to be only when the user hasn't been detected as internal yet and stays on Welcome Screen before the IDE is closed.
    return EventLogMetadataSettingsPersistence.getInstance().isInternal();
  }

  @Override
  public boolean isTestConfig() {
    return myIsTestConfig;
  }

  @Override
  public boolean isTestSendEndpoint() {
    return myIsTestSendEndpoint;
  }

  @Override
  public boolean isEAP() {
    return ApplicationManager.getApplication().isEAP();
  }

  @Override
  public @NotNull DataCollectorDebugLogger getLogger() {
    return LOG;
  }

  @Override
  public @NotNull DataCollectorSystemEventLogger getEventLogger() {
    return myEventLogger;
  }
}
