package com.collabboard.storage;

import java.time.Instant;

public record FileDto(Long id, String name, String contentType, long sizeBytes, String uploadedBy,
                      Instant uploadedAt, String downloadUrl) {

    public static FileDto of(StoredFile f) {
        return new FileDto(f.getId(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(),
                f.getUploadedBy().getFullName(), f.getUploadedAt(), "/api/files/" + f.getId() + "/download");
    }
}
