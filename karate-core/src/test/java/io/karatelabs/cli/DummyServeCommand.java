package io.karatelabs.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/** Test fixture — a contributed subcommand discovered via {@link DummyCommandProvider}. */
@Command(name = "dummy-serve", description = "test-only contributed subcommand")
public class DummyServeCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }

}
