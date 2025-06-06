package com.example.demo.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@Setter
@Getter
@ToString
public class ValidationError {
    private String field;
    private String message;


    public ValidationError(String field, String message) {
        this.field = field;
        this.message = message;
    }

}
