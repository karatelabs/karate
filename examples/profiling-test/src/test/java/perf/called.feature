Feature:

  Scenario:
    * def var1 = { foo: 'bar' }
    * def var2 = { baz: { hello: 'world' } }
    * def var3 = function(name){ return 'hello ' + name }
    * def extraKey = karate.get('extraKey')
    # to see the next line in the log, change level to warn
    * if (extraKey) karate.logger.info('called from gatling:', extraKey)
