# Process Builder

Karate v2 provides a modern process execution system for running external commands, managing background processes, and testing CLI applications.

## Overview

The process API provides:
- **Synchronous execution** via `karate.exec()` - run commands and get output
- **Asynchronous execution** via `karate.fork()` - spawn background processes with line listeners
- **Signal integration** via `karate.signal()` - communicate results from forked processes

## Quick Start

```javascript
// Simple command execution
var output = karate.exec('ls -la')

// Run with options
var output = karate.exec({ line: 'cat file.txt', workingDir: '/tmp' })

// Background process with line listener
var proc = karate.fork({
  args: ['node', 'server.js'],
  listener: function(line) {
    if (line.contains('listening')) {
      karate.log('Server ready!')
    }
  }
})
proc.waitSync()
```

## API Reference

### karate.exec(command)

Executes a command synchronously and returns stdout as a string.

**Arguments:**
- `command` - String, Array, or Map

**Returns:** String (stdout output)

```javascript
// String command (tokenized automatically)
var out = karate.exec('echo hello world')

// Array of arguments (no tokenization)
var out = karate.exec(['echo', 'hello world'])

// Map with options
var out = karate.exec({
  line: 'npm test',
  workingDir: '/project',
  env: { NODE_ENV: 'test' },
  timeout: 30000
})
```

### karate.fork(options)

Spawns an asynchronous process and returns a ProcessHandle.

**Options:**
| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `line` | String | - | Command line (tokenized) |
| `args` | Array | - | Command arguments (alternative to `line`) |
| `workingDir` | String | CWD | Working directory |
| `env` | Map | - | Environment variables |
| `useShell` | Boolean | false | Wrap in shell (sh -c or cmd /c) |
| `redirectErrorStream` | Boolean | true | Merge stderr into stdout |
| `timeout` | Number | - | Timeout in milliseconds |
| `listener` | Function | - | Stdout line listener (receives line string) |
| `errorListener` | Function | - | Stderr line listener (when redirectErrorStream=false) |
| `logToContext` | Boolean | false | Log output to test context |
| `start` | Boolean | true | Start immediately (false for deferred) |

**Returns:** ProcessHandle object

```javascript
// Basic fork
var proc = karate.fork({ args: ['sleep', '5'] })

// With line listener (receives line string directly)
var proc = karate.fork({
  line: 'npm start',
  workingDir: '/app',
  listener: function(line) {
    karate.log('OUTPUT: ' + line)
  }
})

// Separate stdout and stderr listeners
var proc = karate.fork({
  args: ['node', 'app.js'],
  redirectErrorStream: false,
  listener: function(line) { karate.log('stdout: ' + line) },
  errorListener: function(line) { karate.log('stderr: ' + line) }
})

// Deferred start (set up listeners first)
var proc = karate.fork({ args: ['node', 'app.js'], start: false })
proc.onStdOut(function(line) { karate.log(line) })
proc.start()
```

### ProcessHandle

The object returned by `karate.fork()`.

**Properties:**
| Property | Type | Description |
|----------|------|-------------|
| `stdOut` | String | Collected stdout |
| `stdErr` | String | Collected stderr |
| `exitCode` | Number | Exit code (-1 if still running) |
| `alive` | Boolean | Whether process is running |
| `pid` | Number | Process ID |

**Methods:**

#### waitSync([timeout])
Blocks until process exits. Optional timeout in milliseconds.
```javascript
proc.waitSync()        // Wait indefinitely
proc.waitSync(5000)    // Wait up to 5 seconds
```

#### waitForOutput(predicate, [timeout])
Waits until predicate returns true for an output line.
```javascript
// Wait for specific log output
var line = proc.waitForOutput(function(line) {
  return line.contains('ready')
}, 10000)
karate.log('Found: ' + line)
```

#### waitForPort(host, port, [attempts], [interval])
Waits for a TCP port to become available.
```javascript
proc.waitForPort('localhost', 8080, 30, 250)
```

#### waitForHttp(url, [attempts], [interval])
Waits for an HTTP endpoint to respond with 2xx.
```javascript
proc.waitForHttp('http://localhost:8080/health', 30, 1000)
```

#### onStdOut(listener)
Adds a stdout listener. Can be called before or after start().
When `redirectErrorStream: true` (default), receives both stdout and stderr.
```javascript
proc.onStdOut(function(line) {
  karate.log('stdout: ' + line)
})
```

#### onStdErr(listener)
Adds a stderr listener. Only receives lines when `redirectErrorStream: false`.
```javascript
proc.onStdErr(function(line) {
  karate.log('stderr: ' + line)
})
```

#### start()
Starts a deferred process (when `start: false` was used).
```javascript
var proc = karate.fork({ args: ['node', 'app.js'], start: false })
proc.onOutput(myListener)
proc.start()
```

#### close([force])
Terminates the process. Pass `true` to force kill.
```javascript
proc.close()       // Graceful termination
proc.close(true)   // Force kill
```

#### signal(result)
Sends a result to the signal consumer (for listen integration).
```javascript
proc.signal({ status: 'ready', port: 8080 })
```

### karate.signal(result)

Triggers the listen result from JavaScript. Used to communicate from a forked process listener back to the test flow.

```cucumber
* def proc = karate.fork({
    args: ['node', 'server.js'],
    listener: function(line) {
      if (line.contains('listening on port')) {
        var port = parseInt(line.match(/port (\d+)/)[1])
        karate.signal({ port: port })
      }
    }
  })

# In another scenario or after fork
* def result = listen 5000
* match result.port == 8080
```

## Usage Patterns

### Testing CLI Applications

```cucumber
Scenario: test CLI help command
  * def output = karate.exec('./my-cli --help')
  * match output contains 'Usage:'
  * match output contains '--version'

Scenario: test CLI with arguments
  * def output = karate.exec({
      args: ['./my-cli', 'process', '--input', 'data.json'],
      workingDir: testDir
    })
  * match output contains 'Processed 10 records'
```

### Starting Background Servers

```cucumber
Background:
  * def startServer =
    """
    function() {
      var proc = karate.fork({
        args: ['java', '-jar', 'server.jar'],
        workingDir: serverDir,
        listener: function(line) {
          if (line.contains('Started on port')) {
            karate.signal({ ready: true })
          }
        }
      })
      return proc
    }
    """

Scenario: test with background server
  * def server = startServer()
  * def result = listen 30000
  * match result.ready == true

  # Run tests against server
  Given url 'http://localhost:8080'
  When method GET
  Then status 200

  # Cleanup
  * server.close()
```

### Waiting for Specific Output

```cucumber
Scenario: wait for application ready
  * def proc = karate.fork({ args: ['./start-app.sh'] })

  # Wait for ready message (returns the matching line)
  * def line = proc.waitForOutput(function(line) {
      return line.contains('Application started')
    }, 30000)

  * karate.log('App started, message: ' + line)
  * proc.close()
```

### Port Waiting

```cucumber
Scenario: start server and wait for port
  * def proc = karate.fork({
      args: ['node', 'server.js'],
      env: { PORT: '9000' }
    })

  # Wait for port to be available
  * def ready = proc.waitForPort('localhost', 9000, 30, 250)
  * match ready == true

  # Test the server
  Given url 'http://localhost:9000'
  When method GET
  Then status 200

  * proc.close()
```

### Environment Variables

```cucumber
Scenario: run with custom environment
  * def output = karate.exec({
      line: 'printenv MY_VAR',
      env: { MY_VAR: 'hello-world' },
      useShell: true
    })
  * match output contains 'hello-world'
```

### Shell Features (Pipes, Redirects)

When you need shell features like pipes or redirects, use `useShell: true`:

```cucumber
Scenario: use shell pipes
  * def output = karate.exec({
      line: 'cat /etc/passwd | grep root | wc -l',
      useShell: true
    })
  * match output.trim() == '1'
```

### Separate Stdout and Stderr

By default, stderr is merged into stdout (`redirectErrorStream: true`). To handle them separately:

```cucumber
Scenario: capture stderr separately
  * def stdoutLines = []
  * def stderrLines = []
  * def proc = karate.fork({
      args: ['sh', '-c', 'echo out; echo err >&2'],
      redirectErrorStream: false,
      listener: function(line) { stdoutLines.push(line) },
      errorListener: function(line) { stderrLines.push(line) }
    })
  * proc.waitSync()
  * match stdoutLines[0] == 'out'
  * match stderrLines[0] == 'err'
```

## Design Notes

### Threading Model

- Process I/O uses virtual threads for efficient resource usage
- Output is buffered thread-safely for access via `stdOut`/`stdErr`
- Line listeners are called synchronously from reader threads
- `CompletableFuture` used for async exit handling

### Shell Wrapping

By default, commands execute directly without a shell wrapper. This provides:
- Predictable argument handling
- Cross-platform consistency
- No shell injection risks

Use `useShell: true` when you need shell features (pipes, redirects, glob expansion).

### Logging Integration

Set `logToContext: true` to capture process output in test logs:

```cucumber
* def proc = karate.fork({
    args: ['npm', 'test'],
    logToContext: true
  })
```

This integrates with Karate's LogContext for test output reporting.

### Output Capture Reliability

Process output capture waits for stream readers to complete after process exit, ensuring all stdout/stderr is captured before `waitSync()` returns. This prevents race conditions with fast-exiting processes like `echo`.
