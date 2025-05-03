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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class RabbitMQConfig {

    public static final String PROCESSING_QUEUE_NAME = "audio.processing.queue";
    public static final String PROCESSING_EXCHANGE_NAME = "audio.exchange";
    public static final String PROCESSING_ROUTING_KEY = "audio.process.key";

    public static final String UPLOAD_QUEUE_NAME = "nhost.upload.queue";
    public static final String UPLOAD_EXCHANGE_NAME = "nhost.upload.exchange";
    public static final String UPLOAD_AUDIO_ROUTING_KEY = "nhost.upload.audio.key";
    public static final String UPLOAD_PPTX_ROUTING_KEY = "nhost.upload.pptx.key";

    public static final String TRANSCRIPTION_QUEUE_NAME = "audio.transcription.queue";
    public static final String TRANSCRIPTION_ROUTING_KEY = "audio.transcription.key";

    public static final String PPTX_CONVERSION_QUEUE_NAME = "pptx.conversion.queue";
    public static final String PPTX_CONVERSION_ROUTING_KEY = "pptx.conversion.key";

    public static final String SUMMARIZATION_QUEUE_NAME = "summarization.queue";
    public static final String SUMMARIZATION_ROUTING_KEY = "summarization.process.key";

    public static final String RECOMMENDATIONS_QUEUE_NAME = "recommendations.queue";
    public static final String RECOMMENDATIONS_ROUTING_KEY = "recommendations.process.key";



    @Bean
    TopicExchange exchange() {
        return new TopicExchange(PROCESSING_EXCHANGE_NAME, true, false);
    }

    @Bean
    TopicExchange uploadExchange() {
        return new TopicExchange(UPLOAD_EXCHANGE_NAME, true, false);
    }

    @Bean("processingQueue")
    Queue processingQueue() {
        return new Queue(PROCESSING_QUEUE_NAME, true);
    }

    @Bean("uploadQueue")
    Queue uploadQueue() {
        return new Queue(UPLOAD_QUEUE_NAME, true);
    }

    @Bean("transcriptionQueue")
    Queue transcriptionQueue() {
        return new Queue(TRANSCRIPTION_QUEUE_NAME, true);
    }

    @Bean("pptxConversionQueue")
    Queue pptxConversionQueue() {
        return new Queue(PPTX_CONVERSION_QUEUE_NAME, true);
    }

    @Bean("summarizationQueue")
    Queue summarizationQueue() {
        return new Queue(SUMMARIZATION_QUEUE_NAME, true);
    }

    @Bean("recommendationsQueue")
    Queue recommendationsQueue() {
        return new Queue(RECOMMENDATIONS_QUEUE_NAME, true);
    }


    @Bean
    Binding processingBinding(@Qualifier("processingQueue") Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(PROCESSING_ROUTING_KEY);
    }

    @Bean
    Binding uploadAudioBinding(@Qualifier("uploadQueue") Queue queue,
            @Qualifier("uploadExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(UPLOAD_AUDIO_ROUTING_KEY);
    }

    @Bean
    Binding uploadPptxBinding(@Qualifier("uploadQueue") Queue queue,
            @Qualifier("uploadExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(UPLOAD_PPTX_ROUTING_KEY);
    }

    @Bean
    Binding transcriptionBinding(@Qualifier("transcriptionQueue") Queue queue,
            TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(TRANSCRIPTION_ROUTING_KEY);
    }

    @Bean
    Binding pptxConversionBinding(@Qualifier("pptxConversionQueue") Queue queue,
            TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(PPTX_CONVERSION_ROUTING_KEY);
    }

    @Bean
    Binding summarizationBinding(@Qualifier("summarizationQueue") Queue queue,
            TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(SUMMARIZATION_ROUTING_KEY);
    }

    @Bean
    Binding recommendationsBinding(@Qualifier("recommendationsQueue") Queue queue,
            TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(RECOMMENDATIONS_ROUTING_KEY);
    }

    @Bean
    public ObjectMapper rabbitObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper rabbitObjectMapper) {
        return new Jackson2JsonMessageConverter(rabbitObjectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
