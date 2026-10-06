package com.dlqmanager.config;

import com.dlqmanager.util.ApiTime;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

@Configuration
public class AppConfig {

    /**
     * Used for Slack webhooks. Without timeouts a Slack outage would leave the alert
     * scheduler and the team feed waiting on a connection forever.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(requestFactory);
    }

    @Bean
    public ObjectMapper objectMapper() {
        JavaTimeModule javaTime = new JavaTimeModule();
        // LocalDateTime fields (createdAt, startedAt, ...) leave the API as UTC instants with a "Z",
        // so browsers show them in the viewer's own time zone (see ApiTime)
        javaTime.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator json, SerializerProvider provider) throws IOException {
                json.writeString(ApiTime.utc(value));
            }
        });

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(javaTime);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
