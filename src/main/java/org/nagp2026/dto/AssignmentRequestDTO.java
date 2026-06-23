package org.nagp2026.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignmentRequestDTO(
        @NotBlank(message = "Student name cannot be empty")
        String studentName,

        @NotBlank(message = "Topic cannot be empty")
        String topic,

        @NotNull(message = "Score is required")
        @Min(value = 0, message = "Score cannot be less than 0")
        @Max(value = 100, message = "Score cannot be more than 100")
        Integer score
) {}
