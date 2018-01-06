package mock.contract;

/**
 *
 * @author pthomas3
 */
public class Shipment {

    private int paymentId;
    private String status;

    public int getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(int paymentId) {
        this.paymentId = paymentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }        
    
}
