package com.example.chatapp.controller;

import com.example.chatapp.dto.FileUploadResponse;
import com.example.chatapp.model.File;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.FileRepository;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.security.Principal;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file, Principal principal) {
        String fileName = fileService.storeFile(file);

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

        File dbFile = new File();
        dbFile.setFilename(fileName);
        dbFile.setOriginalname(file.getOriginalFilename());
        dbFile.setMimetype(file.getContentType());
        dbFile.setSize(file.getSize());
        dbFile.setUserId(user.getId());
        dbFile.setPath(fileService.loadFileAsResource(fileName).toString()); // This should be the path

        fileRepository.save(dbFile);

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/files/download/")
                .path(fileName)
                .toUriString();

        FileUploadResponse response = new FileUploadResponse(
                fileName,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                fileDownloadUri
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request) {
        Resource resource = fileService.loadFileAsResource(fileName);

        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            // fallback to the default content type if type could not be determined
        }

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
