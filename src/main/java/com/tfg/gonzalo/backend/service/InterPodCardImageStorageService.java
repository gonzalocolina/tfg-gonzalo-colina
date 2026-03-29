package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InterPodCardImageStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private final Path rootDirectory;
    private final long maxImageBytes;

    public InterPodCardImageStorageService(
            @Value("${app.inter-pod-card.images-dir:./data/inter-pod-cards}") String rootDirectory,
            @Value("${app.inter-pod-card.max-image-bytes:5242880}") long maxImageBytes) {
        this.rootDirectory = Paths.get(rootDirectory).toAbsolutePath().normalize();
        this.maxImageBytes = maxImageBytes;
    }

    public StoredImage store(Long eventId, Long cardId, MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        validateImage(imageFile);
        String extension = extensionFrom(imageFile.getOriginalFilename(), imageFile.getContentType());
        String filename = "%d-%s%s".formatted(cardId, UUID.randomUUID(), extension);

        try {
            Path eventDirectory = rootDirectory.resolve(String.valueOf(eventId)).normalize();
            Files.createDirectories(eventDirectory);
            Path destination = eventDirectory.resolve(filename).normalize();
            Files.copy(imageFile.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            String relativePath = Path.of(String.valueOf(eventId), filename).toString();
            return new StoredImage(relativePath, imageFile.getContentType());
        } catch (IOException exception) {
            throw new BusinessRuleException("No se pudo guardar la imagen de la carta inter-pod.");
        }
    }

    public Resource loadAsResource(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BusinessRuleException("La carta inter-pod no tiene imagen asociada.");
        }

        try {
            Path imagePath = rootDirectory.resolve(relativePath).normalize();
            Resource resource = new UrlResource(imagePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessRuleException("No se pudo leer la imagen de la carta inter-pod.");
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw new BusinessRuleException("La ruta de la imagen de la carta inter-pod no es válida.");
        }
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }

        Path imagePath = rootDirectory.resolve(relativePath).normalize();
        try {
            Files.deleteIfExists(imagePath);
        } catch (IOException exception) {
            throw new BusinessRuleException("No se pudo eliminar la imagen de la carta inter-pod.");
        }
    }

    private void validateImage(MultipartFile imageFile) {
        String contentType = imageFile.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessRuleException("Formato de imagen no permitido. Solo se acepta PNG, JPG/JPEG o WEBP.");
        }
        if (imageFile.getSize() > maxImageBytes) {
            throw new BusinessRuleException("La imagen excede el tamaño máximo permitido de 5 MB.");
        }
    }

    private String extensionFrom(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int idx = originalFilename.lastIndexOf('.');
            if (idx >= 0 && idx < originalFilename.length() - 1) {
                return "." + originalFilename.substring(idx + 1).toLowerCase();
            }
        }
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        return ".jpg";
    }

    public record StoredImage(String relativePath, String contentType) {
    }
}