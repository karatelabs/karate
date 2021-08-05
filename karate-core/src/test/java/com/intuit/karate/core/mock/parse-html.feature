Feature: simple mock

  Scenario: pathMatches('/login') && methodIs('post')
    * def responseStatus = 302
    * def responseHeaders = { 'Location': '../login/' }
    * def response =
      """
      <!DOCTYPE HTML>
      <title>Redirecting...</title>
      <h1>Redirecting...</h1>
      <p>You should be redirected automatically to target URL: <a href="/">/</a>.  If not click the link.
      """

  Scenario: pathMatches('/login') && methodIs('get')
    * def response =
    """
    <!DOCTYPE html>
    <html lang="en-US">
        <head>
            <title>Hello world</title>
            <!-- unquoted attribute conforms to spec https://html.spec.whatwg.org/#unquoted -->
            <script nonce=abc123 type="text/javascript">
            </script>
        </head>
        <body>
         <h1>Hello</h1>
        </body>
    </html>
    """