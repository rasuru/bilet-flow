package com.biletflow.biletflow.ordercheckout.application;

import com.biletflow.biletflow.common.application.QueryPage;
import com.biletflow.biletflow.common.security.CurrentActor;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderAdministrationQueryService {

    private final OrderAdminQueries orders;
    private final CurrentActor actor;

    public OrderAdministrationQueryService(OrderAdminQueries orders, CurrentActor actor) {
        this.orders = orders;
        this.actor = actor;
    }

    public List<OrderAdminQueries.View> search(OrderAdminQueries.Filter filter, int offset, int limit) {
        actor.requireAdministrator();
        return orders.search(filter, new QueryPage(offset, limit));
    }
}
