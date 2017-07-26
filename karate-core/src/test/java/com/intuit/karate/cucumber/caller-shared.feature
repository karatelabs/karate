@ignore
Feature:

Scenario:
# in 'shared scope' mode, it actually does not make make sense to pass arguments 
# other than for readability - the variable 'foo' is going to get set 'globally'
* call read('called-shared.feature') { foo: 'bar' }
