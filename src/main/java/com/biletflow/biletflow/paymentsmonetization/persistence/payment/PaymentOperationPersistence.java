package com.biletflow.biletflow.paymentsmonetization.persistence.payment;

import com.biletflow.biletflow.paymentsmonetization.domain.settlement.PaymentOperation;

@jakarta.persistence.Converter
public class PaymentOperationPersistence implements jakarta.persistence.AttributeConverter<PaymentOperation, String> {

    @Override
    public String convertToDatabaseColumn(PaymentOperation operation) {
        return code(operation);
    }

    @Override
    public PaymentOperation convertToEntityAttribute(String value) {
        return operation(value);
    }

    public static String code(PaymentOperation operation) {
        if (operation == null) return null;
        return switch (operation) {
            case CAPTURE -> "charge";
            case VOID -> "cancel";
            case REFUND -> "refund";
        };
    }

    public static PaymentOperation operation(String value) {
        if (value == null) return null;
        return switch (value) {
            case "charge" -> PaymentOperation.CAPTURE;
            case "cancel" -> PaymentOperation.VOID;
            case "refund" -> PaymentOperation.REFUND;
            default -> throw new IllegalArgumentException("Unsupported persisted bank operation");
        };
    }
}
