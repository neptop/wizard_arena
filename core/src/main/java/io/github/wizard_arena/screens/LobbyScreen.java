package io.github.wizard_arena.screens;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import io.github.wizard_arena.net.Messages;
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
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import io.github.wizard_arena.net.NetConfig;
import io.github.wizard_arena.wizardGame;

public class LobbyScreen extends ScreenAdapter {
    private final wizardGame game;
    private final ListenServer server; // should be null for clients
    private final Client client;
    private final String playerName;
    private final boolean isHost;
    private final String hostIP;
    private int hostPort;

    private Stage stage;
    private ScreenViewport viewport;
    private Skin skin;
    private BitmapFont font;
    private Texture whiteTex;

    // list of players in the lobby
    private List <String> playerList;

    private Label ipLabel; // label to show lobby IP
    private String currentLobbyIP; // lobby IP with port
    private String rawHostIP; // raw IP without port

    private SelectBox<String> mapSelect;
    private String selectedMap = "map1.txt";
    private static final String [] AVAILABLE_MAPS = {
        "map1.txt",
        "map2.txt"
    };

    // debug flag
    private CheckBox debugCheckbox;

    public LobbyScreen(wizardGame game, ListenServer server, Client client, String playerName, boolean isHost) {
        this.game = game;
        this.server = server;
        this.client = client;
        this.playerName = playerName;
        this.isHost = isHost;
        this.hostIP = (server != null) ? server.getHostIP() : null;
        this.hostPort = (server != null) ? server.getPort() : NetConfig.TCP_PORT; // fallback to port only
        this.rawHostIP = hostIP;

        if (hostIP != null) {
            this.currentLobbyIP = hostIP + ":" + hostPort;
        } else {
            this.currentLobbyIP = null;
        }
    }

    @Override
    public void show() {
        viewport = new ScreenViewport(new OrthographicCamera());
        stage = new Stage(viewport);
        font = new BitmapFont();
        whiteTex = makeWhiteTexture();
        skin = buildSkin(font, whiteTex);

        Gdx.input.setInputProcessor(stage);

        // layout
        Table root = new Table();
        root.setFillParent(true);
        root.pad(20f);
        stage.addActor(root);

        // player list on left panel
        Table leftPanel = new Table(skin);
        leftPanel.defaults().pad(5f);
        Label playersLabel = new Label("Players", skin);

        // player list
        playerList = new List<>(skin);
        playerList.setItems(playerName + (isHost ? " (Host)" : ""));

        // put player list in scroll pane
        ScrollPane scrollPane = new ScrollPane(playerList, skin);
        leftPanel.add(playersLabel).left().row();
        leftPanel.add(scrollPane).grow().row();

        //show lobby IP / host address
        String initIPText;
        if (currentLobbyIP != null) {
            initIPText = "Host Address: " + currentLobbyIP;
        } else {
            initIPText = "Host Address: N/A";
        }

        ipLabel = new Label(initIPText, skin);
        ipLabel.setColor(Color.LIGHT_GRAY);

        // copy IP button
        TextButton copyIPButton = new TextButton("Copy", skin);
        copyIPButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (currentLobbyIP != null) {
                    Gdx.app.getClipboard().setContents(currentLobbyIP);
                    // feedback to user
                    ipLabel.setText("Host Address: " + currentLobbyIP + " (copied)");
                }
            }
        });

        // assemble IP row
        Table ipTable = new Table(skin);
        ipTable.add(ipLabel).left().padRight(8f);
        ipTable.add(copyIPButton).right();
        leftPanel.add(ipTable).left().padTop(10f).row();

        // header and footer on right panel
        Table rightPanel = new Table(skin);
        rightPanel.defaults().pad(5f);

        // top bar with exit button
        TextButton exitButton = new TextButton(isHost ? "Close Lobby" : "Leave Lobby", skin);
        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (client != null) {
                    client.disconnect();
                }
                if (isHost && server != null) {
                    server.stop();
                }
                dispose();
                game.setScreen(new MainMenuScreen(game)); // return to main menu
            }
        });

        Table topBar = new Table(skin);
        topBar.add().expandX(); // spacer
        topBar.add(exitButton).right();

        // bottom bar
        Table bottomBar = new Table(skin);
        bottomBar.add().expandX(); // spacer

        // start game button (only for host)
        if (isHost) {
            TextButton startButton = new TextButton("Start Game", skin);
            startButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y){
                    if (server != null) {
                        //
                        server.setDebugMode(debugCheckbox != null && debugCheckbox.isChecked());

                        if (mapSelect != null) {
                            selectedMap = (mapSelect.getSelected());
                        }
                        server.setMapFile(selectedMap);
                        server.sendStartGame();
                    }
                }
            });
            bottomBar.add(startButton).right().padTop(10f);
        } else {
            // clients see waiting message
            Label waitLabel = new Label("Waiting for host to start...", skin);
            bottomBar.add(waitLabel).right().padTop(10f);
        }

        // assemble right panel
        rightPanel.top().right();
        rightPanel.add(topBar).growX().top().row();

        if (isHost) {
            Label mapLabel = new Label("Select Map:", skin);
            mapSelect = new SelectBox<>(skin);
            mapSelect.setItems(AVAILABLE_MAPS);
            mapSelect.setSelected(selectedMap);
            mapSelect.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    selectedMap = mapSelect.getSelected();
                }
            });

            Table mapTable = new Table(skin);
            mapTable.add(mapLabel).left().padRight(8f);
            mapTable.add(mapSelect).left().width(200f);
            rightPanel.add(mapTable).left().padTop(10f).row();
        }

        // debug checkbox (only for host)
        if (isHost) {
            debugCheckbox = new CheckBox(" Enable Debug Mode", skin);
            debugCheckbox.left();
            rightPanel.add(debugCheckbox).right().padTop(10f).row();
        }

        rightPanel.add().expand().row(); // spacer
        rightPanel.add(bottomBar).growX().bottom().row();

        // put panels in root table
        root.add(leftPanel).width(300).growY().left();
        root.add(rightPanel).grow();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.08f, 1f); // dark background
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // if connection lost, return to main menu
        if (client == null || !client.isConnected() || client.clientId < 0) {
            Gdx.app.postRunnable(() -> {
                dispose();
                game.setScreen(new MainMenuScreen(game));
            });
            return;
        }

        // process network messages
        for (Messages.Msg msg; (msg = client.pollMessage()) != null; ) {
            if (msg instanceof Messages.LobbyUpdate){
                Messages.LobbyUpdate lobbyUpdate = (Messages.LobbyUpdate) msg;

                String[] names = new String[lobbyUpdate.players.size()];
                for (int i = 0; i < lobbyUpdate.players.size(); i++) {
                    Messages.LobbyPlayerInfo info = lobbyUpdate.players.get(i);
                    names[i] = info.name + (info.isHost ? " (Host)" : "");
                }
                playerList.setItems(names);

                // update ip label for everyone
                if (ipLabel != null && lobbyUpdate.hostIP != null) {
                    rawHostIP = lobbyUpdate.hostIP;
                    hostPort = lobbyUpdate.hostPort;
                    currentLobbyIP = rawHostIP + ":" + hostPort;
                    ipLabel.setText("Host Address: " + currentLobbyIP);
                }

            } else if (msg instanceof Messages.StartGame) {
                // server indicates to start the game
                Messages.StartGame start = (Messages.StartGame) msg;
                final boolean hostFlag = isHost;
                final String mapFile = start.mapFile;
                Gdx.app.postRunnable(() -> {
                    if (hostFlag) {
                        game.setScreen(new PlayScreen(game, server, client, mapFile));
                    } else {
                        game.setScreen(new PlayScreen(game, client, mapFile));
                    }
                });
                return;
            }
        }

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if(stage != null) {
            stage.getViewport().update(width, height, true);
        }
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
        if (font != null) font.dispose();
        if (whiteTex != null) whiteTex.dispose();
    }

    // helpers
    private Texture makeWhiteTexture() {
        Pixmap pixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private Skin buildSkin(BitmapFont font, Texture white) {
        Skin skin = new Skin();

        skin.add("font", font, BitmapFont.class);
        skin.add("white", white, Texture.class);

        // base drawables
        Drawable bg = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0f, 0f, 0f, 0f));
        Drawable btnUp = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.15f, 0.15f, 0.15f, 1f));
        Drawable btnDown = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.25f, 0.25f, 0.25f, 1f));
        Drawable btnOver = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.2f, 0.2f, 0.2f, 1f));

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

        // list style
        List.ListStyle listStyle = new List.ListStyle();
        listStyle.font = font;
        listStyle.fontColorSelected = Color.WHITE;
        listStyle.fontColorUnselected = Color.LIGHT_GRAY;
        listStyle.selection = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.3f, 0.3f, 0.5f, 1f));
        skin.add("default", listStyle);

        // scroll pane style
        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        scrollPaneStyle.background = null;
        scrollPaneStyle.vScroll = null; // no scroll bar for now, may add later
        scrollPaneStyle.vScrollKnob = null;
        skin.add("default", scrollPaneStyle);

        // select box style
        SelectBox.SelectBoxStyle selectBoxStyle = new SelectBox.SelectBoxStyle();
        selectBoxStyle.font = font;
        selectBoxStyle.fontColor = Color.WHITE;
        selectBoxStyle.background = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.15f, 0.15f, 0.15f, 1f));
        selectBoxStyle.scrollStyle = scrollPaneStyle;
        selectBoxStyle.listStyle = listStyle;
        skin.add("default", selectBoxStyle);

        // checkbox style
        CheckBox.CheckBoxStyle checkBoxStyle = new CheckBox.CheckBoxStyle();
        checkBoxStyle.checkboxOff = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.15f, 0.15f, 0.15f, 1f));
        checkBoxStyle.checkboxOn = new TextureRegionDrawable(new TextureRegion(white)).tint(new Color(0.3f, 0.7f, 0.25f, 1f));
        checkBoxStyle.font = font;
        checkBoxStyle.fontColor = Color.WHITE;
        skin.add("default", checkBoxStyle);

        // default background
        skin.add("default-bg", bg);

        return skin;
    }
}
