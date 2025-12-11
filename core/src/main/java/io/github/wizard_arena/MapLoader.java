package io.github.wizard_arena;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.util.ArrayList;
import java.util.List;

public class MapLoader {
    private static final int TILE_SIZE = 64;

    public static class MapSpawns {
        public final List<SpawnPoint> playerSpawns = new ArrayList<>();
        public final List<SpawnPoint> spellSpawns = new ArrayList<>();
        public final List<SpawnPoint> powerupSpawns = new ArrayList<>();
    }

    public static MapSpawns loadSpawns(String mapFilePath) {
        MapSpawns spawns = new MapSpawns();
        FileHandle file = Gdx.files.internal(mapFilePath);
        String text = file.readString();
        String[] lines = text.split("\\r?\\n");

        for (int row = 0; row < lines.length; row++) {
            int flippedRow = lines.length - 1 - row;
            String line = lines[row];
            for (int col = 0; col < line.length(); col++) {
                char tileChar = line.charAt(col);
                float worldX = col * TILE_SIZE;
                float worldY = flippedRow * TILE_SIZE;

                switch (tileChar) {
                    case 'P':
                        spawns.playerSpawns.add(new SpawnPoint(worldX, worldY));
                        break;
                    case 'S':
                        spawns.spellSpawns.add(new SpawnPoint(worldX, worldY));
                        break;
                    case 'U':
                        spawns.powerupSpawns.add(new SpawnPoint(worldX, worldY));
                        break;
                    default:
                        break;
                }
            }
        }
        System.out.println("Loaded spawns from map:" + mapFilePath);
        System.out.println("Player spawns: " + spawns.playerSpawns.size());
        System.out.println("Spell spawns: " + spawns.spellSpawns.size());
        System.out.println("Powerup spawns: " + spawns.powerupSpawns.size());
        return spawns;
    }

    public static List<SpawnPoint> loadSpawnPoints(String mapFilePath) {
        return loadSpawns(mapFilePath).playerSpawns;
    }
}
