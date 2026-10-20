-- File contents for STORAGE_TYPE=database, used on hosts without a persistent disk (e.g. Render's free tier).
-- With the default STORAGE_TYPE=filesystem this table stays empty and files remain on disk under STORAGE_ROOT.
CREATE TABLE file_blobs (
    stored_name     VARCHAR(80)  NOT NULL,
    content         LONGBLOB     NOT NULL,
    CONSTRAINT pk_file_blobs PRIMARY KEY (stored_name),
    CONSTRAINT fk_file_blobs_file FOREIGN KEY (stored_name) REFERENCES stored_files (stored_name) ON DELETE CASCADE
);
