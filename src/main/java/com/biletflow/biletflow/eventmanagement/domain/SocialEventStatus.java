package com.biletflow.biletflow.eventmanagement.domain;

// PUBLISHED + now within dates = ACTIVE
//  now > end = COMPLETED
public enum SocialEventStatus {
    DRAFT,
    PUBLISHED,
    UNPUBLISHED,
    CANCELLED,
}
