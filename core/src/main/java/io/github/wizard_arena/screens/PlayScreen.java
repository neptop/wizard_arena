package io.github.wizard_arena.screens;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import io.github.wizard_arena.Player;
import io.github.wizard_arena.net.*;
import io.github.wizard_arena.wizardGame;

import static io.github.wizard_arena.net.Messages.*;

public class PlayScreen extends ScreenAdapter {
    private final wizardGame game;
    private final boolean isHost;
    private ListenServer server;
    private Client client;
    private Player player;
    private boolean hudOpen;
    // host constructor
    public PlayScreen(wizardGame game, ListenServer server, Client client) {
        this.game = game;
        this.isHost = true;
        this.server = server;
        this.client = client;

        hudOpen = false;
        player = new Player(game, 150f, client);
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
        player = new Player(game, 150f, client);
    }

    // already connected constructor
    public PlayScreen(wizardGame game, Client client) {
        this.game = game;
        this.isHost = false;
        this.client = client;
        this.server = null;

        player = new Player(game, 150f, client);
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
}
