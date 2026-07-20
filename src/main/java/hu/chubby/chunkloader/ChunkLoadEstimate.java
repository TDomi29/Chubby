package hu.chubby.chunkloader;

public record ChunkLoadEstimate(boolean available, int entities, int hoppers, int tileEntities, int score) {
    public String grade() {
        if (!available) return "unavailable";
        if (score >= 80) return "critical";
        if (score >= 40) return "high";
        return "normal";
    }
}
