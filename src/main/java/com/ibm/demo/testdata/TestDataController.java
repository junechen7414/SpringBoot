package com.ibm.demo.testdata;

import org.springframework.context.annotation.Profile;
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

    @Profile({ "dev" })
    @PostMapping("/createJMeterPrecondition")
    public ResponseEntity<String> createJMeterPrecondition(@RequestParam Integer count) {
        testDataService.createOrderPrecondition(count);
        return ResponseEntity.ok(count + "Account and Product record created successfully.");
    }

    @Profile({ "test" })
    @PostMapping("/createOrderPrecondition")
    public ResponseEntity<String> createOrderPrecondition(@RequestParam Integer count) {
        testDataService.createOrderPrecondition(count);
        return ResponseEntity.ok(count + "Account and Product record created successfully.");
    }
}
