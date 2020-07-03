package mock.contract;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.pool.PooledConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class QueueUtils implements AutoCloseable  {

    private static final Logger logger = LoggerFactory.getLogger(QueueUtils.class);

    private static final Connection connection;
    private static final ScheduledExecutorService executorService;

    public static Connection getConnection() {
        return connection;
    }        

    static {
        try {
            executorService = Executors.newScheduledThreadPool(10);
            PooledConnectionFactory connectionFactory = new PooledConnectionFactory("vm://localhost");
            connectionFactory.setBlockIfSessionPoolIsFullTimeout(Duration.ofSeconds(10).toMillis());
            connection = connectionFactory.createConnection();
            connection.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void send(String queueName, String text, int delayMillis) {
        logger.info("*** artificial delay {}: {}", queueName, delayMillis);
        executorService.schedule(() -> send(queueName, text), delayMillis, TimeUnit.MILLISECONDS);
    }

    public static void send(String queueName, String text) {
        try {
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
    }

    @Override
    public void close() throws Exception {
        if(connection != null) {
            connection.close();
        }
    }
}
