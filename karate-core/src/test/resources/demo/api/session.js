// API endpoint for session operations

var action = request.param('action')

if (action === 'init') {
    // Create new session
    context.init()
    response.body = {
        action: 'init',
        sessionId: context.sessionId
    }
}

else if (action === 'close') {
    // Close/invalidate session
    context.close()
    response.body = {
        action: 'close',
        closed: true
    }
}

else if (action === 'set') {
    // Set a session value
    var key = request.param('key')
    var value = request.param('value')
    if (session && key) {
        session[key] = value
        response.body = {
            action: 'set',
            key: key,
            value: value
        }
    } else {
        response.status = 400
        response.body = { error: 'No session or missing key' }
    }
}

else if (action === 'get') {
    // Get a session value
    var key = request.param('key')
    if (session && key) {
        response.body = {
            action: 'get',
            key: key,
            value: session[key]
        }
    } else {
        response.status = 400
        response.body = { error: 'No session or missing key' }
    }
}

else if (action === 'info') {
    // Get session info
    response.body = {
        hasSession: !!session,
        sessionId: context.sessionId,
        closed: context.closed
    }
}

else if (action === 'redirect') {
    // Test redirect
    var target = request.param('target') || '/'
    context.redirect(target)
}

else if (action === 'uuid') {
    // Generate UUID
    response.body = {
        uuid: context.uuid()
    }
}

else if (action === 'log') {
    // Test logging
    var message = request.param('message') || 'test log'
    context.log('Session API log:', message)
    response.body = { logged: true, message: message }
}

else {
    // Default: return session info
    response.body = {
        hasSession: !!session,
        sessionId: context.sessionId,
        data: session ? session.toMap() : null
    }
}
