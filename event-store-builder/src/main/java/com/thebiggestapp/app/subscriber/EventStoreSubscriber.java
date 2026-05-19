package com.thebiggestapp.app.subscriber;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.store.EventStore;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

public class EventStoreSubscriber {

    private static final String[] TOPICS = {"Weather", "Ticketmaster", "PredictHQ"};

    private static final String CLIENT_ID = "event-store-builder";

    private final EventStore eventStore = new EventStore();

    public void start() throws JMSException {
        String brokerUrl = Config.get("ACTIVEMQ_URL");
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);

        Connection connection = factory.createConnection();
        connection.setClientID(CLIENT_ID);
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        for (String topicName : TOPICS) {
            Topic topic = session.createTopic(topicName);
            String subscriptionName = CLIENT_ID + "-" + topicName;
            MessageConsumer consumer = session.createDurableSubscriber(topic, subscriptionName);

            final String capturedTopic = topicName;
            consumer.setMessageListener(message -> {
                try {
                    if (message instanceof TextMessage textMessage) {
                        String json = textMessage.getText();
                        System.out.printf("[Subscriber] Mensaje recibido en topic '%s'%n", capturedTopic);
                        eventStore.store(capturedTopic, json);
                    } else {
                        System.err.println("[Subscriber] Tipo de mensaje no soportado: " + message.getClass());
                    }
                } catch (JMSException e) {
                    System.err.println("[Subscriber] Error al leer mensaje: " + e.getMessage());
                }
            });

            System.out.println("[Subscriber] Suscripción durable activa en topic: " + topicName);
        }

        System.out.println("[EventStoreBuilder] Escuchando mensajes... (Ctrl+C para detener)");

        addShutdownHook(connection);
    }

    private void addShutdownHook(Connection connection) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[EventStoreBuilder] Cerrando conexión...");
            try {
                connection.close();
            } catch (JMSException e) {
                System.err.println("[EventStoreBuilder] Error al cerrar: " + e.getMessage());
            }
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
