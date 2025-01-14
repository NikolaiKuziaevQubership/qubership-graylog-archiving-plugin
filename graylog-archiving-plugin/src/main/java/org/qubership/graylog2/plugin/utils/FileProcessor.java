package org.qubership.graylog2.plugin.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Singleton
public class FileProcessor {

    private static final Logger log = LoggerFactory.getLogger(FileProcessor.class);

    public static final String JSON = ".json";

    public boolean checkFileExisting(String path, String archiveName) {
        return Files.exists(Paths.get(path, archiveName + JSON));
    }

    public void createInfoFile(String path, String archiveName, String archiveInfo) {
        try {
            Files.write(Paths.get(path, archiveName + JSON), archiveInfo.getBytes());
        } catch (IOException e) {
            log.error("Error during writing info-file: " + e.getMessage(), e);
            throw new RuntimeException("Error during writing info-file." + "\n" +
                    "Reason: " + e.getMessage(), e);
        }
    }

    public String readInfoFile(String path, String archiveName) {
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(path, archiveName + JSON));
            return new String(encoded);
        } catch (Exception e) {
            log.error("Error during reading info-file: " + e.getMessage(), e);
            throw new RuntimeException("Error during reading info-file." + "\n" +
                    "Reason: " + e.getMessage(), e);
        }
    }
}
