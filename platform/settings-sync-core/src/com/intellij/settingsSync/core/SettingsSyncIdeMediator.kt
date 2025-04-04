package com.intellij.settingsSync.core

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Connects the Settings Sync engine with the IDE:
 * when a setting is changed in the IDE, sends it to the Settings Sync to log and push;
 * when the settings sync log gets changed, applies the change to the IDE.
 */
@ApiStatus.Internal
interface SettingsSyncIdeMediator {

  /**
   * @param settings if not null, SettingsSync settings will be taken from this object rather than snapshot
   */
  suspend fun applyToIde(snapshot: SettingsSnapshot, settings: SettingsSyncState?)

  fun activateStreamProvider()

  fun removeStreamProvider()

  /**
   * Returns the settings snapshot according to the state of the IDE. This method is called on start of the IDE session.
   * Sometimes the last saved snapshot can be needed to form the current snapshot properly
   * (e.g. not remove certain data from the saved snapshot).
   */
  fun getInitialSnapshot(appConfigPath: Path, lastSavedSnapshot: SettingsSnapshot): SettingsSnapshot

}