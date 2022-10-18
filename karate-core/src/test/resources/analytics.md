# Karate Analytics
From version [1.2.0 onwards](https://github.com/karatelabs/karate/releases/tag/v1.2.0) Karate sends a very limited set of events to a hosted instance of [Posthog](https://posthog.com) - an open-source analytics solution. Access to this is managed by the [Karate Labs team](#who-can-see-the-data).

Only one (anonymous) event is captured: which is when someone views the HTML report that Karate generates. Note that using the HTML report is completely optional.

If you set an OS environment property called `KARATE_TELEMETRY` to the value `false`, no events will be sent.

Users can also completely disable HTML reporting via the [API](https://github.com/karatelabs/karate#parallel-execution) or [command-line](https://github.com/karatelabs/karate/tree/master/karate-netty#output-format).

There is always the option of [using a third-party report](https://github.com/karatelabs/karate/tree/master/karate-demo#example-report) (via the JUnit-XML or Cucumber-JSON output).

## Why We Need This Now
Karate is an open-source project and there is no direct way to track usage and whether awareness camapigns are working.

Now that we are a for-profit open-source company, a basic level of usage data will help us focus on the right areas of product development.

## What Is Tracked 
* Anonymized Machine ID
* Browser Details (including OS info)
* Karate Version
* How Karate was invoked (via IDE plugin, NPM, etc.)

## Who Can See The Data
The dashboard is accessible only to the [Karate Labs](https://karatelabs.io) team that maintains Karate.
