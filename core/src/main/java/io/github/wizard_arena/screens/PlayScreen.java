package io.github.wizard_arena.screens;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.Input;
import io.github.wizard_arena.net.*;
import io.github.wizard_arena.wizardGame;

import static io.github.wizard_arena.net.Messages.*;

public class PlayScreen extends ScreenAdapter {
    private final wizardGame game;
    private final boolean isHost;
    private ListenServer server;
    private Client client;

    public PlayScreen(wizardGame game, boolean isHost, String hostIP) {
        this.game = game;
        this.isHost = isHost;

        try {
            if (isHost){
                server = new ListenServer(NetConfig.TCP_PORT);
                server.start();
            }
            client = new Client();
            client.connect(isHost ? "127.0.0.1" : hostIP, NetConfig.TCP_PORT, System.getProperty("user.name", "Player"));
        } catch (Exception e) {
            e.printStackTrace();
            Gdx.app.postRunnable(() -> game.setScreen(new MainMenuScreen(game)));
        }
    }

    @Override
    public void show() {
        // do later
    }

    @Override
    public void render(float delta) {
        // check connection status before anything
        if (client == null || !client.isConnected() || client.clientId < 0) {
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            return;
        }


        // send input
        InputUpdate input = new InputUpdate();
        input.clientId = client.clientId;
        input.dt = delta;
        input.seq = (int)(System.nanoTime() & 0x7FFFFFFF); // sequence number based on time
        input.moveX = (Gdx.input.isKeyPressed(Input.Keys.D) ? 1 : 0) - (Gdx.input.isKeyPressed(Input.Keys.A) ? 1 : 0);
        input.moveY = (Gdx.input.isKeyPressed(Input.Keys.W) ? 1 : 0) - (Gdx.input.isKeyPressed(Input.Keys.S) ? 1 : 0);
        input.castSpell = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        input.dash = Gdx.input.isKeyPressed(Input.Keys.SPACE);

        // TODO: aiming direction towards mouse

        try {
            client.send(input);
        } catch (Exception e) {
            e.printStackTrace();
        }

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
        if (server != null) {
            server.stop();
        }
    }
}
