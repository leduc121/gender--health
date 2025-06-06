package com.example.demo.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    @GetMapping("/customer/test")
    public ResponseEntity<?> customerTest() {
        return ResponseEntity.ok("This is a customer-only endpoint!");
    }

    @GetMapping("/staff/test")
    public ResponseEntity<?> staffTest() {
        return ResponseEntity.ok("This is a staff-only endpoint!");
    }

    @GetMapping("/admin/test")
    public ResponseEntity<?> adminTest() {
        return ResponseEntity.ok("This is an admin-only endpoint!");
    }
}