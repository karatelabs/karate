Feature:

Background:
* def Runtime = Java.type('java.lang.Runtime').getRuntime()

Scenario:
* Runtime.exec('Chrome')
