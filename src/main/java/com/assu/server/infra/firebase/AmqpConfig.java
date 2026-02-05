package com.assu.server.infra.firebase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {
    public static final String EXCHANGE = "notif.ex";
    public static final String ROUTING_KEY = "notif.send";
    public static final String QUEUE = "notif.send.q";
    public static final String DLX = "notif.dlx";
    public static final String DLQ = "notif.send.dlq";

    @Bean DirectExchange exchange() { return new DirectExchange(EXCHANGE, true, false); }
    @Bean DirectExchange dlx()      { return new DirectExchange(DLX, true, false); }

    @Bean
    Queue queue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY + ".dead")
                .build();
    }

    @Bean Queue dlq() { return QueueBuilder.durable(DLQ).build(); }

    @Bean Binding bind()    { return BindingBuilder.bind(queue()).to(exchange()).with(ROUTING_KEY); }
    @Bean Binding bindDlq() { return BindingBuilder.bind(dlq()).to(dlx()).with(ROUTING_KEY + ".dead"); }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate rt = new RabbitTemplate(cf);
        rt.setMessageConverter(new Jackson2JsonMessageConverter());
        return rt;
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper om) {
        return new Jackson2JsonMessageConverter(om);
    }
}
