package com.biletflow.biletflow.ticketinventory.application.eventinventory.command;

public sealed interface HoldSelection permits GeneralAdmissionSelection, AssignedSeatsSelection {}
