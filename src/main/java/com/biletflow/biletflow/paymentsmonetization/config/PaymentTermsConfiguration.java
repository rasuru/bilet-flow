package com.biletflow.biletflow.paymentsmonetization.config;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentTerms;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentTermsConfiguration {

    @Bean
    public PaymentTerms paymentTerms(
        @Value("${biletflow.payments.activation-fee-kzt:1000}") BigDecimal activationFee,
        @Value("${biletflow.payments.processing-fee-rate:0.03}") BigDecimal processingFeeRate
    ) {
        return new PaymentTerms(activationFee, processingFeeRate);
    }
}
