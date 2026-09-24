package com.biletflow.biletflow.ordercheckout.application.ticketinventory.command;

public sealed interface CheckoutHoldSelection permits CheckoutGeneralAdmissionSelection, CheckoutAssignedSeatsSelection {}
