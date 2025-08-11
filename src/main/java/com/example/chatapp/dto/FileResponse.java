package com.example.chatapp.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileResponse {
    private String id;
    private String filename;
    private String originalname;
    private String mimetype;
    private Long size;
}
