package cucumber.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * as of version 0.9.0 - replaced by {@link com.intuit.karate.KarateOptions}
 * 
 * @author pthomas3
 */
@Deprecated
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface CucumberOptions {

    boolean dryRun() default false;

    boolean strict() default false;

    String[] features() default {};

    String[] glue() default {};

    String[] tags() default {};

    String[] format() default {};

    String[] plugin() default {};

    boolean monochrome() default false;

    String[] name() default {};

    String[] junit() default {};

}
