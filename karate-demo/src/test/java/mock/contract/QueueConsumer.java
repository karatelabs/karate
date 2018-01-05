package mock.contract;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 *
 * @author pthomas3
 */
public class QueueConsumer {

    private final Connection connection;
    private final MessageConsumer consumer;
    private final String queueName;
    
    private boolean stopped = false;

    public QueueConsumer(String queueName) {
        this.queueName = queueName;
        this.connection = QueueUtils.getConnection();
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createQueue(queueName);
            consumer = session.createConsumer(destination);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setMessageListener(MessageListener ml) {
        QueueUtils.submit(() -> {
            try {
                consumer.setMessageListener(ml);
                System.out.println("*** started listener: " + queueName);
                while (!stopped) {
                    System.out.println("*** listening ..");
                    Thread.sleep(200);
                }
                System.out.println("*** stopped listening");
            } catch (Exception e) {
                throw new RuntimeException();
            }
        });
    }

    public String waitForMessage() {
        try {
            TextMessage tm = (TextMessage) consumer.receive();
            return tm.getText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public void purgeMessages() {
        try {
            consumer.setMessageListener(null);
            while (true) {
                Message message = consumer.receive(250);
                if (message == null) {
                    System.out.println("*** no more messages to purge");
                    break;
                }
                System.out.println("*** purged message: " + message);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }    

    public void stop() {
        stopped = true;
        try {
            connection.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
