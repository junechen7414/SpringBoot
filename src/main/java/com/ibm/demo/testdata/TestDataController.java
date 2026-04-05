package com.ibm.demo.testdata;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/testdata")
@RequiredArgsConstructor
public class TestDataController {
    private final TestDataService testDataService;

    @PostMapping("/create")
    public ResponseEntity<String> createTestData(@RequestParam Integer count) {
        testDataService.createTestData(count);
        return ResponseEntity.ok("Test data " + count + " records created successfully.");
    }
}
