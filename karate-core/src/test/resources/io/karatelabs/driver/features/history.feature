Feature: History traversal and navigation edge cases
  Pins the loader-bound page-load waits: every scenario here is a case where a
  document-anonymous "DOM ready" signal used to (or could) complete a wait against
  the wrong document. Deliberately NOT @lock'd — these must hold under pooled
  parallel execution, since that is exactly the condition the loader binding
  hardens against.

  Background:
    * configure driver = driverConfig

  Scenario: back and forward across documents
    * driver serverUrl + '/'
    * match driver.title == 'Karate Driver Test'
    * driver serverUrl + '/navigation'
    * match driver.title == 'Navigation Test'
    * back()
    * match driver.title == 'Karate Driver Test'
    * forward()
    * match driver.title == 'Navigation Test'

  Scenario: back and forward over a pushState history entry
    # A same-document traversal fires NO loader events at all (no frameNavigated,
    # no DOMContentLoaded) — the wait must complete via the URL-verified fallback
    # instead of hanging until the page-load timeout.
    * driver serverUrl + '/navigation'
    * script("history.pushState({}, '', '/virtual/step2')")
    * match driver.url == serverUrl + '/virtual/step2'
    * back()
    * match driver.url == serverUrl + '/navigation'
    # same document throughout — never reloaded
    * match driver.title == 'Navigation Test'
    * forward()
    * match driver.url == serverUrl + '/virtual/step2'

  Scenario: back over a fragment history entry
    * driver serverUrl + '/navigation'
    * script("window.location.hash = 'section'")
    * match driver.url == serverUrl + '/navigation#section'
    * back()
    * match driver.url == serverUrl + '/navigation'
    * match driver.title == 'Navigation Test'

  Scenario: navigation answered with 204 keeps the current document
    # Chrome aborts a no-content navigation (net::ERR_ABORTED on Page.navigate)
    # and the current document stays — the driver must return promptly with the
    # page as-is, not wait for a document that will never commit.
    * driver serverUrl + '/navigation'
    * driver serverUrl + '/no-content'
    * match driver.title == 'Navigation Test'
    * match driver.url == serverUrl + '/navigation'

  Scenario: server redirect chain lands on the final document
    # A 302 keeps the same loader across the chain, so the loader-bound wait
    # completes on the redirect target.
    * driver serverUrl + '/redirect-302'
    * match driver.title == 'Navigation Test'
    * match driver.url == serverUrl + '/navigation'

  Scenario: client-side redirect completes on the first document
    # The page-load wait belongs to the requested document; the redirect the page
    # performs afterwards is crossed with an explicit wait.
    * driver serverUrl + '/client-redirect'
    * waitForUrl(serverUrl + '/navigation')
    * match driver.title == 'Navigation Test'

  Scenario: rapid alternation never observes the previous document
    # The stale-document class: title is read immediately after each navigation,
    # repeatedly, so any wait satisfied by the previous document's signals fails
    # loudly here.
    * driver serverUrl + '/'
    * def hop =
    """
    function() {
      driver.setUrl(serverUrl + '/navigation');
      if (driver.title != 'Navigation Test') karate.fail('stale document after navigation: ' + driver.title);
      driver.setUrl(serverUrl + '/');
      if (driver.title != 'Karate Driver Test') karate.fail('stale document after index: ' + driver.title);
    }
    """
    * karate.repeat(5, hop)
