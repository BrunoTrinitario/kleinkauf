package com.kleinkauf.users.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/*
Profile photos on disk, in avatar.path. Every tenant shares the folder: names are random, so one cannot be guessed
from another. They are served publicly at /users/avatars/{file} (StaticResourceConfig).
 */
@Component
public class AvatarStorage {

    @Value("${avatar.path}")
    private String avatarPath;

    // Saves the image and returns the new file name
    public String save(byte[] bytes, String extension) {
        String fileName = UUID.randomUUID() + "." + extension;
        try {
            Path folder = folder();
            Files.createDirectories(folder);
            Files.write(folder.resolve(fileName), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the profile photo", e);
        }
        return fileName;
    }

    // Deletes the file if it exists. Never leaves the folder: only a plain file name is accepted
    public void delete(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.equals(Paths.get(fileName).getFileName().toString())) {
            return;
        }
        try {
            Files.deleteIfExists(folder().resolve(fileName));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete the profile photo", e);
        }
    }

    private Path folder() {
        return Paths.get(avatarPath).toAbsolutePath().normalize();
    }
}
