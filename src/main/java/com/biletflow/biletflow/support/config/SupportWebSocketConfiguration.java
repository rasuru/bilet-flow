package com.biletflow.biletflow.support.config;

import com.biletflow.biletflow.support.integration.websocket.SupportConversationSocket;
import java.net.URI;
import java.util.LinkedHashSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableWebSocket
public class SupportWebSocketConfiguration implements WebSocketConfigurer {

    private final SupportConversationSocket socket;
    private final String[] allowedOrigins;

    public SupportWebSocketConfiguration(
        SupportConversationSocket socket, JHipsterProperties jHipster, @Value("${biletflow.frontend}") URI frontend
    ) {
        this.socket = socket;
        var origins = new LinkedHashSet<String>();
        origins.add(frontend.getScheme() + "://" + frontend.getRawAuthority());
        if (jHipster.getCors().getAllowedOrigins() != null) origins.addAll(jHipster.getCors().getAllowedOrigins());
        origins.remove("*");
        allowedOrigins = origins.toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(socket, "/websocket/support").setAllowedOrigins(allowedOrigins);
    }
}
