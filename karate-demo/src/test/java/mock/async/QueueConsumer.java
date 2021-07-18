package mock.async;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueConsumer {

    private static final Logger logger = LoggerFactory.getLogger(QueueConsumer.class);

    public static final String QUEUE_NAME = "MOCK.ASYNC";

    private final Connection connection;
    private final MessageConsumer consumer;
    private final Session session;

    // in more complex tests or for re-usability, this field and append() /
    // collect() / clear() methods can be in a separate / static class
    private final List messages = new ArrayList();

    public synchronized void append(Object message) {
        messages.add(message);
        if (condition.test(message)) {
            logger.debug("condition met, will signal completion");
            future.complete(Boolean.TRUE);
        } else {
            logger.debug("condition not met, will continue waiting");
        }
    }

    public synchronized List collect() {
        return messages;
    }
    
    private CompletableFuture future = new CompletableFuture();
    private Predicate condition = o -> true; // just a default
    
    // note how you can pass data in from the test for very dynamic checks
    public List waitUntilCount(int count) {        
        condition = o -> messages.size() == count;
        try {
            future.get(5000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.error("wait timed out: {}", e + "");
        }
        return messages;
    }

    public QueueConsumer() {
        this.connection = QueueUtils.getConnection();
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createQueue(QUEUE_NAME);
            consumer = session.createConsumer(destination);
            consumer.setMessageListener(message -> {
                TextMessage tm = (TextMessage) message;
                try {
                    // this is where we "collect" messages for assertions later
                    append(tm.getText());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
