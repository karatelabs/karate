Feature: cats stateful crud

  Background:
    * def uuid = function(){ return java.util.UUID.randomUUID() + '' }
    * def cats = {}

  Scenario: pathMatches('/cats') && methodIs('post')
    * def cat = request
    * def badRequest =
    """
    function() {
      karate.set('responseStatus', 400)
      karate.abort()
    }
    """
    * if (cat.name == 'Billie') badRequest()
    * def id = uuid()
    * cat.id = id
    * cats[id] = cat
    * def response = cat

  Scenario: pathMatches('/cats/{id}') && methodIs('get')
    * def id = pathParams.id
    * def notFound =
    """
    function() {
      karate.set('responseStatus', 404)
      karate.abort()
    }
    """
    * def cat = cats[id]
    * if (cat == null) notFound()
    * def response = cat

  Scenario: pathMatches('/cats/{id}') && methodIs('delete')
    * def id = pathParams.id
    * def notFound =
    """
    function() {
      karate.set('responseStatus', 404)
      karate.abort()
    }
    """
    * if (cats[id] == null) notFound()
    * karate.remove('cats', '$.' + id)
