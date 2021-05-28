var someName = 'John';
var msg1 = context.render('api/test');
var msg2 = context.render({path: 'api/test.html', variables: {someName: 'Smith'}});
var msg3 = context.render({html: '<div th:text="someName"></div>'});
var msg4 = context.render({html: '<div th:text="someName"></div>', variables: {someName: 'Smith'}});
response.body = {msg1: msg1, msg2: msg2, msg3: msg3, msg4: msg4};
