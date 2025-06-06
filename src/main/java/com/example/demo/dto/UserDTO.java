package com.example.demo.dto;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


import java.util.UUID;


@Getter
@Setter
@ToString

public class UserDTO {
    private UUID role_id;
    private UUID id;
    private String email;
    private String password;
    private String full_name;
    private String roleName;
    private String phone;
}
