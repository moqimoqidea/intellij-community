/*
 * Copyright 2002-2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.xpathView;

import com.intellij.openapi.editor.markup.TextAttributes;
import org.intellij.plugins.xpathView.search.SearchScope;

import java.awt.*;

/**
 * Class that holds the plugin's configuration. All customizable settings are accessible via property getters/setters.
 * The configuration itself can be acquired with {@code getConfig()} in {@link XPathAppComponent}.
 */
public class Config {
  public boolean OPEN_NEW_TAB = false;
  public boolean HIGHLIGHT_RESULTS = true;
  public boolean SHOW_USAGE_VIEW = false;

  public SearchScope SEARCH_SCOPE = new SearchScope();
  public boolean MATCH_RECURSIVELY = false;

  private final TextAttributes attributes = new TextAttributes(null, new Color(255, 213, 120), null, null, Font.PLAIN);
  private final TextAttributes contextAttributes = new TextAttributes(null, new Color(194, 255, 212), null, null, Font.PLAIN);
  public boolean scrollToFirst = true;
  public boolean bUseContextAtCursor = true;
  public boolean bHighlightStartTagOnly = true;
  public boolean bAddErrorStripe = true;

  public boolean isScrollToFirst() {
    return scrollToFirst;
  }

  public TextAttributes getAttributes() {
    return attributes;
  }

  public TextAttributes getContextAttributes() {
    return contextAttributes;
  }

  public boolean isUseContextAtCursor() {
    return bUseContextAtCursor;
  }

  public boolean isHighlightStartTagOnly() {
    return bHighlightStartTagOnly;
  }

  public boolean isAddErrorStripe() {
    return bAddErrorStripe;
  }

  // May be not used now
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final Config config = (Config)o;

    if (HIGHLIGHT_RESULTS != config.HIGHLIGHT_RESULTS) return false;
    if (OPEN_NEW_TAB != config.OPEN_NEW_TAB) return false;
    if (SHOW_USAGE_VIEW != config.SHOW_USAGE_VIEW) return false;
    if (bAddErrorStripe != config.bAddErrorStripe) return false;
    if (bHighlightStartTagOnly != config.bHighlightStartTagOnly) return false;
    if (bUseContextAtCursor != config.bUseContextAtCursor) return false;
    if (scrollToFirst != config.scrollToFirst) return false;
    if (!attributes.equals(config.attributes)) return false;
    return contextAttributes.equals(config.contextAttributes);
  }

  // May be not used now
  @Override
  public int hashCode() {
    int result = (OPEN_NEW_TAB ? 1 : 0);
    result = 29 * result + (HIGHLIGHT_RESULTS ? 1 : 0);
    result = 29 * result + (SHOW_USAGE_VIEW ? 1 : 0);
    result = 29 * result + attributes.hashCode();
    result = 29 * result + contextAttributes.hashCode();
    result = 29 * result + (scrollToFirst ? 1 : 0);
    result = 29 * result + (bUseContextAtCursor ? 1 : 0);
    result = 29 * result + (bHighlightStartTagOnly ? 1 : 0);
    result = 29 * result + (bAddErrorStripe ? 1 : 0);
    return result;
  }
}
