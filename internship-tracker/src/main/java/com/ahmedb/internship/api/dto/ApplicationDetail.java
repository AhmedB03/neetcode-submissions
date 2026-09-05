package com.ahmedb.internship.api.dto;

import com.ahmedb.internship.domain.Application;
import com.ahmedb.internship.domain.StatusEvent;
import com.ahmedb.internship.service.GhostPolicy;
import java.util.Comparator;
import java.util.List;

/** One application with its full event timeline, oldest first. */
public record ApplicationDetail(ApplicationSummary application, List<StatusEventView> timeline) {

    public static ApplicationDetail from(Application application, GhostPolicy ghostPolicy) {
        List<StatusEventView> timeline =
                application.getEvents().stream()
                        .sorted(
                                Comparator.comparing(StatusEvent::getOccurredAt)
                                        .thenComparing(StatusEvent::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(StatusEventView::from)
                        .toList();
        return new ApplicationDetail(ApplicationSummary.from(application, ghostPolicy), timeline);
    }
}
