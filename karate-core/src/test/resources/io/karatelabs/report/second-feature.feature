Feature: Payment Processing

@api @critical
Scenario: Process payment successfully
* def payment = { amount: 99.99, currency: 'USD', status: 'completed' }
* match payment.status == 'completed'
* print 'payment processed'

@api @regression
Scenario: Validate payment amount
* def payment = { amount: 150.00, tax: 12.50, total: 162.50 }
* match payment.total == payment.amount + payment.tax

@api @slow @integration
Scenario: Payment gateway integration
* def gateway = { provider: 'stripe', connected: true }
* match gateway.connected == true

@security @critical
Scenario: Payment data is encrypted
* def encrypted = { cardNumber: '****1234', cvv: '***' }
* match encrypted.cardNumber contains '****'
