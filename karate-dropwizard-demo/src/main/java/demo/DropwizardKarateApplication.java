package demo;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;

public class DropwizardKarateApplication extends Application<Configuration> {
    public static void main(String[] args) throws Exception {
        new DropwizardKarateApplication().run(args);
    }

    @Override
    public void run(Configuration configuration, Environment environment) {
        environment.jersey().register(new HelloResource());
    }
}
