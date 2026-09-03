package com.biletflow.biletflow.ticketinventory.domain.eventinventory;

public sealed interface InventoryMode permits GeneralAdmissionInventory, AssignedSeatingInventory {}
