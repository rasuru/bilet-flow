package com.biletflow.biletflow.ticketinventory.application.eventinventory.command;

public sealed interface HoldReference permits GaReservationReference, SeatHoldReference {}
