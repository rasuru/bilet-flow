package com.biletflow.biletflow.ordercheckout.external.ticketinventory;

import com.biletflow.biletflow.ordercheckout.application.ticketinventory.command.*;
import com.biletflow.biletflow.ordercheckout.application.ticketinventory.port.*;
import com.biletflow.biletflow.ordercheckout.application.ticketinventory.result.*;
import com.biletflow.biletflow.ordercheckout.application.ticketinventory.view.*;

import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.AssignedSeatsSelection;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.GeneralAdmissionSelection;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.HoldInventoryCommand;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.HoldReference;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.HoldSelection;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.ReleaseHoldCommand;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.GaReservationReference;
import com.biletflow.biletflow.ticketinventory.application.eventinventory.command.SeatHoldReference;
import com.biletflow.biletflow.ticketinventory.application.sale.command.CompleteSaleCommand;
import com.biletflow.biletflow.ticketinventory.application.ticket.command.IssueTicketItem;
import com.biletflow.biletflow.ticketinventory.application.ticket.view.TicketView;
import com.biletflow.biletflow.ticketinventory.ohs.TicketInventoryOhs;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class TicketInventoryAcl implements TicketInventoryPort {

    private final TicketInventoryOhs ticketInventoryOhs;

    public TicketInventoryAcl(TicketInventoryOhs ticketInventoryOhs) {
        this.ticketInventoryOhs = Objects.requireNonNull(ticketInventoryOhs);
    }

    @Override
    public CheckoutHoldInventoryResult hold(
        CheckoutHoldInventoryCommand command
    ) {
        var upstream = ticketInventoryOhs.hold(
            new HoldInventoryCommand(
                command.eventId(),
                command.ticketTypeId(),
                command.sessionId(),
                toUpstreamSelection(command.selection())
            )
        );

        return new CheckoutHoldInventoryResult(
            upstream.holds()
                .stream()
                .map(this::toCheckoutHoldReference)
                .toList(),
            upstream.expiresAt()
        );
    }

    @Override
    public void releaseHold(
        CheckoutReleaseHoldCommand command
    ) {
        ticketInventoryOhs.releaseHold(
            new ReleaseHoldCommand(
                command.eventId(),
                command.sessionId(),
                command.holds()
                    .stream()
                    .map(this::toUpstreamHoldReference)
                    .toList()
            )
        );
    }

    @Override
    public List<CheckoutTicketView> completeSale(
        CheckoutCompleteSaleCommand command
    ) {
        var upstream = ticketInventoryOhs.completeSale(
            new CompleteSaleCommand(
                command.orderId(),
                command.eventId(),
                command.holds()
                    .stream()
                    .map(this::toUpstreamHoldReference)
                    .toList(),
                command.items()
                    .stream()
                    .map(this::toUpstreamIssueTicketItem)
                    .toList()
            )
        );

        return upstream
            .stream()
            .map(this::toCheckoutTicketView)
            .toList();
    }

    private HoldSelection toUpstreamSelection(
        CheckoutHoldSelection selection
    ) {
        if (selection instanceof CheckoutGeneralAdmissionSelection ga) {
            return new GeneralAdmissionSelection(
                ga.quantity()
            );
        }

        if (selection instanceof CheckoutAssignedSeatsSelection seats) {
            return new AssignedSeatsSelection(
                seats.seatIds()
            );
        }

        throw new IllegalArgumentException(
            "Unsupported hold selection: " + selection.getClass()
        );
    }

    private HoldReference toUpstreamHoldReference(
        CheckoutHoldReference reference
    ) {
        if (reference instanceof CheckoutGaReservationReference ga) {
            return new GaReservationReference(
                ga.reservationId()
            );
        }

        if (reference instanceof CheckoutSeatHoldReference seat) {
            return new SeatHoldReference(
                seat.holdId()
            );
        }

        throw new IllegalArgumentException(
            "Unsupported hold reference: " + reference.getClass()
        );
    }

    private CheckoutHoldReference toCheckoutHoldReference(
        HoldReference reference
    ) {
        if (reference instanceof GaReservationReference ga) {
            return new CheckoutGaReservationReference(
                ga.reservationId()
            );
        }

        if (reference instanceof SeatHoldReference seat) {
            return new CheckoutSeatHoldReference(
                seat.holdId()
            );
        }

        throw new IllegalArgumentException(
            "Unsupported hold reference: " + reference.getClass()
        );
    }

    private IssueTicketItem toUpstreamIssueTicketItem(
        CheckoutIssueTicketItem item
    ) {
        return new IssueTicketItem(
            item.ticketTypeId(),
            item.attendeeEmail(),
            item.ownerUserId(),
            item.seatId()
        );
    }

    private CheckoutTicketView toCheckoutTicketView(
        TicketView ticket
    ) {
        return new CheckoutTicketView(
            ticket.id(),
            ticket.ticketTypeId(),
            ticket.eventId(),
            ticket.orderId(),
            ticket.attendeeEmail(),
            ticket.ownerUserId(),
            ticket.seatId(),
            ticket.ticketCode(),
            CheckoutTicketStatus.valueOf(
                ticket.status().name()
            ),
            ticket.issuedAt()
        );
    }

    @Override
    public CheckoutTicketTypeConfiguration getTicketType(UUID ticketTypeId) {

        var upstream = ticketInventoryOhs.getTicketType(ticketTypeId);

        return new CheckoutTicketTypeConfiguration(
            upstream.id(),
            upstream.eventId(),
            upstream.price(),
            upstream.maxPerOrder(),
            upstream.priceCategory()
        );
    }
}
