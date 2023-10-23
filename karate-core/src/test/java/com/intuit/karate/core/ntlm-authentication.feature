Feature: ntlm authentication

  Scenario: various ways to configure ntlm authentication
    * configure ntlmAuth = { username: 'admin', password: 'secret', domain: 'my.domain', workstation: 'my-pc' }
    * configure ntlmAuth = { username: 'admin', password: 'secret' }
    * configure ntlmAuth = null
    * eval
    """
    karate.configure('ntlmAuth', { username: 'admin', password: 'secret' })
    """
