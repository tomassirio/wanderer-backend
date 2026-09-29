package com.tomassirio.wanderer.commons.dto;

/**
 * Summary of an admin bulk regeneration of missing trip thumbnails.
 *
 * @param checked trips with at least one update that were inspected
 * @param missing trips among those that had no thumbnail file
 * @param regenerated missing thumbnails that now exist
 * @param failed missing thumbnails that could not be generated
 */
public record ThumbnailBackfillResultDTO(int checked, int missing, int regenerated, int failed) {}
