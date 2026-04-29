package com.decor.product.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FileManager {

    @Value("${image.path}")
    private String imagePath;

    public List<String> SaveAllImages(List<MultipartFile> files){
        List<String> paths = new ArrayList<>();
        for (MultipartFile file : files){
            paths.add(saveImage(file));
        }
        return paths;
    }
    private String saveImage(MultipartFile file){
        try {
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();

            Path uploadPath = Paths.get(imagePath).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            Path filePath = uploadPath.resolve(filename);
            Files.write(filePath, file.getBytes());

            return filePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Error guardando archivo");
        }
    }

    public void deleteImage(String path){
        try {
            Path filePath = Paths.get(path);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Error eliminando archivo");
        }
    }
}
