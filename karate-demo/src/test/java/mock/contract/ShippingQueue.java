package mock.contract;

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
public class ShippingQueue {
    
    private static Connection connection;
    
    public static void start() {
        if (connection == null) {
            try {
                ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");
                connection = connectionFactory.createConnection(); 
                connection.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    public static void stop() {
        try {
            connection.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void put(String text) {
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination destination = session.createQueue("DEMO.SHIPPING");
            MessageProducer producer = session.createProducer(destination);
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT); 
            TextMessage message = session.createTextMessage(text);
            producer.send(message);
            session.close();           
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
