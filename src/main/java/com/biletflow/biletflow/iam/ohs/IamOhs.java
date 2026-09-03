package com.biletflow.biletflow.iam.ohs;

import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import java.util.Optional;

/**
 * Open Host Service published by Identity & Access Management.
 *
 * Other bounded contexts depend on this contract rather than IAM's
 * JPA entities, repositories, security classes, or JHipster DTOs.
 */
public interface IamOhs {
    /**
     * Returns the user's email only when IAM considers the account activated.
     *
     * For the current JHipster model, activation is the available signal that
     * the account/email activation flow has completed.
     */
    Optional<VerifiedUserEmailView> findVerifiedUserEmail(Long userId);
}
