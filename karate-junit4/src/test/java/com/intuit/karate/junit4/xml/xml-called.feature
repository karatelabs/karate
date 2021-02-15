@ignore
Feature:

Scenario:
* def xml =
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
* def nodes = get xml //hello
* def data = [{name: "A", stats: {"vitality":2}}, {name: "B", stats: {"strength":3}}, {name: "C", stats: {"intelligence":5}}]

