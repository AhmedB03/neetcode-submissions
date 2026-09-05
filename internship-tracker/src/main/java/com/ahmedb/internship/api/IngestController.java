package com.ahmedb.internship.api;

import com.ahmedb.internship.service.IngestionPipeline;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs a polling cycle on demand.
 *
 * <p>Exists so ingestion can be driven without waiting for (or enabling) the scheduler -- useful for
 * the first 90-day backfill, and for seeing what a run did.
 */
@RestController
@RequestMapping("/ingest")
public class IngestController {

    private final IngestionPipeline pipeline;

    public IngestController(IngestionPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @PostMapping("/run")
    public IngestionPipeline.IngestionResult run() {
        return pipeline.run();
    }
}
