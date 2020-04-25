# Karate Robot

## Desktop Automation Made `Simple.`
> Version 0.9.6.RC2 is available, and experimental. Please test and contribute if you can !

### Demo Videos
* Clicking the *native* "File Upload" button in a Web Page - [Link](https://twitter.com/ptrthomas/status/1253373486384295936)
  * details, code and explanation [here](https://stackoverflow.com/a/61393515/143475)
* Clicking a button in an iOS Mobile Emulator - [Link](https://twitter.com/ptrthomas/status/1217479362666041344)

### Capabilities
* Cross-Platform: MacOS, Windows, Linux - and should work on others as well via [Java CPP](https://github.com/bytedeco/javacpp)
* Available as a standalone binary via the [ZIP Release](https://github.com/intuit/karate/wiki/ZIP-Release#karate-robot)
* Native Mouse Events
* Native Keyboard Events
* Navigation via image detection
* Tightly integrated into Karate

## Examples
* Refer to the [`examples/robot-test`](../examples/robot-test) project which is a stand-alone Maven project that can be used as a starting point
* Opening a browser tab and performing actions - [Link](src/test/java/robot/core/chrome.feature)

## Using
If you are not that experienced with programming - or don't want to set up a Java development environment, please look at the [ZIP Release](https://github.com/intuit/karate/wiki/ZIP-Release#karate-robot) which you can run using [Visual Studio Code](https://github.com/intuit/karate/wiki/IDE-Support#visual-studio-code).

Maven (or Gradle) users can read on below.

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
`basePath` | defaults to `null`, which means the search will be relative to the "entry point" feature file, but can be used to point to [prefixed / relative paths](https://github.com/intuit/karate#reading-files) such as `classpath:some/folder`
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
* finds an already open window where the name contains "Chrome"
  * note that on Windows you may need to use "New Tab" instead
* enables "highlight" mode for ease of development / troubleshooting
* triggers keyboard events for [COMMAND + t] which will open a new browser tab
  * on Windows this should be `Key.CONTROL` instead
* triggers keyboard events for the input "karate dsl" and an ENTER key-press
* waits for a section of the screen defined by [`tams.png`](src/test/java/robot/core/tams.png) to appear - and clicks in the center of that region
  * Karate will try to use different scaling factors for an image match, for best results - try to use images that are the same resolution (or as close) as the desktop resolution
  * if you run into issues, try re-taking a PNG capture of the area to click-on

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

## `robot.press()`
A mouse press that will be held down, useful for simulating a drag and drop operation.

## `robot.release()`
Release mouse button, useful for simulating a drag and drop operation.

## `robot.screenshot()`
Will save a screenshot of the viewport, which will appear in the HTML report. Note that this returns a byte-array of the PNG image.

# Standalone JAR
The `karate-robot` for Windows and Mac OSX is around 100 MB and hence not distributed with the [ZIP Release](https://github.com/intuit/karate/wiki/ZIP-Release). But you can download it separately, and it can be easily added to the classpath. You can find instructions [here](https://github.com/intuit/karate/wiki/ZIP-Release#karate-robot).

## Building
For Linux, Android or iOS, you can build a stand-alone JAR by following the [Developer Guide](https://github.com/intuit/karate/wiki/Developer-Guide#build-standalone-karate-robot-jar).
