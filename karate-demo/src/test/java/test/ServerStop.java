package test;

import org.junit.jupiter.api.Test;

/**
 *
 * @author pthomas3
 */
class ServerStop {

    @Test
    void stopServer() {
        MonitorThread.stop(8081);
    }

}
