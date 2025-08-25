package com.example.chatapp.dto;

import com.example.chatapp.validation.ValidEmail;
import com.example.chatapp.validation.ValidName;
import com.example.chatapp.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "이름을 입력해주세요.")
    @ValidName(message = "이름은 2자 이상이어야 합니다.")
    private String name;

    @NotBlank(message = "이메일을 입력해주세요.")
    @ValidEmail(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @ValidPassword(message = "비밀번호는 6자 이상이어야 합니다.")
    private String password;
}
