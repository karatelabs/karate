Feature:

Background:
 # background http builder should work even for a dynamic scenario outline
 * url serverUrl 
 # java object that comes from a callSingle in the config
 * def HelloBg = HelloConfigSingle
 * callonce read('call-once-from-feature.feature')
 # cookies are normalized, so reading a JS function should have no impacts (will read as a null variable)
 * configure cookies = read('cookies.js')
 * configure afterFeature =
 """
   function fn() {
      console.log('afterFeature');
   }
 """
  * configure afterScenario =
  """
    function fn() {
       console.log('afterScenario');
    }
  """

@setup
Scenario:
* def data = [ { name: 'value1' }, { name: 'value2' }, { name: 'value3' }, { name: 'value4' } ]

Scenario Outline:
 * call read('called.feature')
 * match functionFromKarateBase() == 'fromKarateBase'
 * path 'fromfeature'
 * method get
 * status 200
 * match response == { message: 'from feature' }
 
 * match HelloBg.sayHello('world') == 'hello world'
 * match HelloOnce.sayHello('world') == 'hello world'
 * match sayHello('world') == 'hello world'

 Examples:
  | karate.setup().data |

Scenario Outline: validating background http context set in background will be shared in shared scope, with dynamic scenario outline
 * call read('called.feature')
 * match functionFromKarateBase() == 'fromKarateBase'
 * call read('parallel-outline-call-api.feature')
 * match response == { message: 'from feature' }


 * match HelloBg.sayHello('world') == 'hello world'
 * match HelloOnce.sayHello('world') == 'hello world'
 * match sayHello('world') == 'hello world'

 Examples:
  | karate.setup().data |