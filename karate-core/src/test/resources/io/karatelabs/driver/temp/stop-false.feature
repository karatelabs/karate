Feature: verify configure driver stop:false

# Manual smoke test for the stop:false flag (W3C chromedriver path).
# Expected WARN logs in the console:
#   1. At init  -> "configure driver = { stop: false } — bypassing driver pool; ..."
#   2. At exit  -> "driver.stop=false — leaving browser running, ..."
# Expected behavior:
#   - Chrome opens and loads example.com
#   - karate.stop(9000) blocks the JVM; the visible browser proves the driver is up
#   - After you `curl http://localhost:9000` (or open it in a browser tab),
#     the scenario completes — at that point the exit WARN fires and Chrome
#     stays running until the JVM itself exits.

Scenario: chromedriver with stop:false leaves the browser running
  * configure driver = { type: 'chromedriver', stop: false }
  * driver 'https://example.com'
  * waitFor('h1')
  * print 'about to pause — verify chrome window is visible, then curl http://localhost:9000 to proceed'
  * karate.stop(9000)
