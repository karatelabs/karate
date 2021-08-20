Feature:

  Scenario:
    * assert      'red ' + 5         ==  'red 5'
    * table       cats
      | name   | age |
      | 'Bob'  | 2   |
    * match       cats               ==  [{name: 'Bob', age: 2}]

    * def         text               =   'hello <foo> world'
    * replace     text.foo           =   'bar'
    * match       text               ==  'hello bar world'
    * print       text

    * def         myJson             =   {  foo: 'bar' }
    * set         myJson.foo         =   'world'
    * match       myJson             ==  { foo: 'world' }
    * remove      myJson.foo

    * configure   logPrettyResponse  =   true
    * param       myParam            =   'foo'
    * header      transaction-id     =   'test'
    * cookie      cook               =   'bar'
    * form field  username           =   'john'
