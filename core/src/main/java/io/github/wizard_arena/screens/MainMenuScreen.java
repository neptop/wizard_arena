package io.github.wizard_arena.screens;

import io.github.wizard_arena.net.NetConfig;
import io.github.wizard_arena.net.Client;
import io.github.wizard_arena.net.ListenServer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.wizard_arena.wizardGame;

public class MainMenuScreen extends ScreenAdapter {
    private final wizardGame game;
    private Stage stage;
    private ScreenViewport viewport;
    private Skin skin;
    private BitmapFont font;
    private Texture whiteTex;

    public MainMenuScreen(wizardGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        viewport = new ScreenViewport(new OrthographicCamera());
        stage = new Stage(viewport);
        font = new BitmapFont();
        whiteTex = makeWhiteTexture();
        skin = buildSkin(font, whiteTex);

        // layout
        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        // title
        Label titleLabel = new Label("Wizard Arena", skin, "title");
        titleLabel.setColor(Color.WHITE);

        // buttons
        TextButton hostButton = new TextButton("Host", skin);
        TextButton joinButton = new TextButton("Join", skin);
        TextButton exitButton = new TextButton("Exit", skin);

        // add listeners
        hostButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String playerName = System.getProperty("user.name", "Host");
                ListenServer server = new ListenServer(NetConfig.TCP_PORT);
                Client client = new Client();
                try {
                    Gdx.app.log("Button", "clicked");
                    server.start();
                    client.connect("127.0.0.1", NetConfig.TCP_PORT, playerName);
                    game.setScreen(new LobbyScreen(game, server, client, playerName, true));
                    dispose();
                } catch (Exception e) {
                    e.printStackTrace();
                    server.stop();
                }
            }
        });

        joinButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                //TODO: prompt for IP
                String ip = "127.0.0.1"; // placeholder IP for now
                String playerName = System.getProperty("user.name", "Player");
                Client client = new Client();
                try {
                    Gdx.app.log("Button", "clicked");
                    client.connect(ip, NetConfig.TCP_PORT, playerName);
                    game.setScreen(new LobbyScreen(game, null, client, playerName, false));
                    dispose();
                } catch (Exception e) {
                    e.printStackTrace();
                    Gdx.app.log("Error", "Failed to connect to server at " + ip);
                }

            }
        });

        exitButton.addListener(e -> {
            if (!exitButton.isPressed()) return false;
            Gdx.app.exit();
            return true;
        });

        // assemble layout
        root.defaults().pad(10f);
        root.add(titleLabel).padTop(40f).row();
        root.add(hostButton).width(240f).height(56f).row();
        root.add(joinButton).width(240f).height(56f).row();
        root.add(exitButton).width(240f).height(56f).row();

        // input
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        // clear to black
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (font != null) font.dispose();
        if (whiteTex != null) whiteTex.dispose();
        if (skin != null) skin.dispose();
    }

    // helpers
    private Texture makeWhiteTexture() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private Skin buildSkin(BitmapFont font, Texture white) {
        Skin skin = new Skin();

        skin.add ("font", font, BitmapFont.class);
        skin.add("white", white, Texture.class);

        // base drawables
        Drawable bg = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0f,0f,0f,0f));
        Drawable btnUp = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.15f,0.15f,0.15f,1f));
        Drawable btnDown = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.25f,0.25f,0.25f,1f));
        Drawable btnOver = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.2f,0.2f,0.2f,1f));

        // Create a LabelStyle
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = font;
        labelStyle.fontColor = Color.WHITE;
        skin.add("default", labelStyle);

        // Create a title LabelStyle
        Label.LabelStyle titleStyle = new Label.LabelStyle();
        BitmapFont titleFont = new BitmapFont();
        titleFont.getData().setScale(2.2f);
        titleStyle.font = titleFont;
        titleStyle.fontColor = Color.WHITE;
        skin.add("title", titleStyle);

        // Create a TextButtonStyle
        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.up = btnUp;
        textButtonStyle.down = btnDown;
        textButtonStyle.over = btnOver;
        textButtonStyle.font = font;
        textButtonStyle.fontColor = Color.WHITE;
        skin.add("default", textButtonStyle);

        // default background
        skin.add("default-bg", bg);

        return skin;
    }
}
