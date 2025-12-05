package tech.seccertificate.certmgmt.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.AbstractMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Custom JSON message converter for RabbitMQ messages.
 * Replaces deprecated Jackson2JsonMessageConverter in Spring Boot 4.0.
 */
public class JsonMessageConverter extends AbstractMessageConverter {

    private final ObjectMapper objectMapper;

    public JsonMessageConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected Message createMessage(Object object, MessageProperties messageProperties) {
        try {
            var jsonBytes = objectMapper.writeValueAsBytes(object);
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
            messageProperties.setContentLength(jsonBytes.length);
            return new Message(jsonBytes, messageProperties);
        } catch (IOException e) {
            throw new MessageConversionException("Failed to convert object to JSON", e);
        }
    }

    @Override
    public Object fromMessage(Message message) throws MessageConversionException {
        var messageProperties = message.getMessageProperties();
        if (messageProperties == null) {
            throw new MessageConversionException("Message properties are null");
        }

        var contentType = messageProperties.getContentType();
        if (contentType != null && contentType.contains("json")) {
            try {
                var body = message.getBody();
                if (body == null || body.length == 0) {
                    return null;
                }
                var jsonString = new String(body, StandardCharsets.UTF_8);
                return objectMapper.readValue(jsonString, Object.class);
            } catch (IOException e) {
                throw new MessageConversionException("Failed to convert JSON to object", e);
            }
        }

        return message.getBody();
    }
}
