Feature:

Scenario Outline: ${driverType}
* call read('00.feature')

Examples:
| driverType   | dimensions!                                |
| chrome       | { x: 0, y: 0, width: 300, height: 800 }    |
| chromedriver | { x: 300, y: 0, width: 250, height: 800 }  |
| geckodriver  | { x: 600, y: 0, width: 300, height: 800 }  |
| safaridriver | { x: 1000, y: 0, width: 400, height: 800 } |
