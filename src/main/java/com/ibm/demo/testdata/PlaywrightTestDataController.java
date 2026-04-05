package com.ibm.demo.testdata;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/PlaywrightTestData")
@RequiredArgsConstructor
@Profile("test")
public class PlaywrightTestDataController {
    private final TestDataService testDataService;

    @PostMapping("/createOrderPrecondition")
    public ResponseEntity<String> createOrderPrecondition(@RequestParam Integer count) {
        testDataService.createOrderPrecondition(count);
        return ResponseEntity.ok(count + "Account and Product record created successfully.");
    }
}
