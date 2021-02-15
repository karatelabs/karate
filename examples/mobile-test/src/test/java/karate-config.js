function fn() {
  var config = {}
  var android = {}
  android["desiredConfig"] = {
   "app" : "https://github.com/babusekaran/droidAction/raw/master/build/UiDemo.apk",
   "newCommandTimeout" : 300,
   "platformVersion" : "9.0",
   "platformName" : "Android",
   "connectHardwareKeyboard" : true,
   "deviceName" : "emulator-5554",
   "avd" : "Pixel2_PIE",
   "automationName" : "UiAutomator2"
  }
  config["android"] = android
  return config;
}
