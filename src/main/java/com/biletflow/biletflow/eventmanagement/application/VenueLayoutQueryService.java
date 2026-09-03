package com.biletflow.biletflow.eventmanagement.application;

import com.biletflow.biletflow.eventmanagement.application.query.VenueLayoutView;
import com.biletflow.biletflow.eventmanagement.application.query.VenueSeatView;
import com.biletflow.biletflow.eventmanagement.domain.VenueLayout;
import com.biletflow.biletflow.eventmanagement.domain.VenueLayoutId;
import com.biletflow.biletflow.eventmanagement.domain.VenueLayoutRepository;
import com.biletflow.biletflow.eventmanagement.domain.VenueSeat;
import com.biletflow.biletflow.eventmanagement.domain.exceptions.VenueLayoutNotFoundException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VenueLayoutQueryService {

    private final VenueLayoutRepository repository;

    public VenueLayoutQueryService(VenueLayoutRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public VenueLayoutView get(VenueLayoutId layoutId) {
        VenueLayout layout = repository.findById(layoutId).orElseThrow(() -> new VenueLayoutNotFoundException(layoutId.value().toString()));

        return toView(layout);
    }

    public List<VenueLayoutView> list() {
        return repository.findAll().stream().map(this::toView).toList();
    }

    private VenueLayoutView toView(VenueLayout layout) {
        return new VenueLayoutView(layout.getId().value(), layout.getName(), layout.getSeats().stream().map(this::toView).toList());
    }

    private VenueSeatView toView(VenueSeat seat) {
        return new VenueSeatView(
            seat.id().value(),
            seat.location().section(),
            seat.location().row(),
            seat.location().seatNumber(),
            seat.accessible(),
            seat.priceCategory()
        );
    }
}
