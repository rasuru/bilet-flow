package com.biletflow.biletflow.ordercheckout.rest;

import com.biletflow.biletflow.common.rest.ApiProblems;
import com.biletflow.biletflow.ordercheckout.application.CheckoutConflict;
import com.biletflow.biletflow.ticketinventory.ohs.CheckoutInventoryOhs;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CheckoutResource.class)
public class CheckoutExceptionAdvice {

    @ExceptionHandler(CheckoutConflict.class)
    public ResponseEntity<ProblemDetail> handleConflict(CheckoutConflict exception) {
        ProblemDetail problem = switch (exception.reason()) {
            case IDEMPOTENCY_KEY_REUSED -> ApiProblems.body(
                HttpStatus.CONFLICT,
                "checkout-idempotency-conflict",
                "Checkout idempotency conflict",
                exception.getMessage()
            );
            case REGISTRATION_CLOSED -> ApiProblems.body(
                HttpStatus.CONFLICT,
                "registration-closed",
                "Event registration closed",
                exception.getMessage()
            );
        };
        return ApiProblems.response(problem);
    }

    @ExceptionHandler(CheckoutInventoryOhs.MixedSeatingModes.class)
    public ResponseEntity<ProblemDetail> handleMixedSeatingModes(CheckoutInventoryOhs.MixedSeatingModes exception) {
        return ApiProblems.response(
            ApiProblems.body(HttpStatus.BAD_REQUEST, "mixed-seating-modes", "Mixed seating modes", exception.getMessage())
        );
    }

    @ExceptionHandler(CheckoutInventoryOhs.Unavailable.class)
    public ResponseEntity<ProblemDetail> handleUnavailable(CheckoutInventoryOhs.Unavailable exception) {
        ProblemDetail problem = ApiProblems.body(
            HttpStatus.CONFLICT,
            "inventory-unavailable",
            "Inventory unavailable",
            exception.getMessage()
        );
        if (exception.seatId() != null) {
            problem.setProperty("unavailableSeatIds", List.of(exception.seatId()));
        }
        return ApiProblems.response(problem);
    }
}
