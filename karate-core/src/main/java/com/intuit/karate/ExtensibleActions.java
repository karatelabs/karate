package com.intuit.karate;

import com.intuit.karate.core.ScenarioEngine;

/**
 * Defines the mechanisms to wire in user defined customizations.
 */
public interface ExtensibleActions {

  /**
   * @return - The current implementation class name.
   */
  Class<? extends ExtensibleActions> implementationClass();

  /**
   * @param engine - The {@link ScenarioEngine} object from Karate that may be required for the user
   *               defined customization.
   */
  void initialiseEngine(ScenarioEngine engine);

}
