package io.github.wizard_arena;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.util.ArrayList;
import java.util.List;

public class MapLoader {
    private static final int TILE_SIZE = 64;

    public static List<SpawnPoint> loadSpawnPoints(String mapFilePath) {
        List<SpawnPoint> spawnPoints = new ArrayList<>();
        FileHandle file = Gdx.files.internal(mapFilePath);
        String text = file.readString();
        String[] lines = text.split("\\r?\\n");

        for (int row = 0; row < lines.length; row++) {
            String line = lines[row];
            for (int col = 0; col < line.length(); col++) {
                char tileChar = line.charAt(col);
                if (tileChar == 'P') { // P is a player spawn point
                    float worldX = col * TILE_SIZE;
                    float worldY = row * TILE_SIZE;
                    spawnPoints.add(new SpawnPoint(worldX, worldY));
                }
            }
        }
        if (spawnPoints.isEmpty()) {
            System.out.println("No spawn points found in map file: " + mapFilePath);
        } else {
            System.out.println("Loaded " + spawnPoints.size() + " spawn points from map file: " + mapFilePath);
        }
        return spawnPoints;
    }
}
