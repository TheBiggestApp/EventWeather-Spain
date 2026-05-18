package com.thebiggestapp.app.subscriber;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.datamart.EventParser;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

public class BusinessUnitSubscriber {

    private static final String[] TOPIC_NAMES      = {"Weather", "Ticketmaster", "PredictHQ"};
    private static final String   CLIENT_ID         = "business-unit";
    private static final String   KEY_ACTIVEMQ_URL  = "activemq.url";
    private static final int      INITIAL_RETRY_MS  = 2_000;
    private static final int      MAX_RETRY_MS      = 30_000;

    private final EventParser eventParser = new EventParser();
    private volatile boolean  active      = true;

    public void start() {
        System.out.println("[BUSubscriber] Iniciando suscripción en tiempo real...");
        connectWithRetry();
    }

    private void connectWithRetry() {
        int retryDelay = INITIAL_RETRY_MS;

        while (active) {
            try {
                openConnectionAndListen();
                retryDelay = INITIAL_RETRY_MS;
            } catch (JMSException e) {
                System.err.printf("[BUSubscriber] Conexión fallida: %s. Reintentando en %ds...%n",
                        e.getMessage(), retryDelay / 1000);
                waitBeforeRetry(retryDelay);
                retryDelay = nextRetryDelay(retryDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void openConnectionAndListen() throws JMSException, InterruptedException {
        Connection connection = createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        subscribeToAllTopics(session);
        registerShutdownHook(connection);

        System.out.println("[BUSubscriber] Escuchando eventos en tiempo real...");
        Thread.currentThread().join();
    }

    private Connection createConnection() throws JMSException {
        String brokerUrl = Config.get(KEY_ACTIVEMQ_URL);
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);

        Connection connection = factory.createConnection();
        connection.setClientID(CLIENT_ID);
        connection.setExceptionListener(ex ->
                System.err.println("[BUSubscriber] Error JMS: " + ex.getMessage()));
        connection.start();
        return connection;
    }

    private void subscribeToAllTopics(Session session) throws JMSException {
        for (String topicName : TOPIC_NAMES) {
            subscribeToDurableTopic(session, topicName);
        }
    }

    private void subscribeToDurableTopic(Session session, String topicName) throws JMSException {
        Topic topic = session.createTopic(topicName);
        String subscriptionName = CLIENT_ID + "-" + topicName;
        MessageConsumer consumer = session.createDurableSubscriber(topic, subscriptionName);
        consumer.setMessageListener(message -> handleMessage(message, topicName));
        System.out.println("[BUSubscriber] Suscripción durable activa en: " + topicName);
    }

    private void handleMessage(Message message, String topicName) {
        try {
            if (message instanceof TextMessage textMessage) {
                System.out.printf("[BUSubscriber] Evento recibido en '%s'%n", topicName);
                eventParser.process(textMessage.getText(), topicName);
            }
        } catch (JMSException e) {
            System.err.println("[BUSubscriber] Error al leer mensaje: " + e.getMessage());
        }
    }

    private void registerShutdownHook(Connection connection) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeConnection(connection)));
    }

    private void closeConnection(Connection connection) {
        System.out.println("[BUSubscriber] Cerrando conexión con ActiveMQ...");
        active = false;
        try { connection.close(); } catch (JMSException ignored) {}
    }

    private void waitBeforeRetry(int delayMs) {
        try { Thread.sleep(delayMs); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private int nextRetryDelay(int current) {
        return Math.min(current * 2, MAX_RETRY_MS);
    }
}