package io.github.wizard_arena.screens;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import io.github.wizard_arena.Player;
import io.github.wizard_arena.Tile;
import io.github.wizard_arena.net.*;
import io.github.wizard_arena.wizardGame;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.github.wizard_arena.net.Messages.*;

public class PlayScreen extends ScreenAdapter {
    private final wizardGame game;
    private final boolean isHost;
    private ListenServer server;
    private Client client;
    private Player player;
    private boolean hudOpen;


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
    }

    @Override
    public void show() {
        // do later
    }

    @Override
    public void render(float delta) {
        // check connection status before anything
        if (client == null || client.isConnected() || client.clientId < 0) {
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            // if disconnected i.e. server crashed or host closed server, return to main menu
            Gdx.app.postRunnable(() -> game.setScreen(new MainMenuScreen(game)));
            return;
        }

        updateCamera();
        game.batch.setProjectionMatrix(camera.combined);

        player.update(hudOpen);

        // process incoming messages
        for (Msg m; (m = client.pollMessage()) != null; ) {
            if (m instanceof Snapshot) {

                // TODO: update local render states from snapshot
                // copy snap players and projectiles to the map
            }
        }

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // TODO: draw players and projectiles
        game.batch.begin();

        for(int i = 0 ; i < map_height ; i++) {
            for(int j = 0; j < map_width; j++) {
                tiles[i][j].render(game.batch);
            }
        }
        player.draw(game.batch);

        game.batch.end();
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

        float world_height = map_height * 64;
        float world_width = map_width * 64;

        float half_camera_height = camera.viewportHeight / 2;
        float half_camera_width = camera.viewportWidth / 2;


        pX = MathUtils.clamp(pX, half_camera_width, world_width - half_camera_width);
        pY = MathUtils.clamp(pY, half_camera_height, world_height - half_camera_height);


        camera.position.set(pX,pY, 0);
        camera.update();
    }


}
