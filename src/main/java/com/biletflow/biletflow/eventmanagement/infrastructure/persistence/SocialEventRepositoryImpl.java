package com.biletflow.biletflow.eventmanagement.infrastructure.persistence;

import com.biletflow.biletflow.eventmanagement.domain.SocialEvent;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventId;
import com.biletflow.biletflow.eventmanagement.domain.SocialEventRepository;
import com.biletflow.biletflow.eventmanagement.infrastructure.persistence.SocialEventMapper;
import com.biletflow.biletflow.eventmanagement.infrastructure.persistence.entity.SocialEventEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SocialEventRepositoryImpl implements SocialEventRepository {

    private final SpringDataSocialEventRepository springDataRepository;
    private final SocialEventMapper mapper;

    public SocialEventRepositoryImpl(SpringDataSocialEventRepository springDataRepository, SocialEventMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public SocialEvent save(SocialEvent socialEvent) {
        SocialEventEntity entity = mapper.toEntity(socialEvent);
        SocialEventEntity savedEntity = springDataRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<SocialEvent> findById(SocialEventId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<SocialEvent> findByOrganizerId(Long organizerId) {
        return springDataRepository.findByOrganizerId(organizerId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsById(SocialEventId id) {
        return springDataRepository.existsById(id.value());
    }

    @Override
    public void deleteById(SocialEventId id) {
        springDataRepository.deleteById(id.value());
    }
}
