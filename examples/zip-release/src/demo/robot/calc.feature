Feature: windows calculator
	did you know that Karate can do Windows desktop / app automation ?
	you are almost set - just download one more JAR file
	instructions here: https://github.com/intuit/karate/wiki/ZIP-Release#karate-robot

Scenario:
* robot { window: 'Calculator', fork: 'calc', highlight: true, highlightDuration: 500 }
* click('Clear')
* click('One')
* click('Plus')
* click('Two')
* click('Equals')
* match locate('#CalculatorResults').name == 'Display is 3'
* screenshot()
* click('Close Calculator')