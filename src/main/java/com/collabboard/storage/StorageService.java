package com.collabboard.storage;

import com.collabboard.common.ApiException;
import com.collabboard.common.Texts;
import com.collabboard.config.AppProperties;
import com.collabboard.submission.WorkItem;
import com.collabboard.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded files under random names. Original names are kept only as metadata and are never used to build
 * filesystem paths. By default contents live on disk outside the web root; with {@code STORAGE_TYPE=database} they are
 * kept in the {@code file_blobs} table instead (for hosts without a persistent disk). Validation is identical.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private static final int HEAD_BYTES = 8192;

    private final Path root;
    private final StoredFileRepository repository;
    private final JdbcTemplate jdbc;
    private final boolean inDatabase;

    public StorageService(AppProperties props, StoredFileRepository repository, JdbcTemplate jdbc) throws IOException {
        // In database mode the root only holds short-lived temp files while an upload is validated.
        this.root = Path.of(props.storage().root()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        this.repository = repository;
        this.jdbc = jdbc;
        this.inDatabase = "database".equalsIgnoreCase(props.storage().type());
        log.info(inDatabase ? "File storage: database (temp files under {})" : "File storage root: {}", root);
    }

    public StoredFile store(MultipartFile upload, WorkItem rules, FileContextType contextType, Long contextId, User uploader) {
        if (upload == null || upload.isEmpty()) {
            throw ApiException.badRequest("FILE_REQUIRED", "Please choose a non-empty file to upload.");
        }
        String originalName = sanitizeName(upload.getOriginalFilename());
        String ext = extensionOf(originalName);
        Set<String> allowed = Texts.parseExtensions(rules.getAllowedExtensions());
        if (ext.isEmpty() || !allowed.contains(ext) || !FileTypes.isSupported(ext)) {
            throw ApiException.badRequest("FILE_TYPE_NOT_ALLOWED",
                    "\"" + originalName + "\" is not an allowed file type. Allowed: " + String.join(", ", allowed) + ".");
        }
        long maxBytes = (long) rules.getMaxFileSizeMb() * 1024 * 1024;
        if (upload.getSize() > maxBytes) {
            throw ApiException.badRequest("FILE_TOO_LARGE",
                    "\"" + originalName + "\" exceeds the " + rules.getMaxFileSizeMb() + " MB limit.");
        }

        String storedName = UUID.randomUUID().toString().replace("-", "");
        Path target = resolve(storedName);
        Path temp = null;
        try (InputStream in = upload.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            temp = Files.createTempFile(root, "upload-", ".tmp");
            byte[] head = new byte[HEAD_BYTES];
            int headLen;
            long total = 0;
            try (DigestInputStream din = new DigestInputStream(in, digest);
                 OutputStream out = Files.newOutputStream(temp)) {
                headLen = din.readNBytes(head, 0, HEAD_BYTES);
                out.write(head, 0, headLen);
                total += headLen;
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = din.read(buf)) != -1) {
                    total += r;
                    if (total > maxBytes) {
                        throw ApiException.badRequest("FILE_TOO_LARGE",
                                "\"" + originalName + "\" exceeds the " + rules.getMaxFileSizeMb() + " MB limit.");
                    }
                    out.write(buf, 0, r);
                }
            }
            if (!FileTypes.contentMatches(ext, head, headLen)) {
                throw ApiException.badRequest("FILE_CONTENT_MISMATCH",
                        "\"" + originalName + "\" does not look like a valid ." + ext + " file.");
            }
            StoredFile meta = new StoredFile(originalName, storedName, FileTypes.mimeFor(ext), total,
                    HexFormat.of().formatHex(digest.digest()), contextType, contextId, uploader);
            if (inDatabase) {
                byte[] content = Files.readAllBytes(temp);
                StoredFile saved = repository.saveAndFlush(meta); // the blob row references stored_files
                jdbc.update("INSERT INTO file_blobs (stored_name, content) VALUES (?, ?)", storedName, content);
                return saved;
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            temp = null;
            return repository.save(meta);
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Failed to store upload: {}", e.toString());
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                    "The file could not be saved. Please try again.");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
        }
    }

    public Resource load(StoredFile file) {
        if (inDatabase) {
            List<byte[]> content = jdbc.query("SELECT content FROM file_blobs WHERE stored_name = ?",
                    (rs, i) -> rs.getBytes(1), file.getStoredName());
            if (content.isEmpty()) {
                throw ApiException.notFound("File");
            }
            return new ByteArrayResource(content.get(0));
        }
        Path path = resolve(file.getStoredName());
        if (!Files.isRegularFile(path)) {
            throw ApiException.notFound("File");
        }
        return new PathResource(path);
    }

    private Path resolve(String storedName) {
        if (!storedName.matches("^[a-f0-9]{32}$")) {
            throw ApiException.notFound("File");
        }
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root)) {
            throw ApiException.notFound("File");
        }
        return p;
    }

    static String sanitizeName(String raw) {
        String name = raw == null ? "" : raw;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "file";
        }
        if (name.length() > 200) {
            String ext = extensionOf(name);
            name = name.substring(0, 190) + (ext.isEmpty() ? "" : "." + ext);
        }
        return name;
    }

    static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
