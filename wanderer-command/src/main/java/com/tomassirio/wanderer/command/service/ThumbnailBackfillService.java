package com.tomassirio.wanderer.command.service;

import com.tomassirio.wanderer.commons.dto.ThumbnailBackfillResultDTO;

/** Admin maintenance: regenerates thumbnails whose files are missing from storage. */
public interface ThumbnailBackfillService {

    /**
     * Generates, one at a time, the thumbnail of every trip that has at least one update but no
     * thumbnail file. A failure on one trip does not stop the others.
     *
     * @return counts of checked, missing, regenerated and failed trips
     */
    ThumbnailBackfillResultDTO regenerateMissingTripThumbnails();
}
