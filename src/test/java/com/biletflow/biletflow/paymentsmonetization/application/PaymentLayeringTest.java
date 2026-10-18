package com.biletflow.biletflow.paymentsmonetization.application;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the requested layer boundaries without adding a production dependency. */
class PaymentLayeringTest {

    private final Path root = Path.of("src/main/java/com/biletflow/biletflow");

    @Test
    void applicationHasNoInfrastructureDependencies() throws IOException {
        for (String context : List.of("paymentsmonetization", "ordercheckout")) {
            try (var files = Files.walk(root.resolve(context).resolve("application"))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    for (String forbidden : List.of(
                        ".persistence.",
                        ".infrastructure.",
                        ".config.",
                        "JdbcTemplate",
                        "TransactionTemplate",
                        "javax.crypto",
                        "java.security",
                        "tools.jackson",
                        "@Scheduled"
                    ))
                        assertFalse(source.contains(forbidden), file + " depends on " + forbidden);
                }
            }
        }
    }

    @Test
    void settlementDomainHasNoApplicationOrFrameworkDependencies() throws IOException {
        try (var files = Files.walk(root.resolve("paymentsmonetization/domain/settlement"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String forbidden : List.of(
                    ".application.",
                    ".persistence.",
                    ".infrastructure.",
                    ".ohs.",
                    "org.springframework",
                    "tools.jackson"
                ))
                    assertFalse(source.contains(forbidden), file + " depends on " + forbidden);
            }
        }
    }
}
