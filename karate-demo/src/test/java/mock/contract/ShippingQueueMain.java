package mock.contract;

import javax.jms.JMSException;
import javax.jms.TextMessage;

/**
 *
 * @author pthomas3
 */
public class ShippingQueueMain {
    
    public static void main(String[] args) {
        ShippingQueue.start();
        ShippingQueue.put("foo");
        Consumer consumer = new Consumer("http://localhost:8080");
        consumer.startQueueListener(m -> {
            TextMessage tm = (TextMessage) m;
            try {
                System.out.println("*** received message: " + tm.getText());
            } catch (JMSException e) {
                System.err.println(e.getMessage());
            }
            consumer.stopQueueListener();
            ShippingQueue.stop();
        });        
    }
    
}
