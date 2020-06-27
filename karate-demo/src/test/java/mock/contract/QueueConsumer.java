package mock.contract;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class QueueConsumer {

    private static final Logger logger = LoggerFactory.getLogger(QueueConsumer.class);

    private final Connection connection;
    private final MessageConsumer consumer;
    private final String queueName;
    private final Session session;

    public QueueConsumer(String queueName) {
        this.queueName = queueName;
        this.connection = QueueUtils.getConnection();
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createQueue(queueName);
            consumer = session.createConsumer(destination);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void listen(java.util.function.Consumer<String> handler) {
        setMessageListener(message -> {
            TextMessage tm = (TextMessage) message;
            try {
                handler.accept(tm.getText());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void setMessageListener(MessageListener ml) {
        try {
            consumer.setMessageListener(ml);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String waitForNextMessage() {
        try {
            TextMessage tm = (TextMessage) consumer.receive();
            return tm.getText();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    public void purgeMessages() {
        try {
            consumer.setMessageListener(null);
            while (true) {
                Message message = consumer.receive(50);
                if (message == null) {
                    logger.info("*** no more messages to purge: {}", queueName);
                    break;
                }
                logger.info("*** purged message: {} - {}", queueName, message);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        try {
            consumer.close();
            session.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
