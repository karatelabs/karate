package io.karatelabs.js;

public class DemoSimpleObject implements SimpleObject {

    @Override
    public Object jsGet(String name) {
        if (name.equals("doWorkException")) {
            return (JsInvokable) args -> {
                throw new RuntimeException("failed");
            };
        }
        if (name.equals("inner")) {
            return (SimpleObject) innerName -> {
                if (innerName.equals("doWorkException")) {
                    throw new RuntimeException("failed");
                }
                return null;
            };
        }
        return null;
    }

}
