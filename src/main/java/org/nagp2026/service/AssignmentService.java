package org.nagp2026.service;

import org.nagp2026.dto.AssignmentRequestDTO;
import org.springframework.stereotype.Service;
import org.nagp2026.repository.AssignmentRepository;
import org.nagp2026.model.Assignment;

import java.util.List;

@Service
public class AssignmentService {
    private final AssignmentRepository repository;

    public AssignmentService(AssignmentRepository repository) {
        this.repository = repository;
    }

    // CREATE: Pass null for the ID so MongoDB generates a new unique ObjectId
    public Assignment persistAssignment(AssignmentRequestDTO requestDTO) {
        Assignment payload = new Assignment(null,
                requestDTO.studentName(),
                requestDTO.topic(),
                requestDTO.score(),
                null);
        return repository.save(payload);
    }

    // READ
    public List<Assignment> getAllRecords() {
        return repository.findAll();
    }

    // UPDATE: Must instantiate a brand-new Record copying over unchanged data
    public Assignment updateScore(String id, Integer newScore) {
        return repository.findById(id).map(existingRecord -> {
            // Re-create the record with the updated score element
            Assignment updatedRecord = new Assignment(
                    existingRecord.id(),
                    existingRecord.studentName(),
                    existingRecord.topic(),
                    newScore, // The new updated value
                    existingRecord.submissionTime()
            );
            return repository.save(updatedRecord);
        }).orElseThrow(() -> new RuntimeException("Assignment not found with id: " + id));
    }

    public List<Assignment> getPassedSubmissions() {
        return repository.findHighScorers(80);
    }

    public List<Assignment> getSubmissionsForStudent(String studentName) {
        return repository.findByStudentName(studentName);
    }

    public List<Assignment> findByTopic(String topic) {
        return repository.findByTopic(topic);
    }

    public List<Assignment> findAssignmentsByMinScore(Integer minScore) {
        return repository.findHighScorers(minScore);
    }
}
