Feature: dynamically creating json for a data-driven test

Background:
    * url demoBaseUrl
    * def creator = read('../callarray/kitten-create.feature')
    * def kittensFn =
    """
    function(count) {
      var out = [];
      for (var i = 0; i < count; i++) { 
        out.push({ name: 'Kit' + i });
      }
      return out;
    }
    """

Scenario: create kittens and validate
    * def kittens = call kittensFn 5
    * def result1 = call creator kittens
    * def created = $result1[*].response
    * assert created.length == 5
    * match each created == { id: '#number', name: '#regex Kit[0-4]' }
    * match created[*].name contains [ 'Kit0', 'Kit1', 'Kit2', 'Kit3', 'Kit4' ]

    # for each kitten created, 'get by id' and validate
    * def result2 = call read('get-cat.feature') created
    * match result2[*].response contains created
