package com.biletflow.biletflow.paymentsmonetization.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.biletflow.biletflow.common.application.AuthorizationException;
import com.biletflow.biletflow.common.application.FinancialAudit;
import com.biletflow.biletflow.common.application.TransactionLocks;
import com.biletflow.biletflow.common.security.CurrentActor;
import com.biletflow.biletflow.common.security.UnauthenticatedActorException;
import com.biletflow.biletflow.paymentsmonetization.application.command.ReinstatePaidSalesCommand;
import com.biletflow.biletflow.paymentsmonetization.application.command.SuspendPaidSalesCommand;
import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentTerms;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PaymentAdministrationAccessTest {

    private final Instant now = Instant.parse("2026-11-03T12:00:00Z");
    private final CurrentActor actor = mock(CurrentActor.class, CALLS_REAL_METHODS);
    private final PaymentSettingsRepository settings = mock(PaymentSettingsRepository.class);
    private final TransactionLocks locks = mock(TransactionLocks.class);
    private final FinancialAudit audit = mock(FinancialAudit.class);
    private final PaymentAdminQueries queries = mock(PaymentAdminQueries.class);
    private final ActivationRepository activations = mock(ActivationRepository.class);
    private final PaymentSettingsService settingsService = new PaymentSettingsService(
        settings,
        new PaymentTermsProvider(new PaymentTerms(new BigDecimal("1000"), new BigDecimal("0.03")), settings),
        mock(PaymentGateway.class),
        locks,
        audit,
        actor,
        Clock.fixed(now, ZoneOffset.UTC)
    );
    private final PaymentAdminQueryService queryService = new PaymentAdminQueryService(queries, mock(PaymentWorkRepository.class), actor);
    private final PaidSalesAdministrationService paidSales = new PaidSalesAdministrationService(
        activations,
        locks,
        audit,
        actor,
        Clock.fixed(now, ZoneOffset.UTC)
    );

    private void signedInAs(long id, boolean administrator) {
        doReturn(Optional.of(id)).when(actor).userId();
        doReturn(administrator).when(actor).isAdministrator();
    }

    @Test
    void nonAdministratorsAreRejectedBeforeAnyDataIsReadOrChanged() {
        signedInAs(7, false);

        assertThrows(AuthorizationException.class, () ->
            queryService.payments(new PaymentAdminQueries.PaymentFilter(null, null, null, null), 0, 10)
        );
        assertThrows(AuthorizationException.class, () -> queryService.activations(null, 0, 10));
        assertThrows(AuthorizationException.class, () -> paidSales.suspend(new SuspendPaidSalesCommand(UUID.randomUUID(), "Fraud")));
        assertThrows(AuthorizationException.class, settingsService::activationFee);
        assertThrows(AuthorizationException.class, () -> settingsService.changeActivationFee(new BigDecimal("1500")));
        assertThrows(AuthorizationException.class, () ->
            paidSales.reinstate(new ReinstatePaidSalesCommand(UUID.randomUUID(), "Cleared"))
        );
        assertThrows(AuthorizationException.class, settingsService::processingFeeRate);
        assertThrows(AuthorizationException.class, () -> settingsService.changeProcessingFeeRate(new BigDecimal("2.5")));

        verifyNoInteractions(queries, activations, settings, locks, audit);
    }

    @Test
    void unauthenticatedCallersAreRejected() {
        doReturn(Optional.empty()).when(actor).userId();

        assertThrows(UnauthenticatedActorException.class, () -> queryService.activations(null, 0, 10));
        verify(queries, never()).findActivations(any(), anyInt(), anyInt());
    }

    @Test
    void feeChangeTakesTheSettingsLockBeforeReadingTheCurrentFee() {
        signedInAs(99, true);
        when(settings.activationFee()).thenReturn(Optional.empty());

        settingsService.changeActivationFee(new BigDecimal("1500"));

        var order = inOrder(locks, settings);
        order.verify(locks).lock("payment-settings:activation-fee");
        order.verify(settings).activationFee();
        order.verify(settings).saveActivationFee(argThat(s -> s.amountKzt().compareTo(new BigDecimal("1500")) == 0 && s.updatedBy() == 99));
    }

    @Test
    void processingFeeRateIsStoredAsAFractionAfterTakingTheSettingsLock() {
        signedInAs(99, true);

        var view = settingsService.changeProcessingFeeRate(new BigDecimal("2.5"));

        var order = inOrder(locks, settings);
        order.verify(locks).lock("payment-settings:processing-fee-rate");
        order.verify(settings).saveProcessingFeeRate(
            argThat(s -> s.rate().compareTo(new BigDecimal("0.025")) == 0 && s.rate().scale() == 4 && s.updatedBy() == 99)
        );
        verify(audit).recordDetails(
            eq("payments"),
            eq(PaymentSettingsService.PROCESSING_FEE_RATE_SETTING),
            eq(99L),
            eq("PROCESSING_FEE_RATE_CHANGED"),
            eq("3.00% -> 2.50%")
        );
        assertEquals(new BigDecimal("3.00"), view.ratePercent());
    }

    @Test
    void processingFeeRateOutsideZeroToHundredPercentOrTooPreciseIsRejected() {
        signedInAs(99, true);

        assertThrows(IllegalArgumentException.class, () -> settingsService.changeProcessingFeeRate(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> settingsService.changeProcessingFeeRate(new BigDecimal("100")));
        assertThrows(IllegalArgumentException.class, () -> settingsService.changeProcessingFeeRate(new BigDecimal("2.555")));
        verify(settings, never()).saveProcessingFeeRate(any());
    }
}
