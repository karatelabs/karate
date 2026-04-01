package io.karatelabs.core.interop;

/**
 * Simple POJO for testing Java interop with Karate's JavaScript engine.
 */
public class SimplePojo {

    private String foo;
    private int bar;

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }

    public int getBar() {
        return bar;
    }

    public void setBar(int bar) {
        this.bar = bar;
    }

    public static String staticVarArgs(String first, String second, String... rest) {
        StringBuilder sb = new StringBuilder(first).append(",").append(second);
        for (String s : rest) {
            sb.append(",").append(s);
        }
        return sb.toString();
    }

    public String instanceVarArgs(String... args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(args[i]);
        }
        return sb.toString();
    }

}
