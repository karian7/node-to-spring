package com.ktb.chatapp.dto;

import com.ktb.chatapp.validation.ValidEmail;
import com.ktb.chatapp.validation.ValidName;
import com.ktb.chatapp.validation.ValidPassword;
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
