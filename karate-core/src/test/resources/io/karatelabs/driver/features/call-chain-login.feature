@ignore
Feature: Login step (reusable called feature)

Scenario: login
# Initializes driver and navigates to login page
* configure driver = driverConfig
* driver serverUrl + '/login.html'
* input('#username', 'admin')
* input('#password', 'admin123')
* click('#login-btn')
# Login redirects to dashboard
* waitForUrl('dashboard')
* match driver.title == 'Dashboard'
