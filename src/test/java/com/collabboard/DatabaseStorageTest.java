package com.collabboard;

import com.collabboard.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** STORAGE_TYPE=database (hosts without a persistent disk): same validation and access rules, contents in MySQL. */
@TestPropertySource(properties = {"app.storage.type=database", "app.storage.root=target/test-db-storage-tmp"})
class DatabaseStorageTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void uploadsAreKeptInTheDatabaseAndDownloadUnchanged() throws Exception {
        Account t = teacher("dbs");
        long c = createClass(t, "DB storage");
        Account s = student("dbs");
        join(s, joinCode(t, c));
        long a = createAssignment(t, c, true, false);
        byte[] content = ("%PDF-1.4\n% database storage test\n%%EOF").getBytes(StandardCharsets.US_ASCII);
        JsonNode sub = ok(upload("/api/assignments/" + a + "/submissions", s.session(), null,
                new MockMultipartFile("files", "report.pdf", "application/pdf", content)));
        JsonNode file = sub.get("versions").get(0).get("files").get(0);

        String storedName = jdbc.queryForObject("SELECT stored_name FROM stored_files WHERE id = ?", String.class,
                file.get("id").asLong());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_blobs WHERE stored_name = ?", Integer.class, storedName))
                .isEqualTo(1);
        assertThat(Files.exists(Path.of("target/test-db-storage-tmp", storedName))).isFalse();
        try (var leftovers = Files.list(Path.of("target/test-db-storage-tmp"))) {
            assertThat(leftovers.filter(p -> p.getFileName().toString().endsWith(".tmp"))).isEmpty();
        }

        byte[] downloaded = mvc.perform(get(file.get("downloadUrl").asText()).session(s.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(content);
        Account outsider = student("dbs-out");
        mvc.perform(get(file.get("downloadUrl").asText()).session(outsider.session())).andExpect(status().isNotFound());
    }

    @Test
    void validationStillAppliesBeforeAnythingIsStored() throws Exception {
        Account t = teacher("dbv");
        long c = createClass(t, "DB validation");
        Account s = student("dbv");
        join(s, joinCode(t, c));
        long a = createAssignment(t, c, true, false);
        Integer before = jdbc.queryForObject("SELECT COUNT(*) FROM file_blobs", Integer.class);
        upload("/api/assignments/" + a + "/submissions", s.session(), Map.of(),
                new MockMultipartFile("files", "fake.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.US_ASCII)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_CONTENT_MISMATCH"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_blobs", Integer.class)).isEqualTo(before);
    }
}
