@ignore
Feature:

Scenario Outline:
* def left = <left>
* def right = <right>
* print 'left', left, 'right', right
* match left == right

@foo
Examples:
| left  | right |
|    1  |     1 |
|    2  |     0 |
|    3  |     3 |

@bar
Examples:
| left  | right |
|    4  |     4 |
|    5  |     0 |
|    6  |     0 |
|    7  |     7 |

