package dev.technics.pods.art;

import java.util.Set;

public record Az100ArtImportResult(Status status, String message, Set<String> slots) {
    public enum Status { SUCCESS, MAPPING_REQUIRED, APP_MISSING, SOURCE_MISMATCH, RESOURCE_MISSING, FAILED }
    public Az100ArtImportResult { slots = Set.copyOf(slots); }
    public boolean success() { return status == Status.SUCCESS; }
    public static Az100ArtImportResult failure(Status status, String message) {
        return new Az100ArtImportResult(status, message, Set.of());
    }
}
