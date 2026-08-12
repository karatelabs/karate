import io.karatelabs.js.Engine;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public class Smoke {

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        File[] files = dir.listFiles((d, n) -> n.endsWith(".js"));
        Arrays.sort(files);
        int pass = 0, fail = 0;
        for (File f : files) {
            String name = f.getName().replace(".js", "");
            String src = new String(Files.readAllBytes(f.toPath()), "UTF-8");
            try {
                Engine engine = new Engine();
                engine.eval(src);
                pass++;
                System.out.println(name + " PASS");
            } catch (Throwable t) {
                fail++;
                Throwable root = t;
                String msg = t.getMessage();
                if (msg == null) {
                    msg = String.valueOf(t);
                }
                int nl = msg.indexOf('\n');
                if (nl >= 0) {
                    msg = msg.substring(0, nl);
                }
                System.out.println(name + " FAIL " + root.getClass().getSimpleName() + ": " + msg);
            }
        }
        System.out.println("---- TOTAL " + (pass + fail) + " PASS " + pass + " FAIL " + fail);
    }
}
