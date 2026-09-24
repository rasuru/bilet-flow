package com.biletflow.biletflow.ordercheckout.application.ticketinventory.command;

public sealed interface CheckoutHoldReference permits CheckoutGaReservationReference, CheckoutSeatHoldReference {}
