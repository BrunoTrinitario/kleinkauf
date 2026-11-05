package com.kleinkauf.product.util;

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

    /*
    An image's extension according to its first bytes (not the name or the Content-Type, which the client picks):
    png, jpg, webp or gif. null if it is none of them (SVG is not accepted either: it can carry scripts).
    The extension decides the Content-Type it is served with in /products/images/.
     */
    public static String imageExtension(byte[] bytes) {
        if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return "png";
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) return "jpg";
        if (startsWith(bytes, 0, 'G', 'I', 'F', '8')) return "gif";
        if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) return "webp";
        return null;
    }

    private static boolean startsWith(byte[] bytes, int offset, int... signature) {
        if (bytes == null || bytes.length < offset + signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[offset + i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }

    private String saveImage(MultipartFile file){
        try {
            byte[] bytes = file.getBytes();
            String extension = imageExtension(bytes);
            if (extension == null) {
                throw new IllegalArgumentException("No es una imagen valida");
            }
            String filename = UUID.randomUUID() + "_" + System.currentTimeMillis() + "." + extension;

            Path uploadPath = Paths.get(imagePath).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            Path filePath = uploadPath.resolve(filename);
            Files.write(filePath, bytes);

            return filePath.toString();
        } catch (IOException e) {
            throw new RuntimeException("Error guardando archivo");
        }
    }

    public void deleteImage(String path){
        try {
            Path uploadPath = Paths.get(imagePath).toAbsolutePath().normalize();
            Path filePath = uploadPath.resolve(path).normalize();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Error eliminando archivo");
        }
    }
}
