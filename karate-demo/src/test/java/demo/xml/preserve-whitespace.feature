Feature: Preserve white space in text content
Scenario:
    * def xml =
    """
    <myRoot xml:space="preserve">
        <myNode> myValue </myNode>
    </myRoot>
    """
    * match karate.xmlPath(xml, '/myRoot/myNode') == ' myValue '