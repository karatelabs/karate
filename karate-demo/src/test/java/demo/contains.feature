Feature: recursive contains

Background:

* def original =
"""
{
	"person": {
		"name": "Bob",
		"address": {
			"line1": "address",
			"line2": "county",
			"previous_address": {
				"line1": "previous_address",
				"line2": "previous_county"
			}
		},
		"phone": "555-5555"
	}
}
"""

* def missing_level1_obj =
"""
{
	"person": {
		"name": "Bob",
		"phone": "555-5555"
	}
}
"""

* def missing_level2_string =
"""
{
	"person": {
		"name": "Bob",
		"address": {
			"line1": "address",
			"previous_address": {
				"line1": "previous_address",
				"line2": "previous_county"
			}
		},
		"phone": "555-5555"
	}
}
"""

* def missing_level1_string =
"""
{
	"person": {
		"address": {
			"line1": "address",
			"line2": "county",
			"previous_address": {
				"line1": "previous_address",
				"line2": "previous_county"
			}
		},
		"phone": "555-5555"
	}
}
"""

* def just_internal_object =
"""
{
	"name": "Bob",
	"address": {
		"line1": "address",
		"line2": "county",
		"previous_address": {
			"line1": "previous_address",
			"line2": "previous_county"
		}
	},
	"phone": "555-5555"
}
"""

* def too_much_info_level2 =
"""
{
	"person": {
		"name": "Bob",
		"address": {
			"line1": "address",
			"line2": "county",
			"line3": "city",
			"previous_address": {
				"line1": "previous_address",
				"line2": "previous_county"
			}
		},
		"phone": "555-5555"
	}
}
"""

* def too_much_info_level3 =
"""
{
	"person": {
		"name": "Bob",
		"address": {
			"line1": "address",
			"line2": "county",
			"previous_address": {
				"line1": "previous_address",
				"line2": "previous_county",
				"line3": "city"
			}
		},
		"phone": "555-5555"
	}
}
"""

* def missing_level2_obj =
"""
{
	"person": {
		"name": "Bob",
		"address": {
			"line1": "address",
			"line2": "county"
		},
		"phone": "555-5555"
	}
}
"""


* def missing_level3_string =
"""
{
	"person": {
		"name": "Bob",
		"address": {
			"line1": "address",
			"line2": "county",
			"previous_address": {
				"line1": "previous_address"
			}
		},
		"phone": "555-5555"
	}
}
"""


Scenario: should be true for the same object
* match original contains original

Scenario: should be true for missing level 1 object
* match original contains missing_level1_obj

Scenario: should be true for missing level 1 string
* match original contains missing_level1_string

Scenario: should be true for missing level 2 object
* match original contains missing_level2_obj

Scenario: should be true for missing level 2 string
* match original contains missing_level2_string

Scenario: should be true for missing level 3 string
* match original contains missing_level3_string

Scenario: should be false for just the internal data
* match original !contains just_internal_object

Scenario: should be false for too much information at level 2
* match original !contains too_much_info_level2

Scenario: should be false for too much information at level 3
* match original !contains too_much_info_level3
