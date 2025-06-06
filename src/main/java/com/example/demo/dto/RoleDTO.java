package com.example.demo.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
    import lombok.Getter;

import java.util.UUID;


@Getter
@Setter
@ToString
public class RoleDTO {
    private UUID id;
    private String name;

}
