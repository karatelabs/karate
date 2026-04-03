Feature: Multi-feature call chain (V1-style reusable flows)
  Tests that driver auto-propagates across a chain of called features
  without needing scope: 'caller' - the common V1 migration pattern

Background:
* url serverUrl

Scenario: driver from called feature is available in caller and subsequent calls
# Step 1: call login feature (creates driver in callee)
* call read('call-chain-login.feature')
# Step 2: driver should be available here (propagated from login)
* match driver.title == 'Dashboard'
# Step 3: call another feature that uses the same driver
* call read('call-chain-navigate.feature')
# Step 4: driver should still be available after second call
* match driver.title == 'Input Test'

Scenario: caller-created driver is inherited by called features
# Step 1: caller creates driver
* configure driver = driverConfig
* driver serverUrl + '/index.html'
* match driver.title == 'Karate Driver Test'
# Step 2: call sub-feature that uses inherited driver
* call read('call-driver-sub.feature')
# Step 3: driver should still work in caller
* match driver.title == 'Karate Driver Test'
