package org.nagp2026.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.LocalDateTime;

@Document(collection = "assignments")
public record Assignment(
        @Id
        String id,

        @Field("student_name")
        String studentName,

        String topic,
        Integer score,
        LocalDateTime submissionTime
) {
    // Optional: Compact constructor to apply default values
    public Assignment {
        if (submissionTime == null) {
            submissionTime = LocalDateTime.now();
        }
    }
}
