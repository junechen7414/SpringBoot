package com.ibm.demo.testdata;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/JMeterTestData")
@RequiredArgsConstructor
@Profile("dev")
public class JMeterTestDataController {
    private final TestDataService testDataService;

    @PostMapping("/create")
    public ResponseEntity<String> createJMeterPrecondition(@RequestParam Integer count) {
        testDataService.createOrderPrecondition(count);
        return ResponseEntity.ok(count + "Account and Product record created successfully.");
    }

}
