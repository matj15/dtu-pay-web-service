package messaging.implementations;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import messaging.Message;
import messaging.MessageQueue;

public class RabbitMqQueue implements MessageQueue {

    private static final String DEFAULT_HOSTNAME = "localhost";
    private static final String EXCHANGE_NAME = "eventsExchange";
    private static final String QUEUE_TYPE = "topic";

    private Channel channel;

    public RabbitMqQueue() {
        this(DEFAULT_HOSTNAME);
    }

    public RabbitMqQueue(String hostname) {
        channel = setUpChannel(hostname);
    }

    @Override
    public void publish(Message event) {
        String message = new Gson().toJson(event);
        System.out.println("[x] Publish event of type " +  event.getClass().getSimpleName() + " " + message);
        try {
            channel.basicPublish(EXCHANGE_NAME, event.getClass().toString(), null, message.getBytes("UTF-8"));
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private Channel setUpChannel(String hostname) {
        Channel channel;
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(hostname);
            Connection connection = factory.newConnection();
            channel = connection.createChannel();
            channel.exchangeDeclare(EXCHANGE_NAME, QUEUE_TYPE);
        } catch (IOException | TimeoutException e) {
            throw new Error(e);
        }
        return channel;
    }

    @Override
    public <T extends Message> void addHandler(Class<T> event, Consumer<T> handler) {
        System.out.println("[x] handler " + handler + " for event type " + event.getSimpleName() + " installed");
        try {
            String queueName = channel.queueDeclare().getQueue();
            channel.queueBind(queueName, EXCHANGE_NAME, event.toString());

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");

                System.out.println("[x] handle event of type "  + event.getSimpleName() + " with message "+ message);
                handler.accept(new Gson().fromJson(message, event));
            };
            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {
            });
        } catch (IOException e1) {
            throw new Error(e1);
        }
    }

}
