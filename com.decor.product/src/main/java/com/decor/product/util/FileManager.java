package com.decor.product.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FileManager {
    public static List<String> SaveAllImages(List<MultipartFile> files){
        List<String> paths = new ArrayList<>();
        for (MultipartFile file : files){
            paths.add(saveImage(file));
        }
        return paths;
    }
    private static String saveImage(MultipartFile file){
        try {
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path path = Paths.get("uploads/" + filename);

            Files.createDirectories(path.getParent());
            Files.write(path, file.getBytes());

            return path.toString();
        } catch (IOException e) {
            throw new RuntimeException("Error guardando archivo");
        }
    }

    public static void deleteImage(String path){
        try {
            Path filePath = Paths.get(path);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Error eliminando archivo");
        }
    }
}
