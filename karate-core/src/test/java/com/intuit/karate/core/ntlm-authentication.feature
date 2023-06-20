Feature: ntlm authentication

  Scenario: various ways to configure ntlm authentication
    * configure ntlmAuthentication = { username: 'admin', password: 'secret', domain: 'my.domain', workstation: 'ws' }
    * configure ntlmAuthentication = { username: 'admin', password: 'secret' }
    * configure ntlmAuthentication = null
    * eval
    """
    karate.configure('ntlmAuthentication', { username: 'admin', password: 'secret' })
    """
