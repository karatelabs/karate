package io.karatelabs.cli;

import java.util.List;

/** Test fixture — registered via META-INF/services to verify Main's ServiceLoader discovery. */
public class DummyCommandProvider implements CliCommandProvider {

    @Override
    public List<Object> commands() {
        return List.of(new DummyServeCommand());
    }

}
