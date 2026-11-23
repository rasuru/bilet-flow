package com.biletflow.biletflow.promotions.ohs;

import com.biletflow.biletflow.common.application.EntityNotFoundException;
import com.biletflow.biletflow.promotions.application.redemption.RedemptionService;
import com.biletflow.biletflow.promotions.application.redemption.command.ConfirmRedemptionCommand;
import com.biletflow.biletflow.promotions.application.redemption.command.PromotionLine;
import com.biletflow.biletflow.promotions.application.redemption.command.QuotePromotionCommand;
import com.biletflow.biletflow.promotions.application.redemption.command.ReleaseRedemptionCommand;
import com.biletflow.biletflow.promotions.application.redemption.command.ReservePromotionCommand;
import com.biletflow.biletflow.promotions.application.redemption.view.PromotionQuoteView;
import com.biletflow.biletflow.promotions.application.redemption.view.ReservedPromotionView;
import com.biletflow.biletflow.promotions.domain.exceptions.PromoCodeRejectedException;
import com.biletflow.biletflow.promotions.domain.redemption.RedemptionSource;
import com.biletflow.biletflow.promotions.ohs.PromotionRejectedException.Reason;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class DefaultPromotionsOhs implements PromotionsOhs {

    private final RedemptionService redemptions;

    public DefaultPromotionsOhs(RedemptionService redemptions) {
        this.redemptions = Objects.requireNonNull(redemptions, "redemptions cannot be null");
    }

    @Override
    public Quote quote(UUID eventId, UUID checkoutId, String code, List<Line> lines) {
        PromotionQuoteView view = translatingRejections(() ->
            redemptions.quote(new QuotePromotionCommand(eventId, checkoutId, code, toPromotionLines(lines)))
        );

        return new Quote(view.campaignId(), view.code(), view.eligibleSubtotal(), view.discountAmount());
    }

    @Override
    public Reservation reserve(UUID eventId, UUID checkoutId, String code, List<Line> lines, Source source) {
        Objects.requireNonNull(source, "source cannot be null");

        ReservedPromotionView view = translatingRejections(() ->
            redemptions.reserve(
                new ReservePromotionCommand(eventId, checkoutId, code, toPromotionLines(lines), RedemptionSource.valueOf(source.name()))
            )
        );

        return new Reservation(view.redemptionId(), view.campaignId(), view.code(), view.discountAmount(), view.expiresAt());
    }

    @Override
    public void confirm(UUID redemptionId, UUID orderId) {
        redemptions.confirm(new ConfirmRedemptionCommand(redemptionId, orderId));
    }

    @Override
    public void release(UUID redemptionId, UUID checkoutId) {
        redemptions.release(new ReleaseRedemptionCommand(redemptionId, checkoutId));
    }

    private static <T> T translatingRejections(Supplier<T> action) {
        try {
            return action.get();
        } catch (PromoCodeRejectedException exception) {
            throw new PromotionRejectedException(Reason.valueOf(exception.getReason().name()), exception.getMessage());
        } catch (EntityNotFoundException exception) {
            throw new PromotionRejectedException(Reason.NOT_FOUND, "Promo code not found");
        }
    }

    private static List<PromotionLine> toPromotionLines(List<Line> lines) {
        Objects.requireNonNull(lines, "lines cannot be null");
        return lines
            .stream()
            .map(line -> new PromotionLine(line.ticketTypeId(), line.unitPrice(), line.quantity()))
            .toList();
    }
}
