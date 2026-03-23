Feature: Notification Service

@smoke @notifications
Scenario: Send email notification
* def email = { to: 'user@example.com', subject: 'Welcome!', sent: true }
* match email.sent == true

@regression @notifications
Scenario: Send SMS notification
* def sms = { phone: '+1234567890', message: 'Your code is 1234', delivered: true }
* match sms.delivered == true

@slow @notifications @integration
Scenario: Batch notification processing
* def batch = { total: 100, sent: 100, failed: 0 }
* match batch.sent == batch.total
* print 'all notifications sent'

@demo @notifications
Scenario: Notification template rendering
* def template = { name: 'welcome', variables: ['username', 'date'] }
* match template.variables contains 'username'
