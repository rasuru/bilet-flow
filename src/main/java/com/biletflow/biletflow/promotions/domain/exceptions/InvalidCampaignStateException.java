package com.biletflow.biletflow.promotions.domain.exceptions;

public class InvalidCampaignStateException extends PromotionsDomainException {
    public InvalidCampaignStateException(String message) {
        super(message);
    }
}
