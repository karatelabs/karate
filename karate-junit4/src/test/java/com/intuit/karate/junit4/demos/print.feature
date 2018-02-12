Feature: test variations of the print keyword

Background:
* def Logger = Java.type('com.intuit.karate.junit4.demos.TestLogAppender')
* def logger = new Logger()

Scenario: single literal
* print 'foo'
* def test = logger.collect()
* match test == '[print] foo\n'

Scenario: multi literal
* print 'foo', 'bar'
* def test = logger.collect()
* match test == '[print] foo bar\n'

Scenario: single expression
* def foo = 'bar'
* print foo
* def test = logger.collect()
* match test == '[print] bar\n'

Scenario: multi expression
* def foo = 'bar'
* print foo, 1
* def test = logger.collect()
* match test == '[print] bar 1\n'

Scenario: json multi expression
* def foo = { bar: 1 }
* print foo, foo.bar
* def test = logger.collect()
* match test == '[print] {\n  "bar": 1\n}\n 1\n'

Scenario: xml expression
* def foo = <bar><baz>1</baz></bar>
* print foo
* def test = logger.collect()
* match test == '[print] <bar>\n  <baz>1</baz>\n</bar>\n\n'

Scenario: typical json use case
* def foo = { bar: 1 }
* print 'the value of foo is:', foo
* def test = logger.collect()
* match test == '[print] the value of foo is: {\n  "bar": 1\n}\n\n'

Scenario: typical pretty json use case
* def foo = { bar: 1 }
* print 'the value of foo is:\n' + karate.pretty(foo)
* def test = logger.collect()
* match test == '[print] the value of foo is:\n{\n  "bar": 1\n}\n\n'

Scenario: troublesome commas
* def foo = { bar: 1 }
* print 'the value, of foo, is:', foo
* def test = logger.collect()
* match test == '[print] the value, of foo, is: {\n  "bar": 1\n}\n\n'

Scenario: forward slash in json
* def foo = { bar: 'http://localhost:8080' }
* print 'the value, of foo, is:', foo
* def test = logger.collect()
* match test == '[print] the value, of foo, is: {\n  "bar": "http://localhost:8080"\n}\n\n'