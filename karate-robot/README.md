# Karate Robot

## Desktop Automation Made `Simple.`

# Index

<table>
<tr>
  <th>Start</th>
  <td>
      <a href="https://github.com/intuit/karate/wiki/ZIP-Release">ZIP Release</a>
    | <a href="#maven">Maven</a>
    | <a href="https://github.com/intuit/karate/wiki/Karate-Robot-Windows-Install-Guide">Windows Install Guide</a>
    | <a href="#debugging">Debugging</a>
    | <a href="https://github.com/intuit/karate#index">Karate - Main Index</a>
  </td>
</tr>
<tr>
  <th>Config</th>
  <td>
      <a href="#robot"><code>driver</code></a>
    | <a href="#robot-options"><code>robot</code> options</a>
  </td>
</tr>
<tr>
  <th>Concepts</th>
  <td>
      <a href="#methods">Methods</a>
    | <a href="#element-api"><code>Element</code></a>
    | <a href="#window-api"><code>Window</code></a>
    | <a href="#finding-windows">Finding Windows</a>
    | <a href="https://github.com/intuit/karate/wiki/Karate-Robot-Windows-Install-Guide#debug-mode">Debugging</a>
    | <a href="#retry">Retries</a>
    | <a href="#karatefork"><code>karate.fork()</code></a>
    | <a href="#utility-functions">Utility Functions</a>
    | <a href="#conditional-start">Conditional Start</a>
  </td>
</tr>
<tr>
  <th>Locators</th>
  <td>
      <a href="#windows-locators">Windows Locators</a>
    | <a href="#image-locators">Image Locators</a> 
    | <a href="#ocr-locators">OCR Locators</a>
  </td>
</tr>
<tr>
  <th>App</th>
  <td>
      <a href="#window"><code>window()</code></a>    
    | <a href="#windowexists"><code>windowExists()</code></a> 
    | <a href="#windowoptional"><code>windowOptional()</code></a> 
    | <a href="#waitforwindowoptional"><code>waitForWindowOptional()</code></a> 
    | <a href="#robotroot"><code>robot.root</code></a> 
    | <a href="#robotactive"><code>robot.active</code></a>   
    | <a href="#robotfocused"><code>robot.focused</code></a>
    | <a href="#robotlocation"><code>robot.location</code></a>
    | <a href="#robotregion"><code>robot.region()</code></a>
    | <a href="#robotclipboard"><code>robot.clipboard</code></a>
    | <a href="#robotallwindows"><code>robot.allWindows</code></a>
    | <a href="#screenshot"><code>screenshot()</code></a>  
    | <a href="#screenshotactive"><code>screenshotActive()</code></a>    
  </td>
</tr>
<tr>
  <th>Actions</th>
  <td>
      <a href="#click"><code>click()</code></a>
    | <a href="#doubleclick"><code>doubleClick()</code></a>
    | <a href="#rightclick"><code>rightClick()</code></a>
    | <a href="#move"><code>move()</code></a>
    | <a href="#press"><code>press()</code></a>
    | <a href="#release"><code>release()</code></a>
    | <a href="#input"><code>input()</code></a>
    | <a href="#focus"><code>focus()</code></a>
    | <a href="#select"><code>select()</code></a>
    | <a href="#highlight"><code>highlight()</code></a>
    | <a href="#highlightall"><code>highlightAll()</code></a>
  </td>
</tr>
<tr>
  <th>State</th>
  <td>
      <a href="#exists"><code>exists()</code></a>
    | <a href="#optional"><code>optional()</code></a> 
    | <a href="#waitforoptional"><code>waitForOptional()</code></a>    
    | <a href="#locate"><code>locate()</code></a>    
    | <a href="#locateall"><code>locateAll()</code></a>
  </td>
</tr>
<tr>
  <th>Retry / Wait</th>
  <td>
      <a href="#retry"><code>retry()</code></a>
    | <a href="#waitfor"><code>waitFor()</code></a>
    | <a href="#waituntil"><code>waitUntil()</code></a>    
    | <a href="#delay"><code>delay()</code></a>
  </td>
</tr>
</table>

## Capabilities
* Available as a standalone binary via the [ZIP Release](https://github.com/intuit/karate/wiki/ZIP-Release#karate-robot)
* Native Mouse Events
* Native Keyboard Events
* Windows object-recognition using [Microsoft UI Automation](https://docs.microsoft.com/en-us/windows/win32/winauto/entry-uiauto-win32)
* [Navigation via image detection](#image-locators) - cross-platform (mac, win, linux) via [JavaCPP and OpenCV](https://github.com/bytedeco/javacpp-presets/tree/master/opencv)
* [OCR driven navigation](#ocr-locators) and text extraction - cross-platform (mac, win, linux) via [JavaCPP and Tesseract](https://github.com/bytedeco/javacpp-presets/tree/master/tesseract)
* Tightly integrated into [Karate](https://github.com/intuit/karate) - which means a [debugger, HTML reports](#debugging), and more

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

Maven (or Gradle) users can read on below. Make sure you follow the [Karate conventions](https://github.com/intuit/karate#folder-structure) and you can use the [`examples/robot-test`](../examples/robot-test) project as a template.

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

## Debugging
This is one of the highlights of Karate's capabilities. You can see a video of it in action [here](https://twitter.com/ptrthomas/status/1261183808985948160).

Refer to the documentation on how to set it up and use it: [Karate Robot Windows Install Guide](https://github.com/intuit/karate/wiki/Karate-Robot-Windows-Install-Guide#install-visual-studio-code).

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

You can use `fork` to run a console command to start an application if needed, before "activating" it. Also see [`karate.fork()`](#karatefork)

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
`autoDelay` | default `0` - time delay added (in milliseconds) after a native action (key press, mouse click), you can set this to a small value e.g. `40` only in case of any issues with OS actions being too fast, etc
`tessData` | default `tessdata` - the path to a directory where the Tesseract (OCR engine) [data files](#ocr-locators) will be looked for, this is needed only if you use an [OCR Locator](#ocr-locators) or attempt to call [`Element.extract()`](#elementextract). Note that the default *value* "`tessdata`" is all lower-case.
`tessLang` | default `eng` - the default OCR language to use, see [OCR Locator](#ocr-locators)

### `configure robot`
For convenience, the same pattern in [Karate UI](https://github.com/intuit/karate/tree/master/karate-core#configure-driver) is supported, where you can have a "central" config, perhaps set-up in [`karate-config.js`](https://github.com/intuit/karate#configuration) - and have your tests specify the "intent" (or even over-ride "global" config) more clearly:

```cucumber
* configure robot = { highlight: true }
# and then later
* robot { window: '^My App' }
# or even
* robot '^My App'
```

### `karate.fork()`
The `fork` option simply calls [`karate.fork()`](https://github.com/intuit/karate#karate-fork) which means that you can use it directly within a test any time you want to start any OS process. This is convenient to implement conditional logic, for e.g. to start an application involving a *different* main window - if a certain window [does not exist](#windowexists).

Here's an example using [`karate.call()`](https://github.com/intuit/karate#call-vs-read):

```cucumber
* robot { highlight: true, highlightDuration: 500, autoClose: false }
* if (!windowExists('^Main Window')) karate.call('sign-in.feature')
```

And `sign-in.feature` looks like this. This example code below also showcases a few Karate capabilities extremely relevant for testing GUI-s such as [`retry()`](#retry) and [`waitFor()`](#waitfor).

```cucumber
@ignore
Feature:

Scenario:
* karate.fork('C:/MyDir/my.exe')
* retry(5).window('Sign In')
* waitFor('#userid').input(testUser)
* input('#password', testPassword)
* click('#submit-btn')
```

Also see [Conditional Start](#conditional-start) which is a more advanced version of the above flow, when the "Sign In" window title is different.

Note how you can [inject variables from global config](https://github.com/intuit/karate#karate-configjs) e.g. `testUser` and `testPassword` using Karate.

### Finding Windows
Finding Windows and dialogs is a critical aspect of UI automation and Karate makes easy the process of handling even dynamic Window titles or un-predictable Windows.

Here's a typical situation with some challenges, and the script that solves them:

* if the app is already running, don't start it
* the window title is un-predictable, it can be "MyApp" or "MYAPP"
* the app takes almost 20 seconds to start
* after the application starts, a modal dialog with the title "Tips on Startup" may or may not appear

```cucumber
* def windowName = '~MyApp|MYAPP'
* robot { window: '#(windowName)', fork: 'C:/Program Files (x86)/MyApp/myapp.exe', retryCount: 10 }
* windowOptional('Tips on Startup').locate('Close').click()
* window(windowName)
```

Explanation:
* `robot { window: '<name>' }` will not call [`fork`](#karatefork) if the window was found to be already present
* the `~` prefix means that Karate will use a regex (regular expression) match to find the window by title
* `retryCount: 10` means that if `fork` was executed, Karate will wait `10 x 3000` milliseconds where `3000` is the default [`retryDuration`](#retry)
* [`windowOptional()`](#windowoptional) will do nothing if the window does not exist
* note how the variable `windowName` can be used as an [embedded expression](https://github.com/intuit/karate#embedded-expressions) or directly when within "round brackets", e.g. [`window(windowName)`](#window)
* the last line makes sure that we switch back to the main window and make it ["active"](#robotactive)

# API
Please refer to the available methods in [`Robot.java`](src/main/java/com/intuit/karate/robot/Robot.java). Most of them are "chainable". The built-in `robot` JS object is where you script UI automation. It will be initialized only after the [`robot`](#robot) keyword has been used to start / attach to a desktop window.

## `Element` API
Any method on the [`Robot`](#api) type that returns [`Element`](src/main/java/com/intuit/karate/robot/Element.java) can be chained for convenience. Here is an example:

```cucumber
* locate('Taxpayer').click(20, 40)
```

This [locates](#windows-locators) a UI control by name, and then within the bounds of that element, proceeds to click the mouse at an inner offset of 20 pixels(horizontal) and 40 pixels (vertical) from the top-left corner of the element.

Also see [`windowOptional()`](#windowoptional) for a good example of chaining a [`click()`](#click) after calling [`locate()`](#locate).

### Tree Walking
The following *properties* (Java getters) are available on an [`Element`](#element-api) instance:

* `parent`
* `children` (returns a list / array of `Element`-s)
* `firstChild`
* `lastChild`
* `nextSibling`
* `previousSibling`

This is convenient in some cases, for example:

```cucumber
* locate('SomeName').parent.click('Close')
* waitFor('//pane{Info}').children[3].click()

```

## `Window` API
A call to [`window()`](#window) will set the current or "active" window and also return an object of type [`Window`](src/main/java/com/intuit/karate/robot/Window.java) (which extends [`Element`](#element-api)). So to set the window and `restore()` it in one step you could do this:

```cucumber
* window('^Tax Organizer').restore()
```

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

```cucumber
* click('someimage.png')
```

So any string that ends with `.png` will be treated as an "image locator".

### Image Match Strictness
You can optionally prefix a number and `:` to the image path like this:

```cucumber
* click('5:someimage.png')
```

This number is a "strictness" factor, 1 for being the most strict and 10 (the default) for "normal". As of now, consider this experimental while we try to arrive at the values that will work for most real-life situations.

In case you find it really hard to get a "match", you can try providing values greater than 10 which means Karate will look for more "lenient" matches.

Tip: use the [debugger](#debugging) and [`highlight()`](#highlight) or [`highlightAll()`](#highlightall) to troubleshoot image matching.

## OCR Locators
Any string that starts with the `{lang}` pattern will be treated as an OCR locator. 

Karate uses the [Tesseract](https://tesseract-ocr.github.io) OCR engine (v4.X). You will need to acquire [data files](https://tesseract-ocr.github.io/tessdoc/Data-Files.html) for the language of your choice, e.g. English (`eng`). You can choose between the options "tessdata", "tessdata-fast" and "tessdata-best" depending on the quality vs speed (and data-file size) compromise you are willing to make. So for example here is the English data file for "tessdata-best": [link](https://github.com/tesseract-ocr/tessdata_best/blob/master/eng.traineddata). You can download it and make it available in a directory called "tessdata" in the root directory of the project you are working in. To change the "tessdata" location, look at the `tessData` [configuration option](#robot-options).

So to find the text "Click Me" and click on it:

```cucumber
* click('{eng}Click Me')
```

A variation is that if the language-key is prefixed with a `-`, the screen or element-region capture will be converted to a "negative" before OCR processing. This is useful in cases the text is in a light font against a dark background.

```
* click('{-eng}Dark Mode')
```

You can omit the language in which case the `tessLang` [configuration option](#robot-options) will be used:

```
* click('{}Some Text')
```

### `Element.extract()`
The [`Element`](#element-api) has an `extract()` method which can scrape out the text via OCR from the bounds of an Element position on the screen. Results may vary and include line-breaks and white-space, but you may be able to pull-off some string-contains comparisons:

```cucumber
* match locate('Some Pane').extract('eng') contains 'Search Results'
```

If you don't pass the language-key to the `extract()` method like you see `eng` above, the default `tessLang` [configured](#robot-options) will be used:

```cucumber
* def text = locate('Some Pane').extract()
```

To extract the text from the whole screen (desktop), you can do this via the [`robot.root`](#robotroot) API:

```cucumber
* def text = robot.root.extract()
```

### `Element.debugExtract()`
For debugging and troubleshooting, there is an [`Element.debugExtract()`](#element-api) API. This will highlight all the words found within the given `Element`. This is super-useful during a [step-through debugger session](#debugging).

## Windows Locators
Prefixing with a `#` means using the "Automation ID" which may or may not be available depending on the application under test. And finding by "name" is the default, if the first character is not `/` or `#`. As a convenience, you can use the `^` prefix for a name "contains" match and `~` for a name regular-expression match.

But the most useful locator strategy is an XPath-like one. While it does not support all the extensions and functions in proper XPath, it is designed to make selecting elements super-easy and for improved performance, you can "scope" to parent element / paths and make these selectors robust.

Here are examples:

Locator | Description
------- | -----------
`click('Click Me')` | the first control (any type) where the name is *exactly*: "Click Me"
`click('//*{Click Me}')` | the "long-form" of the above. Try to use more specific path-selectors for better performance.
`click('^Click')` | the first control (any type) where the name *contains*: "Click"
`click('//*{^Click}')` | the "long-form" of the above. Try to use more specific path-selectors for better performance.
`click('//button{Click Me}')` | the first button where the name is equal to "Click Me"
`click('/pane[2]/button')` | absolute path, the second pane on the active window, and the first button on it
`click('//pane/*/button')` | other examples of what you can use, the `*` will match any control type
`click('//button.TButton{^Click}')` | the first button with a "class name" of "TButton" and the name contains "Click"
`click('//.TButton/{^Click}')` | a different example, so you can use only a "class name" or element name, note the position of the `/`

Use a tool like [Inspect.exe](https://docs.microsoft.com/en-us/windows/win32/winauto/inspect-objects) to identify the properties needed for automation from an application window.

### Root Scope
By default, all the locators above would be from the currently [active](#robotactive) Window or Element, but you can force the search from the Desktop onwards like this:

```cucumber
* def allPopUps = locateAll('/root//window')
```

This is of course extremely useful in some situations.

### Control Type
The [control "type"](https://docs.microsoft.com/en-us/windows/win32/winauto/uiauto-controltypesoverview) is case-insensitive. Examples are `edit`, `button` and `checkbox`. The complete list of types can be [found here](src/main/java/com/intuit/karate/robot/win/ControlType.java). You don't have to rely on the `LocalizedControlType` shown in tools such as "Inspect.exe" because Karate uses the `ControlType`.

Similarly, the "class name" is not case-sensitive. This can be useful in some cases, for example in Delphi you can use values such as `TScrollBox` and `TEdit`.

Also see [`locateAll()`](#locateall) for ways to find the n-th control on a page that matches a locator and do something with it.

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
Wait for the [JS function](https://github.com/intuit/karate#javascript-functions) to evaluate to `true`. Will poll using the [retry()](#retry) settings configured.

```cucumber
* def fun = function(){ return optional('Close').enabled }
* waitUntil(fun)
```

This gives you a lot of flexibility. Note that Karate can call OS commands using [`karate.exec()`](https://github.com/intuit/karate#karate-exec) or even make [HTTP API requests](https://github.com/intuit/karate#core-keywords). You can even [call Java code](https://github.com/intuit/karate#calling-java) if required.

## `optional()`
Will return a "real" [`Element`](#element-api) if it exists or a "fake" object if it does not.

This is useful to perform conditional logic as one-liners:

```cucumber
* optional('//pane{Warning}').locate('Close').click()
```

Note that `optional()`, [`exists()`](#exists), [`windowExists()`](#windowexists) and [`windowOptional()`](#windowoptional) are a little different from the other actions such as [`locate()`](#locate), because they will *not* honor any intent to [`retry()`](#retry) and *immediately* check the [active window](#robotactive) for the given locator. This is important because they are designed to answer the question: "*does the element exist in the application __right now__ ?*"

If you want to *wait* but move on even if something was not found, you can use [`waitForOptional()`](#waitforoptional) and [`waitForWindowOptional()`](#waitforwindowoptional).

## `windowOptional()`
Returns an "optional" [`Window`](#window-api) object and will not update the "active" window. You can call `activate()` on the returned `Window` object to set it as the current, typically after [checking that it exists](#optional) (by using the `present` property getter).

Here's an example of clicking a button within an "optional" modal pop-up only if it exists:

```cucumber
* windowOptional('Tips on Startup').locate('Close').click()
```

Note that on the [`Element` API](#element-api), there is no `click(locator)` API, but you can chain a [`locate()`](#locate) and then call [`click()`](#click).

Also see [finding windows](#finding-windows) and [conditional start](#conditional-start).

## `waitForOptional()`
Useful for those cases, where you want to *wait* for something that may *not* appear. Note that since the [retry()](#retry) count defaults to 3, you may want to tone-down the wait like this:

```cucumber
* retry(1).waitForOptional('Schrodingers Pane')
```

## `waitForWindowOptional()`
Just like `windowOptional()` but can [retry](#retry) *and* move on:

```cucumber
* retry(1).waitForWindowOptional('^My Window')
```

## `exists()`
Similar to [`optional()`](#optional) but returns a boolean, convenient to use with the [`assert`](https://github.com/intuit/karate#assert) keyword:

```cucumber
* assert exists('//pane{Main}')
```

The above is functionally equivalent to:

```cucumber
* assert optional('//pane{Main}').present
```

## `windowExists()`
Returns `true` or `false` and will not set or "activate" the current window.

See also [`windowOptional()`](#windowoptional).

## `window()`
Sets focus (and activates as "current") to the window by title, prefix with `^` for a string "contains" match or `~` for a regular-expression match. The "active" window will be used as the root of all operations such as [locating controls](#windows-locators).

Also see [finding windows](#finding-windows).

## `activate()`
Short-cut to activate any `Element` by locator. The difference from [`window()`](#window) is that this uses the [Windows Locator](#windows-locators) system to find elements. If you do this at the start of a test without a window activated or if [`robot.active`](#robotactive) is `null`, the search-root will be [`robot.root`](#robotroot) or the "Desktop". This can be useful in rare cases where the application under test lives under a "pane" [Control Type](#control-type) instead of a "window".

```cucumber
* activate('//pane{Some Name}')
```

## `robot.root`
Gets the root of all available objects as an [`Element`](#element-api) reference. Useful when you want to search within the entire "Desktop" on Windows. Try to avoid "any-depth" e.g. `robot.root.locate('//button')` kinds of searches on this element, and stick to things like `robot.root.locate('/pane')`.

Note that using the [`/root`](#root-scope) as the start of a [locator](#windows-locators) can be used instead.

## `robot.active`
Returns the currently "active" element, typically set after a previous call to [`window()`](#window) or [`windowOptional()`](#windowoptional). This will fail the test if a window (or any other `Element` type) has not been "activated".

The [`Element`](#element-api) API has an `activate()` method, so you can also do this:

```cucumber
* robot.root.locate('//pane{Some Name}').activate()
```

But it can be more convenient to use the below pattern, as `active` is also a "setter" property on the `robot` object:

```cucumber
* def e = locate('//{Some Name}')
* robot.active = e
```

## `robot.focused`
Returns the [`Element`](#element) that currently has "focus" on the screen, no matter where or what type it is.

## `robot.location`
Returns a [`Location`](src/main/java/com/intuit/karate/robot/Location.java) instance that represents the mouse position, useful for troubleshooting in debug mode.

```cucumber
* def region = locate('foo').region
* region.inset(30, region.height / 6).move()
* robot.location.highlight()
# you can also construct a location
* robot.location(885, 406).highlight()
```

## `robot.region()`
Constructs a [`Region`](src/main/java/com/intuit/karate/robot/Region.java) instance that can be used for debugging:

```cucumber
* def region = robot.region({ x: 100, y: 100, width: 100, height: 100 })
* region.debugCapture()
```

## `robot.clipboard`
Returns the clipboard contents as text. This can be convenient to validate text in non-standard controls where `Element.value` does not work.

```cucumber
# assume that a control containing text has focus
* input(Key.CONTROL + 'a')
* input(Key.CONTROL + 'c')
* match robot.clipboard == 'hello world'
```

## `robot.allWindows`
Returns an array of all windows that exist on the desktop. This is convenient to quickly list all window names on the console, especially in [debug mode](#debugging). Also you could loop over all of them and call methods on the [`Element`](#element-api) or [`Window`](#window-api) instance.

```cucumber
* print robot.allWindows
```

Note that this is equivalent to the below, but with the difference that the returned elements are of type [`Window`](#window-api) for the above but are of type [`Element`](#element-api) for the below.

```cucumber
* print robot.root.locateAll('//window')
```

Also note that you can use [`Element.children`](#element-api) to get all direct children of any element:

```cucumber
* print robot.root.children
```

## `locate()`
Rarely used, but when you want to just instantiate an [`Element`](src/main/java/com/intuit/karate/robot/Element.java) instance, typically when you are writing custom re-usable functions, or using an element as a "waypoint" to access other elements in a large, complex "tree".

```cucumber
* def e = locate('//pane{Some Pane}')
# now you can have multiple steps refer to "e"
* e.locate('//edit').input('foo')
* e.locate('//button').click()
```
Note that `locate()` will fail the test if the element was not found. Think of it as just like [`waitFor()`](#waitfor) but without the "wait" part.

Also see [`exists()`](#exists) and [`optional()`](#optional).

## `locateAll()`'
This can be convenient if you need to loop over a bunch of element and do something. More useful is the ability to target a single item by index. For example, here is how you can find the *second* control with the name "Address" and click on it:

```cucumber
* locateAll('Address')[1].click()
```

## `highlight()`
Designed for use within a [debug session](#debugging), very convenient to interactively locate an element by trial and error.

```cucumber
* highlight('Some Name')
```

Note that the [`Element` API](#element-api) also has an `activate()` method so you can do things like this in debug mode:

```
* robot.active.highlight()
```

Which will highlight the [currently "active"](#robotactive) element.

## `highlightAll()`
Like [`highlight()`](#highlight) and super convenient, you can try doing the following to show *all* buttons on a window !

```cucumber
* highlightAll('//button')
```

## `click()`
Defaults to a "left-click", pass 1, 2 or 3 as the argument to specify left, middle or right mouse button.

```cucumber
* click('Continue')
```

You can also click on any X and Y co-ordinate. Note that (0, 0) is the top, left of the screen.

```cucumber
* click(100, 200)
```

## `doubleClick()`
Performs a double-click at the current mouse position. Note that you can also chain this off an [`Element`](#element-api).

## `doubleClick()`
Performs a right-click at the current mouse position.

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

For convenience, you can pass an array of strings as a single argument, convenient for a lot of "brute force" keyboard navigation:

```cucumber
* input([Key.DOWN, Key.RIGHT, Key.ENTER])
```

And you can also add a second argument to the above case, convenient when you want to slow-down things because for e.g. Karate is too fast for the UI to perform validations or refresh:

```cucumber
* input([Key.DOWN, Key.RIGHT, Key.ENTER], 100)
```

And a string argument is also supported in which case each the delay is before each character.

```cucumber
* input('type this slowly', 100)
```

## `select()`
To select from a drop-down, for elements with a control-type of `itemtype`. The pattern is to get a reference to the item and call `select()` on it:

```cucumber
* locate('Some Text').select()
```

## `press()`
A mouse press that will be held down, useful for simulating a drag and drop operation.

## `release()`
Release mouse button, useful for simulating a drag and drop operation.

## `screenshot()`
Will save a screenshot of the viewport (entire desktop), which will appear in the HTML report. Note that this returns a byte-array of the PNG image.

```cucumber
* screenshot()
```

Note that you can call this *on* an [`Element`](#element-api) instance if you really want to "zoom in":

```cucumber
* locate('//pane{Tree}').screenshot()
```

## `screenshotActive()`
This will screenshot only the [active](#robotactive) control, typically the [window](#window) having focus.

```cucumber
* screenshotActive()
```

Note that this is a convenience short-cut for:

```cucumber
* robot.active.screenshot()
```

# Conditional Start
A useful pattern is to run an app-boot and sign-in sequence only if the main application window is not present. Note how [`karate.abort()`](https://github.com/intuit/karate#karate-abort) can be used to conditionaly exit a "called" feature early.

This is also a great example of using [`windowOptional()`](#windowoptional).

```cucumber
* def mainWindowName = '^MyApp'
* robot {}
* def mainWindow = windowOptional(mainWindowName)
* if (mainWindow.present) { mainWindow.activate(); karate.abort() }
* karate.fork('C:/myapp/app.exe')
* retry(10).window('Sign In')
* waitFor('#userid').input('john@smith.com')
* input('#password', 'Test@123')
* click('#submit-btn')
* retry(10).window(lacWindowName)
```

And the "calling feature" can directly jump into the flow to be tested after making a [`call`](https://github.com/intuit/karate#calling-other-feature-files) to the above:

```cucumber
Feature: main

Background:
* call read('start.feature')

Scenario:
# main flow
* click('#some-btn')
```

Also see [finding windows](#finding-windows).

# Utility Functions
Some of the [Karate JS API](https://github.com/intuit/karate#the-karate-object) that are more relevant to desktop or Windows app testing are described here:

## [`karate.toAbsolutePath()`](https://github.com/intuit/karate#karate-toabsolutepath)
This will return the OS specific path form, for example on Windows, back-slash characters will be used. This is useful to generate file-names needed to [`input()`](#input) into file-chooser dialogs and the like.

Here is an example of creating a random file-name on Windows. Also refer to [commonly needed utilities](https://github.com/intuit/karate#commonly-needed-utilities). The reason we use `target` here is that because it is the standard build-output directory where temp-files and reports are created.

```cucumber
* def random = function(){ return java.lang.System.currentTimeMillis() + '' }
* def dataFolder = function(){ return karate.toAbsolutePath('file:target') }
* def tempTextFile = function(){ return dataFolder() + '\\' + random() + '.txt' }
```

The [multiple functions in one file](https://github.com/intuit/karate#multiple-functions-in-one-file) pattern can be used to set up these common utilities, and now within a feature-file you can do this:

```cucumber
* def tempFile = tempTextFile()
```

## [`karate.exec()`](https://github.com/intuit/karate#karate-exec)
Can execute any OS command, wait for it it terminate, and return the system / console output as a string.

Also see [`karate.fork()`](#karatefork)

# Standalone JAR
The `karate-robot` for Windows is around 150 MB and hence not distributed with the [ZIP Release](https://github.com/intuit/karate/wiki/ZIP-Release). But you can download it separately, and it can be easily added to the classpath. You can find instructions [here](https://github.com/intuit/karate/wiki/ZIP-Release#karate-robot).

## Building
For MacOSX, Linux, Android or iOS, you can build a stand-alone JAR by following the [Developer Guide](https://github.com/intuit/karate/wiki/Developer-Guide#build-standalone-karate-robot-jar).
