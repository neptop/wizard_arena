package io.github.wizard_arena.screens;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.Input;
import io.github.wizard_arena.Player;
import io.github.wizard_arena.Tile;
import io.github.wizard_arena.net.*;
import io.github.wizard_arena.wizardGame;
import io.github.wizard_arena.debug.CheatConsole;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static io.github.wizard_arena.net.Messages.*;

public class PlayScreen extends ScreenAdapter {
    private final wizardGame game;
    private final boolean isHost;
    private ListenServer server;
    private Client client;
    private Player player;
    private boolean hudOpen;
    private Map<Integer, Player> playerSprites = new HashMap<>(); // clientId to Player sprite


    // map stuff
    private Tile[][] tiles;
    private int map_height;
    private int map_width;
    Texture floor;
    Texture wall;
    private int playerSpawnX;
    private int playerSpawnY;
    final private int spriteSize = 64;  //all sprites and tile sizes are 64x64

    //  camera setup
    private OrthographicCamera camera;

    // cheat console
    private CheatConsole console;

    // host constructor
    public PlayScreen(wizardGame game, ListenServer server, Client client) {
        this.game = game;
        this.isHost = true;
        this.server = server;
        this.client = client;

        hudOpen = false;


        initialize();
    }

    // client constructor
    public PlayScreen(wizardGame game, String hostIP, String playerName) {
        this.game = game;
        this.isHost = false;
        try {
            client = new Client();
            client.connect(hostIP, NetConfig.TCP_PORT, playerName);
        } catch (Exception e) {
            e.printStackTrace();
            Gdx.app.postRunnable(() -> game.setScreen(new MainMenuScreen(game)));
        }

        initialize();
    }

    // already connected constructor
    public PlayScreen(wizardGame game, Client client) {
        this.game = game;
        this.isHost = false;
        this.client = client;
        this.server = null;


        initialize();
    }

    public void initialize() {


        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        wall = new Texture(Gdx.files.internal("wall_tile.png"));
        floor = new Texture(Gdx.files.internal("floor_tile.png"));
        FileHandle file;
        file = Gdx.files.internal("map1.txt");
        String text = file.readString();

        String[] lines = text.split("\\r?\\n");

        List<String> list = Arrays.asList(lines);
        Collections.reverse(list);

        map_height = lines.length;
        map_width = lines[0].length();

        tiles = new Tile[map_height][map_width];


        for(int i = 0; i < map_height  ; i++) {
            String line = lines[i];
            for(int j = 0 ; j < map_width ; j++) {
                char c = line.charAt(j);
                Texture curr_tile;
                if(c == 'P') {
                    playerSpawnX = j;
                    playerSpawnY = i;
                    curr_tile = floor;
                } else if (c == 'X' ) {
                    curr_tile = wall;
                }
                else {
                    curr_tile = floor;
                }
                tiles[i][j] = new Tile(game, i, j, curr_tile, (c == 'P' || c == 'O') );
            }
        }

        player = new Player(game, 300f, client, playerSpawnX, playerSpawnY);
        if (client != null) {
            playerSprites.put(client.clientId, player);
        }

        // setup cheat console
        console = new CheatConsole();
        console.setExecutor(this::executeCheat);
    }

    @Override
    public void show() {
        // do later
    }

    @Override
    public void render(float delta) {
        // toggle console with tilde (`) or F1
        if (Gdx.input.isKeyJustPressed(Input.Keys.GRAVE) || Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            if (console.isVisible()) {
                console.hide();
            } else {
                console.show();
            }
        }


        // check connection status before anything
        if (client == null || !client.isConnected() || client.clientId < 0) {
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            // if disconnected i.e. server crashed or host closed server, return to main menu
            Gdx.app.postRunnable(() -> game.setScreen(new MainMenuScreen(game)));
            return;
        }

        // pass whether the console is open to player
        player.update(console != null && console.isVisible());

        // process incoming messages
        for (Msg m; (m = client.pollMessage()) != null; ) {
            if (m instanceof Snapshot) {
                Snapshot s = (Snapshot) m;
                Set<Integer> updatedPlayerIds = new HashSet<>();

                for (PlayerState ps : s.players) {
                    updatedPlayerIds.add(ps.id);
                    Player p = playerSprites.get(ps.id);
                    if (p == null) {
                        // create new player sprite
                        if (ps.id == client.clientId) {
                            p = player; // use local player instance
                            playerSprites.put(ps.id, p);
                        } else {
                            // remote player
                            p = new Player(game, 300f, null, playerSpawnX, playerSpawnY);
                            playerSprites.put(ps.id, p);
                        }
                    }

                    if (ps.id == client.clientId) {
                        // this is the local player, so apply server reconciliation
                        p.applyServerState(ps.x, ps.y, ps.lastProcessedInput);
                        p.setHealth(ps.hp); // update health
                    } else {
                        // remote player, directly set position
                        p.startInter(ps.x, ps.y);
                        p.setHealth(ps.hp); // update health
                    }

                    // debug: check what client thinks is applying the snapshot
                    //System.out.println("applied snapshot: id=" + ps.id + " pos=(" + ps.x + ", " + ps.y + ") hp=" + ps.hp);

                }
                // remove players that aren't in the snapshot i.e. disconnected
                playerSprites.entrySet().removeIf(e -> !updatedPlayerIds.contains(e.getKey()));
            }
        }

        player.update(hudOpen);

        // update interpolation for remote players
        for (Player p : playerSprites.values()) {
            if (p != player) {
                p.updateRemote(delta);
            }
        }

        updateCamera();
        game.batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // TODO: draw players and projectiles
        game.batch.begin();

        for(int i = 0 ; i < map_height ; i++) {
            for(int j = 0; j < map_width; j++) {
                tiles[i][j].render(game.batch);
            }
        }
        for (Player p : playerSprites.values()) {
            p.draw(game.batch);
        }

        game.batch.end();

        // render console on top
        if (console != null) {
            console.render(delta);
        }
    }

    @Override
    public void resize(int width, int height) {
        // do later
    }

    @Override
    public void dispose() {
        if (client != null) {
            client.disconnect();
        }
        if (isHost && server != null) {
            server.stop();
        }
    }

    public void updateCamera() {

        float pX = player.getX();
        float pY = player.getY();

        // temp debug
        //System.out.println("client, cam tracking player at: (" + pX + ", " + pY + ")");

        float world_height = map_height * 64;
        float world_width = map_width * 64;

        float half_camera_height = camera.viewportHeight / 2;
        float half_camera_width = camera.viewportWidth / 2;


        pX = MathUtils.clamp(pX, half_camera_width, world_width - half_camera_width);
        pY = MathUtils.clamp(pY, half_camera_height, world_height - half_camera_height);


        camera.position.set(pX,pY, 0);
        camera.update();
    }

    private void executeCheat(String cmd) {
        if (console == null) return;

        String line = cmd.trim();
        if (line.isEmpty()) return;

        String lower = line.toLowerCase();

        // help cmd
        if (lower.equals("help")) {
            console.print("Available commands:");
            console.print("help - show this help message");
            console.print("god - player takes no damage (toggle)");
            console.print("heal - restore player health to max");
            console.print("tkdmg <amount> - take <amount> damage");
            return;
        }

        // god mode toggle
        if (lower.equals("god")) {
            if (player == null) {
                console.print("player not initialized");
                return;
            }

            boolean enable = !player.isGodMode();

            if (client != null && client.clientId > 0){
                try {
                    client.send(new GodmodeRequest(client.clientId, enable));
                } catch (Exception e) {
                    console.print("failed to send godmode request: " + e.getMessage());
                }
            }

            player.setGodMode(enable);
            console.print("god mode enabled");
            return;
        }

        // heal to max health
        if (lower.equals("heal")) {
            if (client != null && client.clientId > 0){
                try {
                    client.send(new HealRequest(client.clientId, 100f));
                } catch (Exception e) {
                    console.print("failed to send heal request: " + e.getMessage());
                }
            }
            console.print("player healed to max health");
            return;
        }

        // take damage
        if (lower.startsWith("tkdmg ")) {
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                console.print("usage: tkdmg <amount>");
                return;
            }
            try {
                float amount = Float.parseFloat(parts[1]);
                if (amount < 0) {
                    console.print("amount must be non-negative");
                    return;
                }
                if (client != null && client.clientId > 0){
                    try {
                        client.send(new DamageRequest(client.clientId, amount));
                    } catch (Exception e) {
                        console.print("failed to send damage request: " + e.getMessage());
                    }
                }
                console.print("player took " + amount + " damage");
            } catch (NumberFormatException e) {
                console.print("invalid amount: " + parts[1]);
            }
            return;
        }
        console.print("unknown command: " + line);
    }
}
