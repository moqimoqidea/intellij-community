package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.xQuery
import javax.swing.JScrollBar

fun Finder.tryToScrollDown() {
  runCatching {
    scrollBars().single { it.getOrientation() == JScrollBar.VERTICAL }.scrollToMaximum()
  }
}

fun Finder.tryToScrollRight() {
  runCatching {
    scrollBars().single { it.getOrientation() == JScrollBar.HORIZONTAL }.scrollToMaximum()
  }
}

fun Finder.scrollBars(): List<JScrollBarUi> = xx(xQuery { byType(JScrollBar::class.java) }, JScrollBarUi::class.java).list()

fun Finder.verticalScrollBar(f: JScrollBarUi.() -> Unit = {}) =
  scrollBars().single { it.getOrientation() == JScrollBar.VERTICAL }.apply(f)

class JScrollBarUi(data: ComponentData) : UiComponent(data) {
  private val scrollBar get() = driver.cast(component, JScrollBarComponent::class)
  private val fixture by lazy { driver.new(JScrollBarFixture::class, robot, component) }

  fun getOrientation() = scrollBar.getOrientation()

  fun scrollToMaximum() {
    fixture.scrollToMaximum()
  }

  fun scrollToMinimum() {
    fixture.scrollToMinimum()
  }

  fun scrollBlockDown(times: Int) {
    fixture.scrollBlockDown(times)
  }

  fun getScrollValue() = scrollBar.getValue()
}

@Remote("javax.swing.JScrollBar")
interface JScrollBarComponent {
  fun getOrientation(): Int
  fun getValue(): Int
}

@Remote("org.assertj.swing.fixture.JScrollBarFixture")
interface JScrollBarFixture {
  fun scrollToMaximum(): JScrollBarFixture
  fun scrollToMinimum(): JScrollBarFixture
  fun scrollBlockDown(times: Int): JScrollBarFixture
}