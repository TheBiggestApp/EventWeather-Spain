package com.thebiggestapp.app.broker;

import com.thebiggestapp.app.config.Config;
import com.thebiggestapp.app.datamart.EventParser;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

public class ActiveMQSubscriber {

	private final EventParser eventParser = new EventParser();
	private Connection connection;

	public void startListening() {
		try {
			String brokerUrl = Config.get("ACTIVEMQ_URL", "tcp://localhost:61616");
			ConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
			connection = factory.createConnection();
			connection.start();

			Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			String[] topics = {"Weather", "PredictHQ", "Ticketmaster"};

			for (String topicName : topics) {
				Topic topic = session.createTopic(topicName);
				MessageConsumer consumer = session.createConsumer(topic);

				consumer.setMessageListener(message -> {
					try {
						if (message instanceof TextMessage textMessage) {
							String json = textMessage.getText();
							eventParser.process(json, topicName);
						}
					} catch (JMSException e) {
						System.err.println("[ActiveMQSubscriber] Error al leer mensaje: " + e.getMessage());
					}
				});
				System.out.println("[ActiveMQSubscriber] Suscrito al topic: " + topicName);
			}

		} catch (JMSException e) {
			System.err.println("[ActiveMQSubscriber] Error de conexión con ActiveMQ: " + e.getMessage());
		}
	}

	public void stopListening() {
		try {
			if (connection != null) {
				connection.close();
				System.out.println("[ActiveMQSubscriber] Conexión con ActiveMQ cerrada correctamente.");
			}
		} catch (JMSException e) {
			System.err.println("[ActiveMQSubscriber] Error al cerrar conexión: " + e.getMessage());
		}
	}
}