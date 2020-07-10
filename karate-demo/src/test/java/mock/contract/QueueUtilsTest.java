package mock.contract;

import javax.jms.JMSException;
import javax.jms.TextMessage;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class QueueUtilsTest {
    
    private static final Logger logger = LoggerFactory.getLogger(QueueUtilsTest.class);

    private boolean passed = false;

    @Test
    public void testQueueOperations() throws Exception {
        String queueName = "DEMO.TEST";
        QueueUtils.send(queueName, "first", 0);
        QueueConsumer consumer = new QueueConsumer(queueName);
        String text = consumer.waitForNextMessage();
        assertEquals("first", text);
        QueueUtils.send(queueName, "second", 0);
        QueueUtils.send(queueName, "third", 0);
        consumer.purgeMessages();
        QueueUtils.send(queueName, "foo", 25);
        consumer.setMessageListener(m -> {
            TextMessage tm = (TextMessage) m;
            try {
                logger.info("*** received message: {}", tm.getText());
                assertEquals("foo", tm.getText());
                passed = true;
                synchronized (consumer) {
                    consumer.notify();
                }
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }
        });
        synchronized (consumer) {
            consumer.wait(10000);
        }
        assertTrue(passed);
    }

}
