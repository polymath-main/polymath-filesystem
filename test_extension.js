// test_extension.js
// This script runs directly inside the Android Java Virtual Machine via Mozilla Rhino!

PolymathOS.toast("Hello from JavaScript running inside Android JVM!");

var testPath = "/sdcard/Download/polymath_js_test.txt";
PolymathOS.daemonCommand("execute_command", "echo 'Written by JS Extension' > " + testPath);

var content = PolymathOS.readFile(testPath);
PolymathOS.toast("File content read by JS: " + content);

"JS Execution Completed Successfully!";
