@ignore
Feature: large payloads

Scenario: large xml
    * def xml = read('file:target/large.xml')
    * set xml /ProcessRequest/statusCode = 'shipped'
    * match xml == <ProcessRequest xmlns="http://someservice.com/someProcess"><statusCode>shipped</statusCode><foo>#ignore</foo></ProcessRequest>
