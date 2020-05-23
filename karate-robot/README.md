# Karate Robot

## Desktop Automation Made `Simple.`
> Version 0.9.6.RC3 is available, and experimental. Please test and contribute if you can !

### Capabilities
* Available as a standalone binary via the [ZIP Release](https://github.com/intuit/karate/wiki/ZIP-Release#karate-robot)
* Native Mouse Events
* Native Keyboard Events
* Navigation via image detection - cross-platform (mac, win, linux) via [Java CPP](https://github.com/bytedeco/javacpp)
* Windows object-recognition using [Microsoft UI Automation](https://docs.microsoft.com/en-us/windows/win32/winauto/entry-uiauto-win32)
* Tightly integrated into Karate - which means a [debugger, HTML reports](https://twitter.com/ptrthomas/status/1261183808985948160), and more

### Demo Videos
* Clicking the *native* "File Upload" button in a Web Page - [Link](https://twitter.com/ptrthomas/status/1253373486384295936)
  * details, code and explanation [here](https://stackoverflow.com/a/61393515/143475)
* Clicking a button in an iOS Mobile Emulator - [Link](https://twitter.com/ptrthomas/status/1217479362666041344)
* Windows automation by natively accessing UI controls and the window / object tree - [Link](https://twitter.com/ptrthomas/status/1261183808985948160)

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
* robot { window: 'Chrome' }
```

In development mode, you can switch on a red highlight border around areas that Karate finds via image matching. Note that the `^` prefix means that Karate will look for a window where the name *contains* `Chrome`.

```cucumber
* robot { window: '^Chrome', highlight: true }
```

You can use `fork` to run a console command to start an application if needed, before "activating" it.

> If you want to do conditional logic depending on the OS, you can use [`karate.os`](https://github.com/intuit/karate#karate-os) - for e.g. `* if (karate.os.type == 'windows') karate.set('filename', 'start.bat')`

### `robot` options

The keys that the `robot` keyword supports are the following:

key | description
--- | -----------
`window` | (optional) the name of the window to bring to focus, and you can use a `^` prefix to do a string "contains" match or `~` for a regular-expression match, also see [`window()`](#window)
`fork` | (optional) calls an OS executable and takes a string (e.g. `'some.exe -h'`), string-array (e.g. `['some.exe', '-h']`) or JSON as per [`karate.fork()`](https://github.com/intuit/karate#karate-fork)
`autoClose` | default `true` - to close the current window if fork was used on startup 
`attach` | defult `true` - if the `window` exists, `fork` will not be executed
`basePath` | defaults to `null`, which means the "find by image" search will be relative to the "entry point" feature file, but can be used to point to [prefixed / relative paths](https://github.com/intuit/karate#reading-files) such as `classpath:some/folder`
`highlight` | default `false` if an element (or image) match should be highlighted
`highlightDuration` | default `3000` - time to `highlight` in milliseconds
`retryCount` | default [normally `3`](https://github.com/intuit/karate#retry-until) - overrides the default [`retry()`](#retry) count, this applies only for finding the `window` *after* a `fork` was executed 
`retryInterval` | default [normally `3000`](https://github.com/intuit/karate#retry-until) - overrides the default [`retry()`](#retry) interval, this applies only for finding the `window` *after* a `fork` was executed 
`autoDelay` | default `0` - time delay added (in milliseconds) after a native action (key press, mouse click), you can set this to a small value e.g. `40` only in case of any issues with keystrokes being too fast, etc

# API
Please refer to the available methods in [`Robot.java`](src/main/java/com/intuit/karate/robot/Robot.java). Most of them are "chainable". The built-in `robot` JS object is where you script UI automation. It will be initialized only after the [`robot`](#robot) keyword has been used to start / attach to a desktop window.

## Element
Any method on the [`Robot`](#api) type that returns [`Element`](src/main/java/com/intuit/karate/robot/Element.java) can be chained for convenience. Here is an example:

```cucumber
* locate('Taxpayer').click(20, 40)
```

This [locates](#windows-locators) a UI control by name, and then within the bounds of that element, proceeds to click the mouse at an inner offset of 20 pixels(horizontal) and 40 pixels (vertical) from the top-left corner of the element.

## Methods
As a convenience, *all* the methods on the `robot` have been injected into the context as special (JavaScript) variables so you can omit the "`robot.`" part and save a lot of typing. For example instead of:

```cucumber
* robot { window: '^Chrome', highlight: true }
* robot.input(Key.META + 't')
* robot.input('karate dsl' + Key.ENTER)
* robot.click('tams.png')
```

You can shorten all that to:

```cucumber
* robot { window: '^Chrome', highlight: true }
* input(Key.META + 't')
* input('karate dsl' + Key.ENTER)
* click('tams.png')
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

Also see [Image Locators](#image-locators)

## `Key`
Just [like Karate UI](https://github.com/intuit/karate/tree/master/karate-core#special-keys), the special keys are made available under the namespace `Key`. You can see all the available codes [here](https://github.com/intuit/karate/blob/master/karate-core/src/main/java/com/intuit/karate/driver/Key.java).

```cucumber
* input('karate dsl' + Key.ENTER)
```

## `robot.basePath`
Rarely used since `basePath` would typically be set by the [`robot` options](#robot). But you can do this any time during a test to "switch". Note that [`classpath:`](https://github.com/intuit/karate#classpath) would [typically resolve](https://github.com/intuit/karate#folder-structure) to `src/test/java`.

```cucumber
* robot.basePath = 'classpath:some/package'
```

## Image Locators
Images have to be in PNG format, and with the extension `*.png`. Karate will attempt to find images that are smaller or larger to a certain extent. But for the best results, try to save images that are the same resolution as the application under test. Also see [`robot.basePath`](#robotbasepath)

So any string that ends with `.png` will be treated as an "image locator". Else read on for OS-native locators.

## Windows Locators
Finding by "name" is the default. Prefixing with a `#` means using the "Automation ID" which may or may not be available depending on the application under test. The "`{}`" and "`{^}`" locator-prefixes are designed to make adding more filter conditions easy, for e.g. to find by "control type" and "class name".

Here are examples:

Locator | Description
------- | -----------
`click('Click Me')` | the first control (any type) where the name is *exactly*: `Click Me`
`click('{^}Click')` | the first control (any type) where the name *contains*: `Click`
`click('^Click')` | short-cut for the above
`click('{~}Click .{2}')` | the first control (any type) where the name matches the regular expression: `Click .{2}`
`click('~Click .{2}')` | short-cut for the above
`click('{button}')` | the first button found when traversing the whole tree from the primary window
`click('{button}Continue')` | the first button found with the text (name) "Continue"
`click('{^button}Continue')` | the first button found with the text (name) *containing* "Continue"
`click('{button:2}')` | the second button
`click('{button.TButton}')` | the first button with a "class name" of "TButton"
`click('{.TButton}')` | the first control (any type) with a "class name" of "TButton"
`click('{^:4}Me')` | the fourth control (any type) where the name *contains*: `Me`

Use a tool like [Inspect.exe](https://docs.microsoft.com/en-us/windows/win32/winauto/inspect-objects) to identify the properties needed for automation from an application window.

The [control "type"](https://docs.microsoft.com/en-us/windows/win32/winauto/uiauto-controltypesoverview) is not case-sensititive. Examples are `edit`, `button` and `checkbox`. The complete list of types can be [found here](src/main/java/com/intuit/karate/robot/win/ControlType.java). You don't have to rely on the `LocalizedControlType` shown in tools such as "Inspect.exe" because Karate uses the `ControlType`.

Similarly, the "class name" is not case-sensitive. This can be useful in some cases, for example in Delphi you can use values such as `TScrollBox` and `TEdit`.

### Calculator Example

[Here is an example](../examples/robot-test/src/test/java/win/calc.feature) that operates the Calculator app on Windows.

```cucumber
Feature: windows calculator

Scenario:
* robot { window: 'Calculator', fork: 'calc' }
* click('Clear')
* click('One')
* click('Plus')
* click('Two')
* click('Equals')
* match locate('#CalculatorResults').name == 'Display is 3'
* screenshot()
* click('Close Calculator')
```

## `retry()`
Please refer to the documentation for the Karate browser-automation syntax for [`retry()`](https://github.com/intuit/karate/tree/master/karate-core#retry). It is the same for Karate Robot.

## `waitFor()`
Convenient to wait for an element. Try to use this only when necessary, for example once a Window loads, all components within it would be immediately accessible without needing to "wait". So you can use a `waitFor()` only for the first element within that window that you need to act upon:

```cucumber
* waitFor('Add New').click()
```

## `waitUntil()`

## `optional()`

## `windowOptional()`

## `exists()`

## `windowExists()`

## `window()`
Sets focus to the window by title, prefix with `^` for a string "contains" match or `~` for a regular-expression match.

## `robot.desktop`
Gets the root of all other Windows objects as an [`Element`](#element) reference. Useful when you want to search within the entire "Desktop".

## `robot.window`
Returns the currently "active" window set after a previous call to [`window()`](#window) or [`windowOptional()`](#windowoptional). This will fail the test if a window has not been activated.

## `robot.focused`
Returns the [`Element`](#element) that currently has "focus" on the screen, no matter where or what type it is.

## `locate()`
Rarely used, but when you want to just instantiate an [`Element`](src/main/java/com/intuit/karate/robot/Element.java) instance, typically when you are writing custom re-usable functions, or using an element as a "waypoint" to access other elements in a large, complex "tree".

```cucumber
* def e = locate('{pane}Some Pane')
# now you can have multiple steps refer to "e"
* e.locate('{edit}').input('foo')
* e.locate('{button}').click()
```
Note that `locate()` will fail the test if the element was not found. Think of it as just like [`waitFor()`](#waitfor) but without the "wait" part.

Also see [`exists()`](#exists) and [`optional()`](#optional).

## `locateAll()`

## `click()`
Defaults to a "left-click", pass 1, 2 or 3 as the argument to specify left, middle or right mouse button.

```cucumber
* click('Continue')
```

You can also click on any X and Y co-ordinate. Note that (0, 0) is the top, left of the screen.

```cucumber
* click(100, 200)
```

## `move()`
Argument can be `x, y` co-ordinates or typically the name of an image, which will be looked for in the [`basePath`](#robot). Note that relative paths will work.

## `delay()`
Not recommended unless un-avoidable. Argument is time in milliseconds.

## `input()`
The single string argument can include special characters such as a line-feed:

```cucumber
* input('karate dsl' + Key.ENTER)
```

If you need to simulate key combinations, just ensure that the [modifier keys](#key) such as `Key.CTRL`, `Key.ALT` are the first in the sequence (they will be auto-released at the end):

```cucumber
* input(Key.META + 't')
```

## `press()`
A mouse press that will be held down, useful for simulating a drag and drop operation.

## `release()`
Release mouse button, useful for simulating a drag and drop operation.

## `screenshot()`
Will save a screenshot of the viewport, which will appear in the HTML report. Note that this returns a byte-array of the PNG image.

# Standalone JAR
The `karate-robot` for Windows and Mac OSX is around 100 MB and hence not distributed with the [ZIP Release](https://github.com/intuit/karate/wiki/ZIP-Release). But you can download it separately, and it can be easily added to the classpath. You can find instructions [here](https://github.com/intuit/karate/wiki/ZIP-Release#karate-robot).

## Building
For Linux, Android or iOS, you can build a stand-alone JAR by following the [Developer Guide](https://github.com/intuit/karate/wiki/Developer-Guide#build-standalone-karate-robot-jar).
