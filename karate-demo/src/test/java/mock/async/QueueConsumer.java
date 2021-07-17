package mock.async;

import com.intuit.karate.EventContext;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
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

    public QueueConsumer() {
        this.connection = QueueUtils.getConnection();
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createQueue(QUEUE_NAME);
            consumer = session.createConsumer(destination);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public void listen(EventContext context) {
        setMessageListener(message -> {
            TextMessage tm = (TextMessage) message;
            try {
                context.signalAppend(tm.getText());
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

}