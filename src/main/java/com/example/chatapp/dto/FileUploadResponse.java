package com.example.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private String filename;
    private String originalname;
    private String mimetype;
    private long size;
    private String url;
    private boolean ragProcessed;

    // 기존 호환성을 위한 생성자
    public FileUploadResponse(String filename, String originalname, String mimetype, long size, String url) {
        this.filename = filename;
        this.originalname = originalname;
        this.mimetype = mimetype;
        this.size = size;
        this.url = url;
        this.ragProcessed = false;
    }
}
