package com.lafayette.promptserver.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** Payload for submitting a star rating (1–5). */
@Data
public class RatingRequest {

    @Min(value = 1, message = "Rating must be at least 1 star")
    @Max(value = 5, message = "Rating must be at most 5 stars")
    private int stars;
}
