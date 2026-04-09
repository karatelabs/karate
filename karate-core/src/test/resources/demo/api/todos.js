// API endpoint for todos CRUD with path-based routing
// Tests sub-path handling: /api/todos/{id}

session || context.init();

session.todos = session.todos || []

if (request.pathMatches('/{resource}/{id}')) {
    var id = request.pathParams.id
    var index = -1
    for (var i = 0; i < session.todos.length; i++) {
        if (session.todos[i].id === id) {
            index = i
            break
        }
    }
    if (index === -1) {
        response.status = 404
        response.body = { error: 'Not found', id: id }
    } else if (request.get) {
        response.body = session.todos[index]
    } else if (request.put) {
        var todo = request.body
        todo.id = id
        session.todos[index] = todo
        response.body = todo
    } else if (request.delete) {
        session.todos.splice(index, 1)
        response.body = { deleted: true }
    }
} else if (request.post) {
    var todo = request.body
    todo.id = context.uuid()
    session.todos.push(todo)
    response.body = todo
    response.status = 201
} else {
    response.body = session.todos
}
