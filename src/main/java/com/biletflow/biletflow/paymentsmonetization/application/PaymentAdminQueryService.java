package com.biletflow.biletflow.paymentsmonetization.application;

import com.biletflow.biletflow.paymentsmonetization.application.view.ActivationRecordView;
import com.biletflow.biletflow.paymentsmonetization.application.view.AdminPaymentView;
import com.biletflow.biletflow.paymentsmonetization.domain.activation.EventPaidSalesActivationStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform-administrator search over payments and activation records. Endpoint security restricts it to administrators. */
@Service
@Transactional(readOnly = true)
public class PaymentAdminQueryService {

    private final PaymentAdminQueries queries;
    private final PaymentWorkRepository works;

    public PaymentAdminQueryService(PaymentAdminQueries queries, PaymentWorkRepository works) {
        this.queries = Objects.requireNonNull(queries, "queries cannot be null");
        this.works = Objects.requireNonNull(works, "works cannot be null");
    }

    public List<AdminPaymentView> payments(PaymentAdminQueries.PaymentFilter filter, int offset, int limit) {
        requirePage(offset, limit);
        return queries
            .findPaymentIds(Objects.requireNonNull(filter, "filter cannot be null"), offset, limit)
            .stream()
            .map(works::find)
            .flatMap(Optional::stream)
            .map(AdminPaymentView::from)
            .toList();
    }

    public List<ActivationRecordView> activations(EventPaidSalesActivationStatus status, int offset, int limit) {
        requirePage(offset, limit);
        return queries.findActivations(status, offset, limit).stream().map(ActivationRecordView::from).toList();
    }

    private static void requirePage(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid pagination");
    }
}
