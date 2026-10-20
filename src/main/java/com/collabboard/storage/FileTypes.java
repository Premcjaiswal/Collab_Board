package com.collabboard.storage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Server-side allow-list of uploadable file types. Teachers choose a subset per assignment/project.
 * The content type sent by the browser is ignored; it is derived from the extension, and the file's
 * leading bytes must match the expected signature for binary formats.
 */
public final class FileTypes {

    private FileTypes() {
    }

    private enum Kind { PDF, ZIP_BASED, OLE, PNG, JPEG, GIF, RAR, SEVEN_Z, GZIP, TAR, TEXT }

    private record Type(String mime, Kind kind) {
    }

    private static final Map<String, Type> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("pdf", new Type("application/pdf", Kind.PDF));
        TYPES.put("doc", new Type("application/msword", Kind.OLE));
        TYPES.put("docx", new Type("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Kind.ZIP_BASED));
        TYPES.put("ppt", new Type("application/vnd.ms-powerpoint", Kind.OLE));
        TYPES.put("pptx", new Type("application/vnd.openxmlformats-officedocument.presentationml.presentation", Kind.ZIP_BASED));
        TYPES.put("xls", new Type("application/vnd.ms-excel", Kind.OLE));
        TYPES.put("xlsx", new Type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Kind.ZIP_BASED));
        TYPES.put("odt", new Type("application/vnd.oasis.opendocument.text", Kind.ZIP_BASED));
        TYPES.put("odp", new Type("application/vnd.oasis.opendocument.presentation", Kind.ZIP_BASED));
        TYPES.put("ods", new Type("application/vnd.oasis.opendocument.spreadsheet", Kind.ZIP_BASED));
        TYPES.put("zip", new Type("application/zip", Kind.ZIP_BASED));
        TYPES.put("rar", new Type("application/vnd.rar", Kind.RAR));
        TYPES.put("7z", new Type("application/x-7z-compressed", Kind.SEVEN_Z));
        TYPES.put("gz", new Type("application/gzip", Kind.GZIP));
        TYPES.put("tar", new Type("application/x-tar", Kind.TAR));
        TYPES.put("png", new Type("image/png", Kind.PNG));
        TYPES.put("jpg", new Type("image/jpeg", Kind.JPEG));
        TYPES.put("jpeg", new Type("image/jpeg", Kind.JPEG));
        TYPES.put("gif", new Type("image/gif", Kind.GIF));
        TYPES.put("ipynb", new Type("application/x-ipynb+json", Kind.TEXT));
        for (String t : new String[]{"txt", "md", "csv", "json", "xml", "java", "py", "c", "cpp", "h", "js", "ts",
                "html", "css", "sql", "kt", "go", "rb", "php", "cs", "r", "m", "sh"}) {
            TYPES.put(t, new Type("text/plain", Kind.TEXT));
        }
    }

    public static Set<String> supported() {
        return Collections.unmodifiableSet(TYPES.keySet());
    }

    /**
     * Validates a teacher-supplied list such as ".pdf, docx" against the server allow-list and returns
     * the normalised CSV ("pdf,docx").
     */
    public static String normalizeAllowed(String csv) {
        Set<String> exts = com.collabboard.common.Texts.parseExtensions(csv);
        if (exts.isEmpty()) {
            throw com.collabboard.common.ApiException.badRequest("FILE_TYPES_REQUIRED", "List at least one permitted file type.");
        }
        java.util.List<String> unsupported = exts.stream().filter(e -> !TYPES.containsKey(e)).toList();
        if (!unsupported.isEmpty()) {
            throw com.collabboard.common.ApiException.badRequest("FILE_TYPES_UNSUPPORTED",
                    "Unsupported file type(s): " + String.join(", ", unsupported) + ". Supported: " + String.join(", ", TYPES.keySet()) + ".");
        }
        return String.join(",", exts);
    }

    public static boolean isSupported(String ext) {
        return TYPES.containsKey(ext);
    }

    public static String mimeFor(String ext) {
        Type t = TYPES.get(ext);
        return t == null ? "application/octet-stream" : t.mime();
    }

    /** Checks the first bytes of the file against the signature expected for the extension. */
    public static boolean contentMatches(String ext, byte[] head, int len) {
        Type t = TYPES.get(ext);
        if (t == null) {
            return false;
        }
        byte[] h = Arrays.copyOf(head, len);
        return switch (t.kind()) {
            case PDF -> startsWith(h, "%PDF".getBytes(StandardCharsets.US_ASCII));
            case ZIP_BASED -> startsWith(h, new byte[]{0x50, 0x4B, 0x03, 0x04}) || startsWith(h, new byte[]{0x50, 0x4B, 0x05, 0x06});
            case OLE -> startsWith(h, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});
            case PNG -> startsWith(h, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
            case JPEG -> startsWith(h, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case GIF -> startsWith(h, "GIF8".getBytes(StandardCharsets.US_ASCII));
            case RAR -> startsWith(h, "Rar!".getBytes(StandardCharsets.US_ASCII));
            case SEVEN_Z -> startsWith(h, new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C});
            case GZIP -> startsWith(h, new byte[]{0x1F, (byte) 0x8B});
            case TAR -> h.length >= 262 && new String(h, 257, 5, StandardCharsets.US_ASCII).equals("ustar");
            case TEXT -> isText(h);
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isText(byte[] data) {
        for (byte b : data) {
            if (b == 0) {
                return false;
            }
        }
        return true;
    }
}
