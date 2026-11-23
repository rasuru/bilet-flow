package com.biletflow.biletflow.promotions.config;

import com.biletflow.biletflow.promotions.domain.redemption.ReservationLifetime;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PromotionsConfiguration {

    @Bean
    ReservationLifetime promotionReservationLifetime(@Value("${biletflow.promotions.reservation-lifetime:PT15M}") Duration lifetime) {
        return new ReservationLifetime(lifetime);
    }
}
