Feature: log listener example

Scenario: ping
* def pingCount = { value: 0 }
* def listener = 
"""
function(line) {
  if (line.contains('time=')) {
    pingCount.value++;
    karate.log('count is', pingCount.value);
  }
  if (pingCount.value == 3) {
    karate.log('3 pings done, stopping');      
    var proc = karate.get('proc');
    karate.signal(proc.sysOut);
  }
}
"""
* def proc = karate.fork({ args: ['ping', 'google.com'], listener: listener })
* listen 5000
* print 'console output:', listenResult
