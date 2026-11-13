package com.biletflow.biletflow.promotions.persistence.promocode;

import com.biletflow.biletflow.promotions.domain.campaign.CampaignId;
import com.biletflow.biletflow.promotions.domain.promocode.CampaignToken;
import com.biletflow.biletflow.promotions.domain.promocode.CodeValue;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCode;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeId;
import com.biletflow.biletflow.promotions.domain.promocode.PromoCodeRepository;
import com.biletflow.biletflow.promotions.persistence.promocode.entity.PromoCodeJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaPromoCodeRepository implements PromoCodeRepository {

    private final SpringDataPromoCodeJpaRepository jpaRepository;
    private final PromoCodePersistenceMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaPromoCodeRepository(SpringDataPromoCodeJpaRepository jpaRepository, PromoCodePersistenceMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Optional<PromoCode> findById(PromoCodeId id) {
        Objects.requireNonNull(id, "PromoCodeId cannot be null");
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<PromoCode> findByCode(CodeValue code) {
        Objects.requireNonNull(code, "code cannot be null");
        return jpaRepository.findByCode(code.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<PromoCode> findByToken(CampaignToken token) {
        Objects.requireNonNull(token, "token cannot be null");
        return jpaRepository.findByCampaignToken(token.value()).map(mapper::toDomain);
    }

    @Override
    public List<PromoCode> findByCampaignId(CampaignId campaignId) {
        Objects.requireNonNull(campaignId, "CampaignId cannot be null");
        return jpaRepository
            .findByCampaignIdOrderByCreatedAtAsc(campaignId.value())
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public boolean existsByCode(CodeValue code) {
        Objects.requireNonNull(code, "code cannot be null");
        return jpaRepository.existsByCode(code.value());
    }

    @Override
    @Transactional
    public PromoCode save(PromoCode promoCode) {
        Objects.requireNonNull(promoCode, "promoCode cannot be null");

        PromoCodeJpaEntity managed = entityManager.find(PromoCodeJpaEntity.class, promoCode.getId().value());

        if (managed == null) {
            PromoCodeJpaEntity created = mapper.toNewEntity(promoCode);
            entityManager.persist(created);
            entityManager.flush();
            return mapper.toDomain(created);
        }

        mapper.updateEntity(promoCode, managed);
        entityManager.flush();
        return mapper.toDomain(managed);
    }
}
