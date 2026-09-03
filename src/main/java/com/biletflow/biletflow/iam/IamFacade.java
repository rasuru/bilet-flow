package com.biletflow.biletflow.iam;

import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import com.biletflow.biletflow.iam.repository.UserRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IamFacade implements IamOhs {

    private final UserRepository userRepository;

    public IamFacade(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
    }

    @Override
    public Optional<VerifiedUserEmailView> findVerifiedUserEmail(Long userId) {
        Objects.requireNonNull(userId, "userId cannot be null");

        return userRepository
            .findById(userId)
            .filter(user -> user.isActivated())
            .filter(user -> user.getEmail() != null)
            .filter(user -> !user.getEmail().isBlank())
            .map(user -> new VerifiedUserEmailView(user.getId(), user.getEmail()));
    }
}
