@ignore
Feature: Tag Filter multi-scenario-outline feature.
# Note, in this feature, tags are used in both scenario outlines. This should pass the tag filter which is checking that there are tags defined.

  Background:

  @domain=TXN-WORKFLOW
  Scenario Outline: my outline 1
    * print 'I am in outline 1.'

  @testId=1
    Examples:
      | input|
      | true |

  @testId=2
    Examples:
      | input|
      | true |

  @testId=3
    Examples:
      | input|
      | true |

  @testId=4
    Examples:
      | input|
      | true |

  @testId=5
    Examples:
      | input|
      | true |

  @domain=TXN-WORKFLOW
  Scenario Outline: my outline 2
    * print 'I am in outline 2.'

  @testId=6
    Examples:
      | input|
      | true |

  @testId=7
    Examples:
      | input|
      | true |