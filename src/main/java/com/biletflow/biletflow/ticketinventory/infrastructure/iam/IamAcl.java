package com.biletflow.biletflow.ticketinventory.infrastructure.iam;

import com.biletflow.biletflow.iam.ohs.IamOhs;
import com.biletflow.biletflow.iam.ohs.dto.VerifiedUserEmailView;
import com.biletflow.biletflow.ticketinventory.application.ticket.port.VerifiedUserEmailPort;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class IamAcl implements VerifiedUserEmailPort {

    private final IamOhs iamOhs;

    public IamAcl(IamOhs iamOhs) {
        this.iamOhs = Objects.requireNonNull(iamOhs);
    }

    @Override
    public Optional<String> findVerifiedEmail(Long userId) {
        return iamOhs.findVerifiedUserEmail(userId).map(VerifiedUserEmailView::email);
    }
}
