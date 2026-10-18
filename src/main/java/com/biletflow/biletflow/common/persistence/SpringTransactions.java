package com.biletflow.biletflow.common.persistence;

import com.biletflow.biletflow.common.application.Transactions;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringTransactions implements Transactions {

    private final TransactionTemplate transactions;

    public SpringTransactions(PlatformTransactionManager manager) {
        transactions = new TransactionTemplate(manager);
    }

    @Override
    public <T> T required(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
