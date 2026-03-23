Feature: Keyboard Tests
  Keyboard operations and key presses

  Background:
    * configure driver = driverConfig
    * driver serverUrl + '/input'

  Scenario: Type text
    * focus('#username')
    * keys().type('hello')
    * def val = value('#username')
    * match val == 'hello'

  Scenario: Type with numbers
    * focus('#username')
    * keys().type('user123')
    * def val = value('#username')
    * match val == 'user123'

  Scenario: Type with special characters
    * focus('#email')
    * keys().type('test@example.com')
    * def val = value('#email')
    * match val == 'test@example.com'

  Scenario: Tab key navigation
    * focus('#username')
    * keys().type('user1')
    * keys().press(Key.TAB)
    * keys().type('test@test.com')
    * def emailValue = value('#email')
    * match emailValue == 'test@test.com'

  Scenario: Enter key
    * focus('#username')
    * keys().press(Key.ENTER)

  Scenario: Backspace key
    * focus('#username')
    * keys().type('hello world')
    * keys().press(Key.BACKSPACE)
    * keys().press(Key.BACKSPACE)
    * keys().press(Key.BACKSPACE)
    * keys().press(Key.BACKSPACE)
    * keys().press(Key.BACKSPACE)
    * def val = value('#username')
    * match val == 'hello '

  Scenario: Arrow keys
    * focus('#username')
    * keys().type('abc')
    * keys().press(Key.LEFT)
    * keys().press(Key.LEFT)
    * keys().type('X')
    * def val = value('#username')
    * match val == 'aXbc'

  Scenario: Ctrl+A select all
    * input('#username', 'select me')
    * focus('#username')
    * keys().ctrl('a')
    * keys().type('replaced')
    * def val = value('#username')
    * match val == 'replaced'

  Scenario: Shift+Arrow select text
    * input('#username', 'hello')
    * focus('#username')
    * keys().shift(Key.LEFT)
    * keys().shift(Key.LEFT)
    * keys().type('XY')
    * def val = value('#username')
    * match val == 'helXY'

  Scenario: Alt+key does not type character
    * focus('#username')
    * keys().type('hello')
    * keys().alt('x')
    * def val = value('#username')
    * match val == 'hello'

  Scenario: Home and End keys
    * focus('#username')
    * keys().type('hello')
    * keys().press(Key.HOME)
    * keys().type('X')
    * def val = value('#username')
    * match val == 'Xhello'
    * keys().press(Key.END)
    * keys().type('Y')
    * def val2 = value('#username')
    * match val2 == 'XhelloY'

  Scenario: Delete key (forward delete)
    * focus('#username')
    * keys().type('hello')
    * keys().press(Key.HOME)
    * keys().press(Key.DELETE)
    * def val = value('#username')
    * match val == 'ello'

  Scenario: Escape key does not add text
    * focus('#username')
    * keys().type('hello')
    * keys().press(Key.ESCAPE)
    * def val = value('#username')
    * match val == 'hello'

  Scenario: Chained key operations
    * input('#username', 'test value')
    * focus('#username')
    * keys().ctrl('a').type('new')
    * def val = value('#username')
    * match val == 'new'

  Scenario: Hold Shift while typing for uppercase
    * focus('#username')
    * keys().down(Key.SHIFT)
    * keys().type('hello')
    * keys().up(Key.SHIFT)
    * def val = value('#username')
    * match val == 'HELLO'

  Scenario: Hold Shift then release - mixed case
    * focus('#username')
    * keys().down(Key.SHIFT).type('abc').up(Key.SHIFT).type('def')
    * def val = value('#username')
    * match val == 'ABCdef'

  Scenario: Plus notation - Control+a
    * input('#username', 'select me')
    * focus('#username')
    * keys().press('Control+a')
    * keys().type('replaced')
    * def val = value('#username')
    * match val == 'replaced'

  Scenario: Plus notation - Shift+ArrowLeft
    * input('#username', 'hello')
    * focus('#username')
    * keys().press('Shift+ArrowLeft')
    * keys().press('Shift+ArrowLeft')
    * keys().type('XY')
    * def val = value('#username')
    * match val == 'helXY'

  Scenario: Plus notation - Control+Shift+ArrowLeft
    * input('#username', 'test')
    * focus('#username')
    * keys().press('Control+Shift+ArrowLeft')
    * keys().type('X')
    * def val = value('#username')
    * match val != 'test'

  Scenario: Type shifted punctuation characters
    * focus('#username')
    * keys().type('P@ss!')
    * def val = value('#username')
    * match val == 'P@ss!'

  Scenario: Type all shifted number row characters
    * focus('#username')
    * keys().type('!@#$%^&*()')
    * def val = value('#username')
    * match val == '!@#$%^&*()'
