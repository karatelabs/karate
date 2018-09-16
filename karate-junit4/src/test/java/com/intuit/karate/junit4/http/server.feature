@ignore
Feature:

Scenario: pathMatches('/echo') && methodIs('post')
* def response = request

Scenario: pathMatches('/echo')
* def response = { uri: '#(requestUri)' }

Scenario: pathMatches('/chrome')
* def response =
"""
[ {
   "description": "",
   "devtoolsFrontendUrl": "/devtools/inspector.html?ws=127.0.0.1:9222/devtools/page/E54102F8004590484CC9FF85E2ECFCD0",
   "id": "E54102F8004590484CC9FF85E2ECFCD0",
   "title": "Take Chrome everywhere",
   "type": "page",
   "url": "chrome://welcome/?variant=everywhere",
   "webSocketDebuggerUrl": "ws://127.0.0.1:9222/devtools/page/E54102F8004590484CC9FF85E2ECFCD0"
} ]
"""

