package demo.upload;

import cucumber.api.CucumberOptions;
import demo.TestBase;

/**
 *
 * @author pthomas3
 */
@CucumberOptions(features = "classpath:demo/upload/upload-image.feature")
public class UploadImageRunner extends TestBase {
    
}
