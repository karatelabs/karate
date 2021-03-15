Feature:

Scenario: set via table, variable and xml nodes will be auto-built
    * set search /acc:getAccountByPhoneNumber
    | path                        | value |
    | acc:phoneNumber             | 1234  |   
    | acc:phoneNumberSearchOption | 'all' |

    * match search ==
    """
    <acc:getAccountByPhoneNumber>
        <acc:phoneNumber>1234</acc:phoneNumber>
        <acc:phoneNumberSearchOption>all</acc:phoneNumberSearchOption>        
    </acc:getAccountByPhoneNumber>
    """
