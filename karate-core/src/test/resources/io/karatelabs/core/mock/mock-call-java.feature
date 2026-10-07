Feature: a helper that reaches for Java and the OS

@java
Scenario:
* def version = Java.type('java.lang.System').getProperty('java.specification.version')

@exec
Scenario:
* def version = karate.exec(['java', '-version'])
