Feature: windows calculator

Scenario:
* robot { window: 'Form1', fork: 'C:/peter/ui-automation/apps/Project1', highlight: true }
* input('#655972', 'hello')
* screenshot()
