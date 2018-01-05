package mock.contract;

import javax.jms.JMSException;
import javax.jms.TextMessage;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class QueueUtilsTest {

    private boolean passed = false;

    @Test
    public void testQueueOperations() throws Exception {
        String queueName = "DEMO.TEST";
        QueueUtils.send(queueName, "first", 0);
        QueueConsumer consumer = new QueueConsumer(queueName);
        String text = consumer.waitForMessage();
        assertEquals("first", text);
        QueueUtils.send(queueName, "second", 0);
        QueueUtils.send(queueName, "third", 0);
        consumer.purgeMessages();
        QueueUtils.send(queueName, "foo", 25);
        consumer.setMessageListener(m -> {
            TextMessage tm = (TextMessage) m;
            try {
                System.out.println("*** received message: " + tm.getText());
                assertEquals("foo", tm.getText());
                passed = true;
                consumer.stop();
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }

        });
        QueueUtils.waitUntilStopped();
        assertTrue(passed);
    }

}
