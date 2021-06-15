Feature:

Scenario:
* text foo =
    """
    name,type
    Billie,LOL
    Bob,Wild
    """
* csv bar = foo
* match bar == [{ name: 'Billie', type: 'LOL' }, { name: 'Bob', type: 'Wild' }]
