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

    private String displayName = ""; // if empty then server uses Player1, Player2, etc.

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

        // root layout
        Table root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        // top bar at top right with display name
        final TextButton nameButton = new TextButton(getDisplayName(), skin);
        nameButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                final Dialog dialog = new Dialog("Set Display Name", skin);
                final TextField nameField = new TextField(displayName, skin);

                // build dialog table
                Table content = dialog.getContentTable();
                content.pad(10f);
                content.add(new Label("Display Name:", skin)).left().row();
                content.add(nameField).width(200f).padTop(5f).row();

                // buttons
                TextButton okButton = new TextButton("OK", skin);
                TextButton cancelButton = new TextButton("Cancel", skin);

                // add buttons to dialog button table
                Table buttonTable = dialog.getButtonTable();
                buttonTable.pad(10f);
                buttonTable.add(okButton).padRight(10f);
                buttonTable.add(cancelButton);

                // ok button listener
                okButton.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        displayName = nameField.getText().trim();
                        nameButton.setText(getDisplayName());
                        dialog.hide();
                    }
                });
                // cancel button listener
                cancelButton.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        dialog.hide();
                    }
                });

                dialog.show(stage);
                stage.setKeyboardFocus(nameField);
            }
        });

        // top bar table
        Table topBar = new Table();
        topBar.setFillParent(true);
        topBar.top().right();
        topBar.pad(10f);
        topBar.add(nameButton);
        stage.addActor(topBar);

        // title
        Label titleLabel = new Label("Wizard Arena", skin, "title");
        titleLabel.setColor(Color.WHITE);

        // buttons
        TextButton hostButton = new TextButton("Host", skin);
        TextButton joinButton = new TextButton("Join", skin);
        TextButton exitButton = new TextButton("Exit", skin);

        // host button listener
        hostButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String playerName = (displayName == null) ? "" : displayName.trim();
                ListenServer server = new ListenServer();
                Client client = new Client();
                try {
                    Gdx.app.log("Button", "clicked");
                    server.start();
                    int serverPort = server.getPort();
                    String hostIP = server.getHostIP();
                    client.connect("127.0.0.1", serverPort, playerName);
                    game.setScreen(new LobbyScreen(game, server, client, playerName, true));
                    dispose();
                } catch (Exception e) {
                    e.printStackTrace();
                    server.stop();
                }
            }
        });

        // join button listener
        joinButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {

               // dialog prompt for IP address
               final Dialog dialog = new Dialog("Join Game", skin);
               final TextField ipField = new TextField("", skin);
               final Label errorLabel = new Label("", skin);

               ipField.setMessageText("e.g. 192.168.0.15:25565");

               // build dialog table
               Table content = dialog.getContentTable();
               content.pad(10f);
               content.add(new Label("Enter host address (IP:port):", skin)).left().row();
               content.add(ipField).width(200f).padTop(5f).row();
               content.add(errorLabel).left().padTop(5f).row();

               // buttons
               TextButton connectButton = new TextButton("Connect", skin);
               TextButton cancelButton = new TextButton("Cancel", skin);

               // add buttons to dialog button table
               Table buttonTable = dialog.getButtonTable();
               buttonTable.pad(10f);
               buttonTable.add(connectButton).padRight(10f);
               buttonTable.add(cancelButton);

               // connect button listener
               connectButton.addListener(new ClickListener() {
                   @Override
                   public void clicked(InputEvent event, float x, float y) {
                       String ip = ipField.getText().trim();
                       if (ip.isEmpty()) {
                           ip = "127.0.0.1"; // default to localhost
                       }

                       String host = ip;
                       int port = NetConfig.TCP_PORT;
                       int colonIndex = ip.lastIndexOf(':');
                       if (colonIndex != -1) {
                           host = ip.substring(0, colonIndex).trim();
                           String portStr = ip.substring(colonIndex + 1).trim();
                           try {
                               port = Integer.parseInt(portStr);
                           } catch (NumberFormatException e) {
                               errorLabel.setText("Invalid port number.");
                               return;
                           }
                       }

                       if (host.isEmpty()) {
                           errorLabel.setText("IP address cannot be empty.");
                           return;
                       }

                       String playerName = (displayName == null) ? "" : displayName.trim();
                       Client client = new Client();
                       try {
                           Gdx.app.log("Button", "clicked");
                           Gdx.app.log("Join", "Connecting to " + host + ":" + port);
                           client.connect(host, port, playerName);
                           game.setScreen(new LobbyScreen(game, null, client, playerName, false));
                           dispose();
                       } catch (Exception e) {
                           e.printStackTrace();
                           Gdx.app.log("Error", "Failed to connect to server at " + ip);
                           errorLabel.setText("Connection failed. Please check the IP and try again.");
                       }
                   }
               });
               // cancel button listener
               cancelButton.addListener(new ClickListener() {
                   @Override
                   public void clicked(InputEvent event, float x, float y) {
                       dialog.hide();
                   }
               });
               dialog.show(stage);
               stage.setKeyboardFocus(ipField);
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

    // helpers ------------------------------------------------------------

    private String getDisplayName() {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "Set Name";
        }
        return displayName;
    }

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

        // Create a TextFieldStyle for ip input
        TextField.TextFieldStyle tfStyle = new TextField.TextFieldStyle();
        tfStyle.font = font;
        tfStyle.fontColor = Color.WHITE;
        tfStyle.cursor = new TextureRegionDrawable(new TextureRegion(white));
        tfStyle.selection = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.3f, 0.3f, 0.5f, 1f));
        tfStyle.background = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.1f, 0.1f, 0.1f, 1f));
        skin.add("default", tfStyle);

        // window style
        Window.WindowStyle windowStyle = new Window.WindowStyle();
        windowStyle.titleFont = font;
        windowStyle.titleFontColor = Color.WHITE;
        windowStyle.background = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.05f, 0.05f, 0.05f, 1f));
        skin.add("default", windowStyle);

        // default background
        skin.add("default-bg", bg);

        return skin;
    }
}
