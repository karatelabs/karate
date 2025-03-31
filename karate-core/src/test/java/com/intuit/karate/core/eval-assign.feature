Feature:

  Scenario:
    * def foo = { bar: 'one' }
    * foo.bar =
    """
    {
      some: 'big',
      message: 'content'
    }
    """
    * match foo == { bar: { some: 'big', message: 'content' } }