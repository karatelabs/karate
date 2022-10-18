Feature: windows notepad

  Scenario:
    * robot { window: 'Document - WordPad', fork: 'write', highlight: true }
    * def sWidget = '//document'
    * def sText = 'This is a test'
    * locate(sWidget).click()
    * input(sText + ' with press & release !')
    * match locate(sWidget).value contains sText
    # Sélectionne l'écran et copie le contenu dans le clipboard
    * def oRegion = locate(sWidget).region
    * print 'region:', oRegion
    * move(oRegion.x + 1, oRegion.y + 1).press().move(oRegion.x + 10, oRegion.y + 10).release()
    * input(Key.CONTROL + 'c')
    * print 'clipboard:', robot.clipboard.trim()

