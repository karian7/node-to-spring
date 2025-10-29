package com.example.chatapp.dto;

import com.example.chatapp.validation.ValidEmail;
import com.example.chatapp.validation.ValidName;
import com.example.chatapp.validation.ValidPassword;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @ValidName
    private String name;

    @ValidEmail
    private String email;

    @ValidPassword
    private String password;
}
