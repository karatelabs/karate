Feature: XML call test

Scenario: call with XML nodes as argument
* def moreXml =
"""
<root>
    <hello>
        <there>everyone</there>
    </hello>
    <hello>
        <there>anyone</there>
    </hello>
</root>
"""
* def nodes = get moreXml //hello
* def response = call read('xml-called.feature') { nodes: '#(nodes)' }
* match response.result[0] == <hello><there>everyone</there></hello>
