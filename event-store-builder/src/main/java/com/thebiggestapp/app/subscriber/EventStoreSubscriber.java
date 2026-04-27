package com.thebiggestapp.app.subscriber;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.store.EventStore;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

/**
 * Se suscribe de forma DURABLE a los topics de ActiveMQ y persiste
 * cada mensaje recibido en el EventStore.
 *
 * La suscripción durable garantiza que, si este módulo se detiene,
 * al reiniciar recuperará los mensajes no consumidos.
 */
public class EventStoreSubscriber {

    // Topics a los que suscribirse (deben coincidir con los feeders)
    private static final String[] TOPICS = {"Weather", "Ticketmaster", "PredictHQ"};

    // ID único del cliente JMS para suscripciones durables
    private static final String CLIENT_ID = "event-store-builder";

    private final EventStore eventStore = new EventStore();

    public void start() throws JMSException {
        String brokerUrl = Config.get("ACTIVEMQ_URL");
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);

        Connection connection = factory.createConnection();
        // El clientID es obligatorio para suscripciones durables
        connection.setClientID(CLIENT_ID);
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Crear una suscripción durable por cada topic
        for (String topicName : TOPICS) {
            Topic topic = session.createTopic(topicName);
            // El nombre de la suscripción durable debe ser único por topic
            String subscriptionName = CLIENT_ID + "-" + topicName;
            MessageConsumer consumer = session.createDurableSubscriber(topic, subscriptionName);

            // Listener asíncrono: cada mensaje recibido se persiste en el EventStore
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

        // Mantener el proceso vivo
        addShutdownHook(connection);
    }

    /** Cierra la conexión limpiamente al hacer Ctrl+C. */
    private void addShutdownHook(Connection connection) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[EventStoreBuilder] Cerrando conexión...");
            try {
                connection.close();
            } catch (JMSException e) {
                System.err.println("[EventStoreBuilder] Error al cerrar: " + e.getMessage());
            }
        }));

        // Bloquear el hilo principal para que el proceso no termine
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
