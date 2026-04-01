package io.karatelabs.js;

public class DemoUtils {

    public static String doWork() {
        return "hello";
    }

    public static String doWorkException() {
        throw new RuntimeException("failed");
    }

    public static String withVarArgs(String arg1, String arg2, String... varArgs) {
        StringBuilder sb = new StringBuilder(arg1).append(",").append(arg2);
        for (String v : varArgs) {
            sb.append(",").append(v);
        }
        return sb.toString();
    }

}
