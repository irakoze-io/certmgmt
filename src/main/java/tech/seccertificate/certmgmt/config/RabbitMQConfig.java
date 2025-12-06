package tech.seccertificate.certmgmt.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for async PDF generation pipeline.
 * 
 * <p>Queue Structure:
 * <pre>
 * certificate.exchange (topic)
 *   ├── certificate.generation.queue (routing key: certificate.generate)
 *   │   └── (DLQ) certificate.generation.dlq (routing key: certificate.generate.dlq)
 * </pre>
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String CERTIFICATE_GENERATION_QUEUE = "certificate.generation.queue";
    public static final String CERTIFICATE_GENERATION_DLQ = "certificate.generation.dlq";
    public static final String CERTIFICATE_EXCHANGE = "certificate.exchange";
    public static final String ROUTING_KEY_GENERATE = "certificate.generate";
    public static final String ROUTING_KEY_DLQ = "certificate.generate.dlq";

    @Bean
    public TopicExchange certificateExchange() {
        return ExchangeBuilder
                .topicExchange(CERTIFICATE_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue certificateGenerationDLQ() {
        return QueueBuilder
                .durable(CERTIFICATE_GENERATION_DLQ)
                .build();
    }

    @Bean
    public Queue certificateGenerationQueue() {
        return QueueBuilder
                .durable(CERTIFICATE_GENERATION_QUEUE)
                .withArgument("x-dead-letter-exchange", CERTIFICATE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DLQ)
                .withArgument("x-message-ttl", 3600000)
                .build();
    }

    @Bean
    public Binding certificateGenerationBinding() {
        return BindingBuilder
                .bind(certificateGenerationQueue())
                .to(certificateExchange())
                .with(ROUTING_KEY_GENERATE);
    }

    @Bean
    public Binding certificateGenerationDLQBinding() {
        return BindingBuilder
                .bind(certificateGenerationDLQ())
                .to(certificateExchange())
                .with(ROUTING_KEY_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        var converter = new Jackson2JsonMessageConverter(objectMapper);
        // Jackson2JsonMessageConverter automatically includes __TypeId__ header
        // which allows proper deserialization to the target type
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter(objectMapper));
        template.setMandatory(true);
        
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("Message confirmed: {}", correlationData);
            } else {
                log.error("Message not confirmed: {}, cause: {}", correlationData, cause);
            }
        });
        
        template.setReturnsCallback(returned -> {
            log.error("Message returned: {}, replyCode: {}, replyText: {}, exchange: {}, routingKey: {}",
                    returned.getMessage(), returned.getReplyCode(), returned.getReplyText(),
                    returned.getExchange(), returned.getRoutingKey());
        });
        
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter(objectMapper));
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        factory.setPrefetchCount(10);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        
        return factory;
    }
}
