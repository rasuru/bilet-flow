package com.biletflow.biletflow.support.persistence.supportcase;

import com.biletflow.biletflow.support.domain.supportcase.SupportCase;
import com.biletflow.biletflow.support.domain.supportcase.SupportCaseId;
import com.biletflow.biletflow.support.domain.supportcase.SupportMessage;
import com.biletflow.biletflow.support.persistence.supportcase.entity.SupportCaseJpaEntity;
import com.biletflow.biletflow.support.persistence.supportcase.entity.SupportMessageJpaEmbeddable;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class SupportCasePersistenceMapper {

    public SupportCase toDomain(SupportCaseJpaEntity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");

        List<SupportMessage> messages = entity.getMessages().stream()
            .map(message -> new SupportMessage(
                message.getId(),
                message.getAuthorUserId(),
                message.getAuthorType(),
                message.getContent(),
                message.getSentAt()
            ))
            .toList();

        return SupportCase.rehydrate(
            new SupportCaseId(entity.getId()),
            entity.getType(),
            entity.getRequesterUserId(),
            entity.getSubject(),
            entity.getCategory(),
            entity.getEventId(),
            entity.getOrderId(),
            entity.getTicketId(),
            entity.getStatus(),
            entity.getAssignedSupportUserId(),
            entity.getAssignedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getResolvedAt(),
            messages
        );
    }

    public SupportCaseJpaEntity toNewEntity(SupportCase domain) {
        Objects.requireNonNull(domain, "domain cannot be null");

        SupportCaseJpaEntity entity = new SupportCaseJpaEntity(
            domain.getId().value(),
            domain.getType(),
            domain.getRequesterUserId(),
            domain.getCreatedAt()
        );
        updateEntity(domain, entity);
        return entity;
    }

    public void updateEntity(SupportCase domain, SupportCaseJpaEntity entity) {
        Objects.requireNonNull(domain, "domain cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");

        if (!entity.getId().equals(domain.getId().value())) {
            throw new IllegalArgumentException("Cannot map SupportCase onto entity with another id");
        }
        if (entity.getType() != domain.getType()) {
            throw new IllegalArgumentException("SupportCase type is immutable and cannot be changed");
        }
        if (!entity.getRequesterUserId().equals(domain.getRequesterUserId())) {
            throw new IllegalArgumentException("RequesterUserId is immutable and cannot be changed");
        }
        if (!entity.getCreatedAt().equals(domain.getCreatedAt())) {
            throw new IllegalArgumentException("CreatedAt is immutable and cannot be changed");
        }

        entity.setMutableState(
            domain.getSubject(),
            domain.getCategory(),
            domain.getEventId(),
            domain.getOrderId(),
            domain.getTicketId(),
            domain.getStatus(),
            domain.getAssignedSupportUserId(),
            domain.getAssignedAt(),
            domain.getUpdatedAt(),
            domain.getResolvedAt()
        );

        entity.replaceMessages(domain.getMessages().stream()
            .map(message -> new SupportMessageJpaEmbeddable(
                message.id(),
                message.authorUserId(),
                message.authorType(),
                message.content(),
                message.sentAt()
            ))
            .toList());
    }
}
