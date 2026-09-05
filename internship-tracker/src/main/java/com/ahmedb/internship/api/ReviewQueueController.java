package com.ahmedb.internship.api;

import com.ahmedb.internship.api.dto.Requests;
import com.ahmedb.internship.api.dto.StatusEventView;
import com.ahmedb.internship.api.dto.UnmatchedEmailView;
import com.ahmedb.internship.service.ReviewQueueService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Emails the classifier understood but could not attribute.
 *
 * <p>Nothing here has touched the pipeline. Linking one writes the event it should have produced;
 * dismissing one drops it.
 */
@RestController
@RequestMapping("/unmatched")
public class ReviewQueueController {

    private final ReviewQueueService reviewQueue;

    public ReviewQueueController(ReviewQueueService reviewQueue) {
        this.reviewQueue = reviewQueue;
    }

    @GetMapping
    public List<UnmatchedEmailView> pending() {
        return reviewQueue.pending().stream().map(UnmatchedEmailView::from).toList();
    }

    /** Attaches a queued email to an application and writes its event. */
    @PostMapping("/{id}/link")
    public StatusEventView link(
            @PathVariable Long id, @Valid @RequestBody Requests.LinkUnmatchedEmail request) {
        return StatusEventView.from(
                reviewQueue.link(
                        id,
                        request.applicationId(),
                        request.learnSenderDomain() == null || request.learnSenderDomain(),
                        request.note()));
    }

    @PostMapping("/{id}/dismiss")
    public UnmatchedEmailView dismiss(@PathVariable Long id) {
        return UnmatchedEmailView.from(reviewQueue.dismiss(id));
    }
}
