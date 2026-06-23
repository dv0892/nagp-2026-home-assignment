package org.nagp2026.web;

import jakarta.validation.Valid;
import org.nagp2026.dto.AssignmentRequestDTO;
import org.nagp2026.model.Assignment;
import org.nagp2026.service.AssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/assignments")
public class AssignmentController {

    private final AssignmentService service;

    public AssignmentController(AssignmentService service) {
        this.service = service;
    }

    // POST endpoint to handle raw JSON submissions
    @PostMapping
    public ResponseEntity<Assignment> submitAssignment(@Valid @RequestBody AssignmentRequestDTO requestBody) {
        Assignment savedRecord = service.persistAssignment(requestBody);
        return new ResponseEntity<>(savedRecord, HttpStatus.CREATED);
    }

    @GetMapping
    public List<Assignment> fetchAssignments(@RequestParam(required = false) String studentName, @RequestParam(required = false) String topicName,
                                             @RequestParam(required = false) Integer minScore) {
        if (studentName != null) {
            return service.getSubmissionsForStudent(studentName);
        } else if (topicName != null) {
            return service.findByTopic(topicName);
        } else if (minScore != null) {
            return service.findAssignmentsByMinScore(minScore);
        } else
            return service.getAllRecords();
    }
}
