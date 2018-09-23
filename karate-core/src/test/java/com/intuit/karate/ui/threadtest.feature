@ignore
Feature: Thread Test

Scenario: Scenario-1
* print "Scenario-1 Started"
* string a = 'a'
* string b = 'b'
* string c = 'c'
* print "Scenario-1 Finished"

Scenario: Scenario-2
* print "Scenario-2 Started"
* string x = 'x'
* def Thread = Java.type('java.lang.Thread')
# def sleep = Thread.sleep(3000)
* def threadName = Thread.currentThread().getName()
* match threadName contains 'Karate-UI Run'
* print 'current thread is ' + threadName
* string y = 'y'
* string z = 'z'
# def sleep = Thread.sleep(3000)
* string a = 'a'
* print "Scenario-2 Finished"