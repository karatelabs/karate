# Karate Image Comparison
This project is designed to demonstrate basic usage of the [Image Comparison](https://github.com/karatelabs/karate/#compare-image) feature. You can also watch a video explanation [here](https://youtu.be/wlvmNBraP60).

## Overview
The [Image Comparison](https://github.com/karatelabs/karate/#compare-image) feature was introduced in [Karate 1.3.0](https://github.com/karatelabs/karate/wiki/1.3.0-Upgrade-Guide).
As a new feature with a number of options and a new UI component we wanted to provide a simple introduction to help users get started.

The included features are numbered 1 through 5 and build on each other. 
They are intended to demonstrate how you might start from scratch without any baseline images on a new project:
*  `1_establish_baseline.feature` establishes baseline images to use in future test runs
*  `2_compare_baseline.feature` compares dynamic screenshots against our baseline images
*  `3_custom_rebase.feature` demonstrates the use of the `onShowRebase` handler to customize the filename when rebasing
*  `4_generic_rebase.feature` shows a slightly more advanced use of the `onShowRebase` handler that incorporates image comparison configuration options
*  `5_custom_config.feature` shows the final scenario that is similar to what you might use in real tests

There is also a [screencast](https://www.youtube.com/watch?v=NIP3-njBR-Q) that demonstrates basic usage of the diff UI in the Karate HTML report.

## Running
The `5_custom_config.feature` is a complete [Karate UI test](https://github.com/karatelabs/karate/tree/master/karate-core) that can be executed by running `ImageComparisonRunner` as a JUnit test.
You will be able to open the HTML report (the file-name will appear at the end of the console log) and refresh it when re-running the test.

To manually run the test execute the following commands:
*  Install maven artifacts from the latest [develop](https://github.com/karatelabs/karate/tree/develop) branch locally
   ```
   mvn clean install -P pre-release
   ```
*  Run the test from the `examples/image-comparison` directory
   ```
    mvn clean test -Dtest=ImageComparisonRunner
   ```

## Debugging
You should be able to use the [Karate extension for Visual Studio Code](https://github.com/karatelabs/karate/wiki/IDE-Support#vs-code-karate-plugin) for stepping-through a test for troubleshooting.
