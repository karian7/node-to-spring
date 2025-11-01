package com.ktb.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 검증 에러 정보 DTO.
 * errors: [ { field: "email", message: "..." } ]
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {
    private String field;
    private String message;
}
