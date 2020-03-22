Feature:

Scenario:
* configure driver = { type: 'chrome', showDriverLog: true }
* driver 'https://www.seleniumeasy.com/test/drag-and-drop-demo.html'
* script("var myDragEvent = new Event('dragstart'); myDragEvent.dataTransfer = new DataTransfer()")
* waitFor('{}Draggable 1').script("_.dispatchEvent(myDragEvent)")
* script("var myDropEvent = new Event('drop'); myDropEvent.dataTransfer = myDragEvent.dataTransfer")
* script('#mydropzone', "_.dispatchEvent(myDropEvent)")
* screenshot()
