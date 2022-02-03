package com.intuit.karate.core.extensions;

import com.intuit.karate.ExtensibleActions;
import com.intuit.karate.core.ScenarioEngine;
import cucumber.api.java.en.When;

public class Greetings implements ExtensibleActions {

  private ScenarioEngine engine;

  @Override
  public Class<? extends ExtensibleActions> implementationClass() {
    return getClass();
  }

  @Override
  public void initialiseEngine(ScenarioEngine engine) {
    this.engine = engine;

  }

  @When("^greet (.+)")
  public void greet(String exp) {
    engine.print(exp);
  }
}
