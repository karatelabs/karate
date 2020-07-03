package mock.contract;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class QueueUtils {

    private static final Logger logger = LoggerFactory.getLogger(QueueUtils.class);

    private static final Connection connection;

    public static Connection getConnection() {
        return connection;
    }        

    static {
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false&waitForStart=10000");
            connection = connectionFactory.createConnection();
            connection.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void send(String queueName, String text, int delayMillis) {
         new Thread(() -> {
            try {
                logger.info("*** artificial delay {}: {}", queueName, delayMillis);
                Thread.sleep(delayMillis);
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = session.createQueue(queueName);
                MessageProducer producer = session.createProducer(destination);
                producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                TextMessage message = session.createTextMessage(text);
                producer.send(message);
                logger.info("*** sent message {}: {}", queueName, text);
                session.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
         }).start();
    }

}
