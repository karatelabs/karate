package com.intuit.karate.mock.core;

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author pthomas3
 */
@Testcontainers
class KarateMockRunner {

    static final Logger logger = LoggerFactory.getLogger(KarateMockRunner.class);

    static KarateMockCallback mockCallback;

    @Container
    public GenericContainer<?> activeMQContainer = new GenericContainer<>(DockerImageName.parse("rmohr/activemq"))
            .withExposedPorts(61616);

    private Connection connection;
    private Session session;
    private MessageProducer producer;
    private MessageConsumer consumer;

    // To store the received message
    private String receivedMessageText;

    private KarateMessage mockResp;

    static KarateMockCallback startKarateMockServer() {
        KarateMockCallback callback = KarateMockServer
                .feature("classpath:com/intuit/karate/mock/core/_mock.feature")
                .build();
        return callback;
    }

    @BeforeAll
    static void beforeAll() throws JMSException {
        mockCallback=startKarateMockServer();
    }

    @AfterAll
    static void afterAll() throws JMSException {
        mockCallback=null;
    }

    @BeforeEach
    public void setup() throws JMSException {
        String brokerUrl = "tcp://localhost:" + activeMQContainer.getMappedPort(61616);
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        connection = connectionFactory.createConnection();
        connection.start();

        // Creating session for sending messages
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Getting the queue
        Queue queue = session.createQueue("testQueue");

        // Creating the producer & consumer
        producer = session.createProducer(queue);
        consumer = session.createConsumer(queue);

        // Setting the MessageListener to process the messages asynchronously
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                try {
                    if (message instanceof TextMessage) {
                        String recMsg = ((TextMessage) message).getText();
                        KarateMessage<String> request = new KarateMessage<>();
                        request.setBody(recMsg);
                        mockResp = mockCallback.receive(request);
                        receivedMessageText = recMsg;
                    }
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @AfterEach
    public void tearDown() throws JMSException {
        // Cleaning up resources
        if (producer != null) producer.close();
        if (consumer != null) consumer.close();
        if (session != null) session.close();
        if (connection != null) connection.close();
    }

    @Test
    void testMessage() throws JMSException, InterruptedException {
        String dummyPayload = "TestPayload";

        // Sending a text message to the queue
        TextMessage message = session.createTextMessage(dummyPayload);
        producer.send(message);

        // Wait a moment for the asynchronous onMessage method to be triggered
        Thread.sleep(1000);

        assertEquals("TestPayload2", new String((byte[])mockResp.getBody(), StandardCharsets.UTF_8));
    }
}
