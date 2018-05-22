Feature: tags demo - second
    run the following example from the command line:
    mvn test -Dcucumber.options="--tags @smoke" -Dtest=TagsRunner

Scenario: f2 - s1
    * print 'second feature, first scenario'

@smoke @fire
Scenario: f2 - s2
    * print 'second feature, second scenario:@smoke @fire'
