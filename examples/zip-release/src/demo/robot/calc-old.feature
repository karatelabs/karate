Feature: windows calculator
	depending on your version of windows, if "calc.feature" doesn't work, try this one instead
	also refer docs: https://github.com/intuit/karate/wiki/ZIP-Release#karate-robot 

Scenario:
* robot { window: 'Calculator', fork: 'calc', highlight: true, highlightDuration: 500 }
* click('Clear')
* click('1')
* click('Add')
* click('2')
* click('Equals')
* match locate('//text{3}').name == '3'
* screenshot()
