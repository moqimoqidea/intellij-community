package com.intellij.terminal.frontend.view.hyperlinks

import com.intellij.openapi.Disposable
import com.intellij.terminal.frontend.view.hyperlinks.TerminalOutputModelChangesTracker.Companion.MAX_CHANGES_HISTORY_LENGTH
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener

internal class TerminalOutputModelChangesTracker(
  private val outputModel: TerminalOutputModel,
  parentDisposable: Disposable,
) {
  // Variables should be accessed only from EDT
  private var contentChanged: Boolean = true
  private var firstChangedLine: TerminalLineIndex = outputModel.firstLineIndex

  /** Ordered by [ChangeInfo.modificationStamp] */
  private val changesHistory = ArrayDeque<ChangeInfo>(initialCapacity = MAX_CHANGES_HISTORY_LENGTH)

  init {
    outputModel.addListener(parentDisposable, object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        if (!event.isTrimming) {
          val line = event.model.getLineByOffset(event.offset)
          firstChangedLine = minOf(firstChangedLine, line)
        }
        contentChanged = true
      }
    })
  }

  /**
   * Returns the first changed line index since the last call.
   */
  @RequiresEdt
  fun getFirstChangedLineAndReset(): TerminalLineIndex? {
    if (!contentChanged) return null

    // The stored line may be below `outputModel.firstLineIndex` if trim happened after it was recorded.
    // Clamp it, so callers never see a line that no longer exists in the model.
    val line = maxOf(firstChangedLine, outputModel.firstLineIndex)
    recordChange(line)

    contentChanged = false
    firstChangedLine = outputModel.lastLineIndex
    return line
  }

  /**
   * Analyzes the output model changes history to find the range of content that was changed since the [modificationStamp].
   * Returns the start of this range - the first changed character offset.
   */
  @RequiresEdt
  fun getFirstChangedOffsetSinceStamp(modificationStamp: Long): TerminalOffset {
    val searchResult = changesHistory.binarySearch { changeInfo ->
      if (changeInfo.modificationStamp <= modificationStamp) -1 else 1
    }
    val nextChangeIndex = -searchResult - 1
    if (nextChangeIndex == changesHistory.size) {
      // No changes after the specified stamp, return the end of the model
      return outputModel.endOffset
    }

    val offset = changesHistory.subList(nextChangeIndex, changesHistory.size).minOf { it.startOffset }
    // Clamp to the current end offset. The recorded change offsets can become stale when the
    // document shrinks (for example, `clear`) after a change was recorded but before the next flush updates the history.
    return minOf(offset, outputModel.endOffset)
  }

  private fun recordChange(startLine: TerminalLineIndex) {
    val offset = outputModel.getStartOfLine(startLine)
    val changeInfo = ChangeInfo(offset, outputModel.modificationStamp)
    changesHistory.addLast(changeInfo)
    while (changesHistory.size > MAX_CHANGES_HISTORY_LENGTH) {
      changesHistory.removeFirst()
    }
  }

  private data class ChangeInfo(
    // The offset of the first changed character
    val startOffset: TerminalOffset,
    // The modification stamp of the document at the moment of registering the change
    val modificationStamp: Long,
  )

  companion object {
    /**
     * Covers the changes in the output model history
     * for [MAX_CHANGES_HISTORY_LENGTH] * [HYPERLINKS_OUTPUT_MODEL_FLUSH_DELAY] = 2 seconds
     */
    private const val MAX_CHANGES_HISTORY_LENGTH = 100
  }
}
