package edu.cit.audioscholar.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PROCESSING_QUEUE_NAME = "audio.processing.queue";
    public static final String PROCESSING_EXCHANGE_NAME = "audio.exchange";
    public static final String PROCESSING_ROUTING_KEY = "audio.process.key";

    public static final String UPLOAD_QUEUE_NAME = "nhost.upload.queue";
    public static final String UPLOAD_ROUTING_KEY = "nhost.upload.key";


    @Bean("processingQueue")
    Queue processingQueue() {
        return new Queue(PROCESSING_QUEUE_NAME, true);
    }

    @Bean
    TopicExchange exchange() {
        return new TopicExchange(PROCESSING_EXCHANGE_NAME, true, false);
    }

    @Bean
    Binding processingBinding(@Qualifier("processingQueue") Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(PROCESSING_ROUTING_KEY);
    }

    @Bean("uploadQueue")
    Queue uploadQueue() {
        return new Queue(UPLOAD_QUEUE_NAME, true);
    }

    @Bean
    Binding uploadBinding(@Qualifier("uploadQueue") Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(UPLOAD_ROUTING_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
