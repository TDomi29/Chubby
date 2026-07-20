package hu.chubby.chunkloader;

import org.bukkit.Chunk;

public record ChunkKey(String worldName, int x, int z) {
    public static ChunkKey of(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public String configKey() {
        return worldName + ";" + x + ";" + z;
    }

    public static ChunkKey parse(String value) {
        String[] parts = value.split(";", 3);
        if (parts.length != 3) throw new IllegalArgumentException("Invalid chunk key: " + value);
        return new ChunkKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
}
