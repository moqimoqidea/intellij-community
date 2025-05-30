package com.intellij.polySymbols.webTypes.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JType;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.rules.TypeRule;

public class WebTypesTypeRule extends TypeRule {

  private final WebTypesRuleFactory myRuleFactory;

  protected WebTypesTypeRule(WebTypesRuleFactory ruleFactory) {
    super(ruleFactory);
    myRuleFactory = ruleFactory;
  }

  @Override
  public JType apply(String nodeName,
                     JsonNode node,
                     JsonNode parent,
                     JClassContainer jClassContainer,
                     Schema schema) {
    var fromCache = myRuleFactory.getTypeFromCache(nodeName, node);
    if (fromCache != null) {
      return fromCache;
    }

    JType result = myRuleFactory.getComplexTypeRule().apply(nodeName, node, parent, jClassContainer.getPackage(), schema);
    if (result == null) {
      result = super.apply(nodeName, node, parent, jClassContainer, schema);
    }
    if (result != null){
      myRuleFactory.storeTypeInCache(nodeName, node, result);
    }
    return result;
  }


}
