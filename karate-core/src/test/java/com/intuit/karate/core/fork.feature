Feature:

Background:
* def command =
"""
function(line) {
  var proc = karate.fork({ redirectErrorStream: false, useShell: true, line: line });
  proc.waitSync();
  karate.set('sysOut', proc.sysOut);
  karate.set('exitCode', proc.exitCode);
}
"""

Scenario: java cli
* if (karate.os.type == 'windows') karate.abort()
* command('ls')
* match exitCode == 0
* match sysOut contains 'pom.xml'
