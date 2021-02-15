Feature: Example

Background:
  * def prepare_data = call read('xml-called.feature')
  * def data = prepare_data.data

Scenario Outline: make sure any json clone operations don't crash during call
  * print name
  * print stats

  Examples:
    | data |

Scenario: make sure call arg json conversion for reporting fails gracefully
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
* call read('xml-called.feature') { nodes: '#(nodes)' }
