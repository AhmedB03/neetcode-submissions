package com.ahmedb.internship.api;

import com.ahmedb.internship.api.dto.DigestResponse;
import com.ahmedb.internship.service.DigestService;
import com.ahmedb.internship.service.GhostPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What needs attention: deadlines inside the horizon, and anything that has gone quiet. */
@RestController
@RequestMapping("/digest")
public class DigestController {

    private final DigestService digest;
    private final GhostPolicy ghostPolicy;

    public DigestController(DigestService digest, GhostPolicy ghostPolicy) {
        this.digest = digest;
        this.ghostPolicy = ghostPolicy;
    }

    @GetMapping
    public DigestResponse get() {
        return DigestResponse.from(digest.build(), ghostPolicy);
    }
}
