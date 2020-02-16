# Karate Robot

## Desktop Automation Made `Simple.`
> Version 0.9.5 is the first release, and experimental. Please test and contribute if you can !

### Demo Videos
* Clicking the *native* "File Upload" button in a Web Page - [Link](https://twitter.com/ptrthomas/status/1215534821234995200)
* Clicking a button in an iOS Mobile Emulator - [Link](https://twitter.com/ptrthomas/status/1217479362666041344)

### Capabilities
* Cross-Platform: MacOS, Windows, Linux - and should work on others as well via [Java CPP](https://github.com/bytedeco/javacpp)
* Native Mouse Events
* Native Keyboard Events
* Navigation via image detection
* Tightly integrated into Karate

## Using
The `karate-robot` capabilities are not part of the `karate-core`, because they bring in a few extra dependencies.

### Maven
Add this to the `<dependencies>`:

```xml
    <dependency>
        <groupId>com.intuit.karate</groupId>
        <artifactId>karate-robot</artifactId>
        <version>${karate.version}</version>
        <scope>test</scope>
    </dependency> 
```

This may result in a few large JAR files getting downloaded by default because of the [`javacpp-presets`](https://github.com/bytedeco/javacpp-presets) dependency. But you can narrow down to what is sufficient for your OS by [following these instructions](https://github.com/bytedeco/javacpp-presets/wiki/Reducing-the-Number-of-Dependencies).

## `robot`
Karate Robot is designed to only activate when you use the `robot` keyword, and if the `karate-robot` Java / JAR dependency is present in the project classpath.

Here Karate will look for an application window called `Chrome` and will "focus" it so that it becomes the top-most window, and be visible. This will work on Mac, Windows and Linux (X Window System / X11).

```cucumber
* robot { app: 'Chrome' }
```

In development mode, you can switch on a red highlight border around areas that Karate finds via image matching. Note that the `^` prefix means that Karate will look for a window where the name *contains* `Chrome`.

```cucumber
* robot { app: '^Chrome', highlight: true }
```

Note that you can use [`karate.exec()`](https://github.com/intuit/karate#karate-exec) to run a console command to start an application if needed, before "activating" it.

> If you want to do conditional logic depending on the OS, you can use [`karate.os`](https://github.com/intuit/karate#karate-os) - for e.g. `* if (karate.os.type == 'windows') karate.set('filename', 'start.bat')`

The keys that the `robot` keyword supports are the following:

key | description
--- | -----------
`app` | the name of the window to bring to focus, and you can use a `^` prefix to do a string "contains" match
`basePath` | defaults to [`classpath:`](https://github.com/intuit/karate#classpath) which means `src/test/java` if you use the [recommended project structure](https://github.com/intuit/karate#folder-structure)
`highlight` | default `false` if an image match should be highlighted
`highlightDuration` | default `1000` - time to `highlight` in milliseconds
`retryCount` | default `3` number of times Karate will attempt to find an image
`retryInterval` | default `2000` time between retries when finding an image

# API
Please refer to the available methods in [`Robot.java`](src/main/java/com/intuit/karate/robot/Robot.java). Most of them are "chainable".

Here is a sample test:

```cucumber
* robot { app: '^Chrome', highlight: true }
* robot.input(Key.META, 't')
* robot.input('karate dsl' + Key.ENTER)
* robot.click('tams.png')
```

The above flow performs the following operations:
* finds an already open window where the name contains `Chrome`
* enables "highlight" mode for ease of development / troubleshooting
* triggers keyboard events for [COMMAND + t] which will open a new browser tab
* triggers keyboard events for the input "karate dsl" and an ENTER key-press
* waits for a section of the screen defined by [`tams.png`](src/test/java/tams.png) to appear - and clicks in the center of that region

## Images
Images have to be in PNG format, and with the extension `*.png`. Karate will attempt to find images that are smaller or larger to a certain extent. But for the best results, try to save images that are the same resolution as the application under test.

## `Key`
Just [like Karate UI](https://github.com/intuit/karate/tree/master/karate-core#special-keys), the special keys are made available under the namespace `Key`. You can see all the available codes [here](https://github.com/intuit/karate/blob/master/karate-core/src/main/java/com/intuit/karate/driver/Key.java).

```cucumber
* robot.input('karate dsl' + Key.ENTER)
```

## `robot.basePath`
Rarely used since `basePath` would typically be set by the [`robot` options](#robot). But you can do this any time during a test to "switch". Note that [`classpath:`](https://github.com/intuit/karate#classpath) would [typically resolve](https://github.com/intuit/karate#folder-structure) to `src/test/java`.

```cucumber
* robot.basePath = 'classpath:some/package'
```

## `robot.click()`
Defaults to a "left-click", pass 1, 2 or 3 as the argument to specify left, middle or right mouse button.

## `robot.move()`
Argument can be `x, y` co-ordinates or typically the name of an image, which will be looked for in the [`basePath`](#robot). Note that relative paths will work.

## `robot.delay()`
Not recommended unless un-avoidable. Argument is time in milliseconds.

## `robot.input()`
If there are 2 arguments, the first argument is for [modifier keys](#key) such as `Key.CTRL`, `Key.ALT`, etc.

```cucumber
* robot.input(Key.META, 't')
```

Else, you pass a string of text which can include special characters such as a line-feed:

```cucumber
* robot.input('karate dsl' + Key.ENTER)
```
