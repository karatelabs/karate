Feature:

Scenario:
* def nums = { profit: -1002.2000000000002 }
* match nums == { profit: -1002.2000000000002 }
* def nums = { profit: -1002.2000000000000 }
* match nums == { profit: -1002.20 }
* def nums = { profit: -1002.20 }
* match nums == { profit: -1002.2000000000000 }
