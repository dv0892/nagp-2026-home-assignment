package org.nagp2026.repository;

import org.nagp2026.model.Assignment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AssignmentRepository extends MongoRepository<Assignment, String> {

    // 1. Derived Query Method (Spring auto-builds parser logic based on method naming)
    List<Assignment> findByStudentName(String studentName);

    List<Assignment> findByTopic(String topic);

    // 2. Custom JSON Query Filter
    @Query("{ 'score' : { $gte : ?0 } }")
    List<Assignment> findHighScorers(Integer minimumScore);
}
