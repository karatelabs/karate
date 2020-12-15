Feature:

Background:
* def command =
"""
function(line) {
  var proc = karate.fork({ redirectErrorStream: false, useShell: true, line: line });
  proc.waitSync();
  karate.set('sysErr', proc.sysErr);
  karate.set('exitCode', proc.exitCode);
}
"""

Scenario: java cli
* command('java -version')
* match sysErr == '#regex java version (.+[\\r\\n])+'
* assert exitCode == 0

Scenario: jave fail
* command('java -foo')
* match sysErr contains 'Unrecognized option: -foo'
* assert exitCode == 1
