package mock.async;

import javax.jms.*;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueUtils {

    private static final Logger logger = LoggerFactory.getLogger(QueueUtils.class);

    private static final Connection connection;

    public static Connection getConnection() {
        return connection;
    }

    static {
        try {
            logger.debug("waiting for queue / connection ...");
            ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false&waitForStart=10000");
            connection = connectionFactory.createConnection();
            connection.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void send(String text, int delayMillis) {
        new Thread(() -> {
            try {
                logger.info("*** scheduled delay: {}", delayMillis);
                Thread.sleep(delayMillis);
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = session.createQueue(QueueConsumer.QUEUE_NAME);
                MessageProducer producer = session.createProducer(destination);
                producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                TextMessage message = session.createTextMessage(text);
                producer.send(message);
                logger.info("*** sent message: {}", text);
                session.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

}
