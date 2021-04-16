Feature:

Scenario Outline:
  * print 'in called:', karate.tags
  * def fromCallSingle = val

  @callme
  Examples:
    | val             |
    | hello world     |

  @dontcallme
  Examples:
    | val             |
    | not hello world |