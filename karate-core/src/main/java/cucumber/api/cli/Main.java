package cucumber.api.cli;

/**
 * replaces cucumber-jvm code
 *
 * @author pthomas3
 */
public class Main {

    public static void main(String[] args) {
        com.intuit.karate.cli.IdeMain.main(args);
        System.exit(0);
    }

}
