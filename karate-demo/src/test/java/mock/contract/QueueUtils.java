package mock.contract;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;

/**
 *
 * @author pthomas3
 */
public class QueueUtils {

    public static Connection getConnection() {
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");
            Connection connection = connectionFactory.createConnection();
            connection.start();
            return connection;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    public static void submit(Runnable task) {
        executor.submit(task);
    }

    public static void waitUntilStopped() {
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void send(String queueName, String text, int delayMillis) {
        executor.submit(() -> {
            try {
                System.out.println("*** sleeping: " + delayMillis);
                Thread.sleep(delayMillis);
                Connection connection = getConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination destination = session.createQueue(queueName);
                MessageProducer producer = session.createProducer(destination);
                producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                TextMessage message = session.createTextMessage(text);
                producer.send(message);
                System.out.println("*** sent message: " + text);
                session.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}
