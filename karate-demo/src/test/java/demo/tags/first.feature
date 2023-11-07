@smoke
Feature: tags demo - first
    run the following example from the command line:
    mvn test -Dkarate.options="--tags @smoke" -Dtest=TagsRunner

Scenario: f1 - s1
    * print 'first feature:@smoke, first scenario'

@fire
Scenario: f1 - s2
    * print 'first feature:@smoke, second scenario:@fire'
