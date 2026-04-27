package com.thebiggestapp.app.publisher;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.model.Event;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

/**
 * Publica eventos en un topic de ActiveMQ.
 * Uso: instanciar, llamar publish() para cada evento, cerrar con close().
 */
public class ActiveMQPublisher implements AutoCloseable {

    private final Connection connection;
    private final Session session;
    private final MessageProducer producer;
    private final String topicName;

    public ActiveMQPublisher(String topicName) throws JMSException {
        this.topicName = topicName;
        String brokerUrl = Config.get("ACTIVEMQ_URL");

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        connection = factory.createConnection();
        connection.start();

        // Session no transaccional, auto-acknowledge
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Topic topic = session.createTopic(topicName);
        producer = session.createProducer(topic);
        producer.setDeliveryMode(DeliveryMode.PERSISTENT);

        System.out.println("[Publisher] Conectado a ActiveMQ. Topic: " + topicName);
    }

    /**
     * Serializa el evento a JSON y lo envía al topic.
     */
    public void publish(Event event) throws JMSException {
        String json = event.toJson();
        TextMessage message = session.createTextMessage(json);
        producer.send(message);
        System.out.println("[Publisher] Evento enviado -> " + json);
    }

    @Override
    public void close() {
        try {
            producer.close();
            session.close();
            connection.close();
            System.out.println("[Publisher] Conexión cerrada. Topic: " + topicName);
        } catch (JMSException e) {
            System.err.println("[Publisher] Error al cerrar conexión: " + e.getMessage());
        }
    }
}
