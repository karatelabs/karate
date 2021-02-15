Feature: karate.extract()

Background:
* def text = karate.readAsString('extract.html')

Scenario: extract first regex
* def token = karate.extract(text, 'login_form_token.+value=\\"([^\\"]+)', 1)
* match token == 'secret1'

Scenario: extract all regexes
* def tokens = karate.extractAll(text, 'login_form.?_token.+value=\\"([^\\"]+)', 1)
* match tokens == ['secret1', 'secret2']
