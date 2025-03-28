package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.AccessibleNameCellRendererReader
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleTree
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.rdTarget
import com.intellij.driver.sdk.withRetries
import org.intellij.lang.annotations.Language
import java.awt.Point
import kotlin.time.Duration.Companion.seconds

fun Finder.bookmarksToolWindow(action: BookmarksToolWindowUiComponent.() -> Unit = {}) =
  x(BookmarksToolWindowUiComponent::class.java) { byAccessibleName("Bookmarks Tool Window") }.apply(action)

class BookmarksToolWindowUiComponent(data: ComponentData) : UiComponent(data) {

  val bookmarksTree by lazy {
    tree().apply { replaceCellRendererReader(driver.new(AccessibleNameCellRendererReader::class, rdTarget = component.rdTarget)) }
  }

  fun rightClickOnBookmarkWithText(text: String, fullMatch: Boolean = true) = bookmarksTree.apply {
    rightClickRow(findBookmarkWithText(text, fullMatch).row)
  }

  fun clickOnBookmarkWithText(text: String, fullMatch: Boolean = true) = bookmarksTree.apply {
    clickRow(findBookmarkWithText(text, fullMatch).row)
  }

  fun doubleClickOnBookmarkWithText(text: String, fullMatch: Boolean = true) = bookmarksTree.apply {
    doubleClickRow(findBookmarkWithText(text, fullMatch).row)
  }

  fun pressMoreButton() {
    actionMenuAppearance()
    x("//div[@class='ActionButton' and @myicon='moreVertical.svg']").click()
  }

  fun expandAllBookmarksTree() {
    actionMenuAppearance()
    x("//div[@class='ActionButton' and @myicon='expandAll.svg']").click()
  }

  private fun actionMenuAppearance(){
    moveMouse()
    moveMouse(Point(component.x + 20, component.y - 20))
  }

  private fun findBookmarkWithText(text: String, fullMatch: Boolean = true) = bookmarksTree.collectExpandedPaths().single {
    if (fullMatch) it.path.last() == text else it.path.last().contains(text, true)
  }
}

fun Finder.bookmarksMnemonicGrid(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='MyContentPanel'][//div[@class='BookmarkLayoutGrid']]", BookmarksGridLayoutUiComponent::class.java)

class BookmarksGridLayoutUiComponent(data: ComponentData) : UiComponent(data) {

  val textField
    get() = x("//div[@class='JBTextField']")

  fun findButton(text: String) = x("//div[@class='JButton' and @text='$text']", JButtonUIComponent::class.java)

  fun clickButton(text: String) = findButton(text).click()

  fun doubleClickButton(text: String) = findButton(text).doubleClick()

  class JButtonUIComponent(data: ComponentData) : UiComponent(data)
}

fun Finder.bookmarksPopup(action: BookmarksPopupUiComponent.() -> Unit = {}) =
  x("//div[@class='HeavyWeightWindow'][//div[@class='EngravedLabel' and @text='Bookmarks']]", BookmarksPopupUiComponent::class.java).apply(action)

class BookmarksPopupUiComponent(data: ComponentData) : UiComponent(data) {

  val bookmarksTree by lazy {
    tree().apply { replaceCellRendererReader(driver.new(AccessibleNameCellRendererReader::class, rdTarget = component.rdTarget)) }
  }

  fun getBookmarksList() = bookmarksTree.collectExpandedPaths().mapNotNull { it.path.lastOrNull() }

  fun clickBookmark(textContains: String) {
    bookmarksTree.clickRow(Point(5, 5)) { it.contains(textContains) }
  }

  fun doubleClickBookmark(textContains: String) {
    withRetries(times = 3) {
      bookmarksTree.doubleClickRow(Point(5, 5)) { it.contains(textContains) }
      waitNotFound(2.seconds)
    }
  }
}