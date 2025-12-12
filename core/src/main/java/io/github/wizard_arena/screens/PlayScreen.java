package io.github.wizard_arena.screens;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.wizard_arena.Player;
import io.github.wizard_arena.Tile;
import io.github.wizard_arena.net.*;
import io.github.wizard_arena.wizardGame;
import io.github.wizard_arena.debug.CheatConsole;
import org.w3c.dom.Text;

import java.util.*;

import static io.github.wizard_arena.net.Messages.*;

public class PlayScreen extends ScreenAdapter {
    private final wizardGame game;
    private final boolean isHost;
    private ListenServer server;
    private Client client;
    private Player player;
    private boolean hudOpen;
    private Map<Integer, Player> playerSprites = new HashMap<>(); // clientId to Player sprite
    private String mapFile = "dungeon.txt"; // default map file

    // player wizard sprite textures
    private Texture redWizardTexture;
    private Texture blueWizardTexture;
    private Texture greenWizardTexture;
    private Texture yellowWizardTexture;

    // map stuff
    private Tile[][] tiles;
    private int map_height;
    private int map_width;
    Texture floor;
    Texture wall;
    Texture grass;
    Texture mud;
    Texture ice;
    Texture snow;
    final private int spriteSize = 64;  //all sprites and tile sizes are 64x64

    //  camera setup
    private OrthographicCamera camera;
    private OrthographicCamera hudCamera;

    // cheat console
    private CheatConsole console;

    // round state
    private int roundNumber = 0;
    private int roundState = 0; // 0 = waiting, 1 = in progress, 2 = post round, 3 = match over
    private float roundTimeRemaining = 0f;
    private float postRoundTimeRemaining = 0f;
    private int roundWinnerId = -1;
    private int matchWinnerId = -1;
    private String roundWinnerName = null;
    private String matchWinnerName = null;
    private Map<Integer, String> playerNames = new HashMap<>();

    // ui stuff
    private BitmapFont font;
    private ShapeRenderer shapeRenderer;
    private Viewport hudViewport;
    private SpriteBatch hudBatch;
    Texture roundWinEmpty;
    Texture roundWinFull;
    Texture hotbarTexture;

    // spell projectiles
    private Map<Integer, ProjectileRender> projectileSprites = new HashMap<>();
    private Texture starterBallTexture;
    private Texture poisonBallTexture;
    private Texture fireBallTexture;
    private Texture lightningBallTexture;
    private Texture airBallTexture;

    private static class ProjectileRender {
        public float x;
        public float y;
        public String spellType;
    }

    // spell pickups
    private Map<Integer, SpellPickupRender> spellPickupSprites = new HashMap<>();
    private Texture spellScrollTexture;
    private static class SpellPickupRender {
        public float x;
        public float y;
    }

    // valid cheat spell names
    private static final Set<String> validSpellNames = new HashSet<>(Arrays.asList(
        "starter", "poison", "fire", "lightning", "air"
    ));

    // powerup pickups
    private Map<Integer, PowerupPickupRender> powerupPickupSprites = new HashMap<>();
    private Texture speedPowerupTexture;
    private Texture healPotionTexture;
    private Texture overdriveTexture;
    private static class PowerupPickupRender {
        public float x;
        public float y;
        public String type;
    }

    // valid cheat powerup names
    private static final Set<String> validPowerupNames = new HashSet<>(Arrays.asList(
        "speed", "health", "overdrive"
    ));

    // lava
    private boolean lavaActive = false;
    private float lavaCenterX = 0f;
    private float lavaCenterY = 0f;
    private float lavaRadius = 0f;
    private float lavaWarningTimer = 0f;
    private static final float LAVA_WARNING_DURATION = 3f; // seconds

    // host constructor
    public PlayScreen(wizardGame game, ListenServer server, Client client, String mapFile) {
        this.game = game;
        this.isHost = true;
        this.server = server;
        this.client = client;
        this.mapFile = mapFile;

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
    public PlayScreen(wizardGame game, Client client, String mapFile) {
        this.game = game;
        this.isHost = false;
        this.client = client;
        this.server = null;
        this.mapFile = mapFile;

        initialize();
    }

    public void initialize() {


        camera = new OrthographicCamera();
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // setup a separate camera for hud elements
        hudCamera = new OrthographicCamera();
        hudCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        hudViewport = new ScreenViewport(hudCamera);
        hudBatch = new SpriteBatch();

        wall = new Texture(Gdx.files.internal("wall_tile.png"));
        floor = new Texture(Gdx.files.internal("floor_tile.png"));
        grass = new Texture(Gdx.files.internal("grass_tile.png"));
        mud = new Texture(Gdx.files.internal("mud_tile.png"));
        ice = new Texture(Gdx.files.internal("ice_tile.png"));
        snow = new Texture(Gdx.files.internal("snow_tile.png"));

        // spell projectile textures
        starterBallTexture = new Texture(Gdx.files.internal("star_spell_icon.png"));
        poisonBallTexture = new Texture(Gdx.files.internal("poison_ball_projectile.png"));
        fireBallTexture = new Texture(Gdx.files.internal("fireball_projectile.png"));
        lightningBallTexture = new Texture(Gdx.files.internal("lightning_ball_projectile.png"));
        airBallTexture = new Texture(Gdx.files.internal("ball.png"));

        spellScrollTexture = new Texture(Gdx.files.internal("scroll.png"));

        // powerup textures
        speedPowerupTexture = new Texture(Gdx.files.internal("speed_powerup.png"));
        healPotionTexture = new Texture(Gdx.files.internal("health_potion.png"));
        overdriveTexture = new Texture(Gdx.files.internal("overdrive.png"));

        // player wizard textures
        redWizardTexture = new Texture(Gdx.files.internal("red_wizard.png"));
        blueWizardTexture = new Texture(Gdx.files.internal("blue_wizard.png"));
        greenWizardTexture = new Texture(Gdx.files.internal("green_wizard.png"));
        yellowWizardTexture = new Texture(Gdx.files.internal("yellow_wizard.png"));

        FileHandle file = Gdx.files.internal(mapFile);
        String text = file.readString();

        String[] lines = text.split("\\r?\\n");

        List<String> list = Arrays.asList(lines);
        Collections.reverse(list);

        map_height = lines.length;
        map_width = lines[0].length();

        tiles = new Tile[map_height][map_width];


        System.out.println("now playing" + mapFile);
        for(int i = 0; i < map_height  ; i++) {
            String line = lines[i];
            for(int j = 0 ; j < map_width ; j++) {
                char c = line.charAt(j);
                Texture curr_tile;
                if(c == 'P'|| c == 'U' || c == 'S') {
                    if(Objects.equals(mapFile, "dungeon.txt"))
                        curr_tile = floor;
                    else if(Objects.equals(mapFile, "grass.txt"))
                        curr_tile = grass;
                    else
                        curr_tile = snow;
                } else if (c == 'X' ) {
                    if(Objects.equals(mapFile, "dungeon.txt"))
                        curr_tile = wall;
                    else if(Objects.equals(mapFile, "grass.txt"))
                        curr_tile = mud;
                    else
                        curr_tile = ice;
                }
                else {
                    if(Objects.equals(mapFile, "dungeon.txt"))
                        curr_tile = floor;
                    else if(Objects.equals(mapFile, "grass.txt"))
                        curr_tile = grass;
                    else
                        curr_tile = snow;
                }
                tiles[i][j] = new Tile(game, i, j, curr_tile, (c == 'P' || c == 'O' || c == 'S' || c == 'U'));
            }
        }

        // spawn point gets updated when snapshot received
        player = new Player(game, 300f, client, 0, 0, tiles);
        if (client != null) {
            applyPlayerTex(client.clientId, player);
            playerSprites.put(client.clientId, player);
        }

        // setup cheat console
        console = new CheatConsole();
        console.setExecutor(this::executeCheat);

        // setup ui
        font = new BitmapFont();
        shapeRenderer = new ShapeRenderer();
        roundWinEmpty = new Texture(Gdx.files.internal("round_win_empty.png"));
        roundWinFull = new Texture(Gdx.files.internal("round_win_full.png"));

        // setup custom cursor / crosshair
        Pixmap pixmap = new Pixmap(Gdx.files.internal("crosshair.png"));
        int cursorX = pixmap.getWidth() / 2;
        int cursorY = pixmap.getHeight() / 2;
        Cursor cursor = Gdx.graphics.newCursor(pixmap, cursorX, cursorY);
        Gdx.graphics.setCursor(cursor);
        pixmap.dispose();
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

        // block input if console is open or round not in progress
        boolean blockInput = (console != null && console.isVisible()) || (roundState != 1);
        player.update(blockInput, camera);

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
                            p = new Player(game, 300f, null, 0, 0, tiles); // spawn will be updated at next snapshot
                            applyPlayerTex(ps.id, p);
                            playerSprites.put(ps.id, p);
                        }
                    }

                    if (ps.id == client.clientId) {
                        // this is the local player, so apply server reconciliation
                        p.applyServerState(ps.x, ps.y, ps.lastProcessedInput);
                        p.setHealth(ps.hp); // update health
                        p.hudHealthBar.setHealthRatio(ps.hp, p.maxHealth); // update health bar
                        p.playerHealthBar.setHealthRatio(ps.hp, p.maxHealth);
                        p.playerBlinkBar.setBlinkRatio(p.blinkTimer * 70, p.maxBlinkBar);
                        p.setAlive(ps.alive);
                        p.setRoundsWon(ps.roundsWon);
                        p.setSpellSlots(ps.spellSlots);
                        p.setSpellCooldowns(ps.spellCooldownRemaining, ps.spellCooldownMax);
                    } else {
                        // remote player, directly set position
                        p.startInter(ps.x, ps.y);
                        p.setHealth(ps.hp); // update health
                        p.hudHealthBar.setHealthRatio(ps.hp, p.maxHealth); // update health bar
                        p.playerHealthBar.setHealthRatio(ps.hp, p.maxHealth);
                        p.lookAngle = ps.lookAngle;
                        p.setRotation(ps.lookAngle);



                        p.currentAnimationID = ps.currentAnimationID;


                        p.angle = ps.lookAngle;
                        p.setAlive(ps.alive);
                        p.setRoundsWon(ps.roundsWon);
                    }

                    // debug: check what client thinks is applying the snapshot
                    //System.out.println("applied snapshot: id=" + ps.id + " pos=(" + ps.x + ", " + ps.y + ") hp=" + ps.hp);

                }
                // remove players that aren't in the snapshot i.e. disconnected
                playerSprites.entrySet().removeIf(e -> !updatedPlayerIds.contains(e.getKey()));

                // update round state
                roundNumber = s.roundNumber;
                roundTimeRemaining = s.roundTimeRemaining;
                roundState = s.roundState;
                roundWinnerId = s.roundWinnerId;
                matchWinnerId = s.matchWinnerId;
                postRoundTimeRemaining = s.postRoundTimeRemaining;

                // update player names
                if (s.playerNames != null) {
                    playerNames.clear();
                    playerNames.putAll(s.playerNames);
                }
                roundWinnerName = s.roundWinnerName;
                matchWinnerName = s.matchWinnerName;

                // update lava state
                lavaActive = s.lavaActive;
                lavaCenterX = s.lavaCenterX;
                lavaCenterY = s.lavaCenterY;
                lavaRadius = s.lavaRadius;
                if (s.lavaJustStarted) {
                    lavaWarningTimer = LAVA_WARNING_DURATION;
                }

                // process projectiles
                Set<Integer> updatedProjectileIds = new HashSet<>();
                if (s.projectiles != null){
                    for (ProjectileState ps : s.projectiles) {

                        updatedProjectileIds.add(ps.id);
                        ProjectileRender pr = projectileSprites.get(ps.id);
                        if (pr == null) {
                            pr = new ProjectileRender();
                            projectileSprites.put(ps.id, pr);
                        }
                        pr.x = ps.x;
                        pr.y = ps.y;
                        pr.spellType = ps.spellType;
                    }
                }
                // remove projectiles that aren't in the snapshot anymore
                projectileSprites.entrySet().removeIf(e -> !updatedProjectileIds.contains(e.getKey()));

                // process spell pickups
                Set<Integer> updatedPickupIds = new HashSet<>();
                if (s.spellPickups != null) {
                    for (SpellPickupState sp : s.spellPickups) {
                        updatedPickupIds.add(sp.id);
                        SpellPickupRender spr = spellPickupSprites.get(sp.id);
                        if (spr == null) {
                            spr = new SpellPickupRender();
                            spellPickupSprites.put(sp.id, spr);
                        }
                        spr.x = sp.x;
                        spr.y = sp.y;
                    }
                }
                // remove pickups that aren't in the snapshot anymore
                spellPickupSprites.entrySet().removeIf(e -> !updatedPickupIds.contains(e.getKey()));

                // process powerup pickups
                Set<Integer> updatedPowerupIds = new HashSet<>();
                if (s.powerups != null) {
                    for (PowerupState pp : s.powerups) {
                        updatedPowerupIds.add(pp.id);
                        PowerupPickupRender ppr = powerupPickupSprites.get(pp.id);
                        if (ppr == null) {
                            ppr = new PowerupPickupRender();
                            powerupPickupSprites.put(pp.id, ppr);
                        }
                        ppr.x = pp.x;
                        ppr.y = pp.y;
                        ppr.type = pp.powerupType;
                    }
                }
                powerupPickupSprites.entrySet().removeIf(e -> !updatedPowerupIds.contains(e.getKey()));
            }
        }

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

        game.batch.begin();

        // draw map tiles
        for(int i = 0 ; i < map_height ; i++) {
            for(int j = 0; j < map_width; j++) {
                tiles[i][j].render(game.batch);
            }
        }

        // draw players
        for (Player p : playerSprites.values()) {
            if(!p.isAlive()) continue;

            switch (p.currentAnimationID) {
                case 0:
                    if(p.currentAnimation != p.idle) {
                        p.currentAnimation = p.idle;
                        p.stateTime = 0f;
                    }
                    break;
                case 1:
                    if(p.currentAnimation != p.attackAnimation) {
                        p.currentAnimation = p.attackAnimation;
                        p.stateTime = 0f;
                    }
                    break;
                case 2:
                    if(p.currentAnimation != p.blinkAnimation) {
                        p.currentAnimation = p.blinkAnimation;
                        p.stateTime = 0f;
                    }
                    break;
                default:
                    p.currentAnimation = p.idle;
                    break;
            }
            p.stateTime += delta;
            p.render(game.batch);

            // draw each players' health bar under them
            p.playerHealthBar.draw(game.batch, 1);

        }

        // draw projectiles
        for (ProjectileRender pr : projectileSprites.values()) {
            Texture projectileTexture = null;
            if (pr == null) {
                projectileTexture = starterBallTexture; // default
            } else {
                switch (pr.spellType) {
                    case "starter":
                        projectileTexture = starterBallTexture;
                        break;
                    case "poison":
                        projectileTexture = poisonBallTexture;
                        break;
                    case "fire":
                        projectileTexture = fireBallTexture;
                        break;
                    case "lightning":
                        projectileTexture = lightningBallTexture;
                        break;
                    case "air":
                        projectileTexture = airBallTexture;
                        break;
                    default:
                        projectileTexture = starterBallTexture; // default
                        break;
                }
            }
            game.batch.draw(projectileTexture, pr.x, pr.y);
        }

        // draw spell pickups
        for (SpellPickupRender spr : spellPickupSprites.values()) {
            game.batch.draw(spellScrollTexture, spr.x, spr.y);
//            game.batch.draw(fireBallTexture, spr.x, spr.y); // TODO: change this to show actual sprite
        }

        // draw powerup pickups
        for (PowerupPickupRender ppr : powerupPickupSprites.values()) {
            Texture tex = null;
            if (ppr.type == null) {
                tex = speedPowerupTexture; // default
            } else {
                switch (ppr.type) {
                    case "speed":
                        tex = speedPowerupTexture;
                        break;
                    case "health":
                        tex = healPotionTexture;
                        break;
                    case "overdrive":
                        tex = overdriveTexture;
                        break;
                    default:
                        tex = speedPowerupTexture; // default
                        break;
                }
            }
            game.batch.draw(tex, ppr.x, ppr.y);
        }

        game.batch.end();

        // draw lava safe circle and lava field
        if (lavaActive && lavaRadius > 0f){
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapeRenderer.setProjectionMatrix(camera.combined);

            float innerRadius = lavaRadius;
            float worldWidth = map_width * spriteSize;
            float worldHeight = map_height * spriteSize;
            float outerRadius = Math.max(worldWidth, worldHeight) * 1.2f;
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(1f, 0.4f, 0f, 0.25f); // orange translucent

            int numSegments = 80; // number of segments to approximate the circle
            for (int i = 0; i < numSegments; i++) {
                float angle1 = (float) (i * 2 * Math.PI / numSegments);
                float angle2 = (float) ((i + 1) * 2 * Math.PI / numSegments);
                float cos1 = (float) Math.cos(angle1);
                float cos2 = (float) Math.cos(angle2);
                float sin1 = (float) Math.sin(angle1);
                float sin2 = (float) Math.sin(angle2);
                float x1Inner = lavaCenterX + innerRadius * cos1;
                float y1Inner = lavaCenterY + innerRadius * sin1;
                float x2Inner = lavaCenterX + innerRadius * cos2;
                float y2Inner = lavaCenterY + innerRadius * sin2;
                float x1Outer = lavaCenterX + outerRadius * cos1;
                float y1Outer = lavaCenterY + outerRadius * sin1;
                float x2Outer = lavaCenterX + outerRadius * cos2;
                float y2Outer = lavaCenterY + outerRadius * sin2;

                shapeRenderer.triangle(x1Inner, y1Inner, x1Outer, y1Outer, x2Outer, y2Outer);
                shapeRenderer.triangle(x1Inner, y1Inner, x2Outer, y2Outer, x2Inner, y2Inner);
            }
            shapeRenderer.end();

            // draw lava safe circle outline
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(1f, 0.3f, 0f, 1f); // orange
            shapeRenderer.circle(lavaCenterX, lavaCenterY, innerRadius);
            shapeRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        // draw ui stuff over tiles + players
        hudBatch.setProjectionMatrix(hudCamera.combined);
        hudBatch.begin();

        player.hudHealthBar.draw(hudBatch, 1);
        player.playerBlinkBar.draw(hudBatch, 1);
        player.hotbar.draw(hudBatch, 1);
        player.hotbar.hotbarSelectedSprite.draw(hudBatch);

        // draw the rounds won indicators
        int numRounds = player.getRoundsWon();
        for (int i = 0; i < 5; i++){
            if (numRounds > i) {
                hudBatch.draw(roundWinFull, i * 72, 32);
            } else {
                hudBatch.draw(roundWinEmpty, i * 72, 32);
            }
        }

        hudBatch.end();

        // draw lava warning
        if (lavaWarningTimer > 0f) {
            lavaWarningTimer -= delta;
            hudBatch.setProjectionMatrix(hudCamera.combined);
            hudBatch.begin();
            String msg = "Lava is rising!";
            font.setColor(Color.ORANGE);

            // center the text at the top of the screen
            float screenWidth = hudCamera.viewportWidth;
            float y = hudCamera.viewportHeight - 40f;
            font.draw(hudBatch, msg, screenWidth / 2f- 80f, y);
            hudBatch.end();
        }


        // render the between round overlay
        if (roundState == 2 || roundState == 3) {
            drawRoundOverlay();
        }

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

    private void drawRoundOverlay() {
        Gdx.gl.glEnable(GL20.GL_BLEND); // enable blending for transparency
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(new Color(0, 0, 0, 0.6f));
        shapeRenderer.rect(camera.position.x - camera.viewportWidth / 2f,
                camera.position.y - camera.viewportHeight / 2f,
                camera.viewportWidth,
                camera.viewportHeight
        );
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // draw text
        // round winner + leaderboard + countdown to next round
        game.batch.begin();
        font.setColor(Color.WHITE);

        float centerX = camera.position.x;
        float centerY = camera.position.y;

        // title
        String title = "Round " + roundNumber + " Over";
        if(roundState == 3) {
            title = "Match Over";
        }
        font.draw(game.batch, title, centerX - 150, centerY + 100);

        // winner text
        String winnerText;
        if (roundState == 3 && matchWinnerId != -1){
            String name = (matchWinnerName != null && !matchWinnerName.trim().isEmpty()) ? matchWinnerName : getPlayerNameById(matchWinnerId);
            winnerText = "Player " + matchWinnerId + " (" + name + ") wins the match!";
        } else if (roundWinnerId != -1) {
            String name = (roundWinnerName != null && !roundWinnerName.trim().isEmpty()) ? roundWinnerName : getPlayerNameById(roundWinnerId);
            winnerText = "Player " + roundWinnerId + " (" + name + ") wins the round!";
        } else {
            winnerText = "Round ended in a draw!";
        }
        font.draw(game.batch, winnerText, centerX - 150, centerY + 70);

        // leaderboard
        List<Map.Entry<Integer, Player>> entries = new ArrayList<>(playerSprites.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue().getRoundsWon(), a.getValue().getRoundsWon()));
        float leaderboardY = centerY + 40;
        int rank = 1;
        for (Map.Entry<Integer, Player> entry : entries) {
            Player player = entry.getValue();
            String lbLine = rank + ". Player " + getPlayerNameById(entry.getKey()) + " - Rounds Won: " + player.getRoundsWon();
            font.draw(game.batch, lbLine, centerX - 150, leaderboardY);
            leaderboardY -= 20;
            rank++;
        }

        // countdown to next round
        if (roundState == 2){
            int secs = (int) Math.ceil(postRoundTimeRemaining);
            String countdownText = "Next round in: " + secs + " seconds";
            font.draw(game.batch, countdownText, centerX - 150, centerY - 20);
        }
        game.batch.end();
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
            console.print("die - instantly die");
            console.print("killall - instantly kill all other players (should end round as a draw)");
            console.print("spawnspell <name> - spawn a spell of the given name at the player's position");
            console.print("spawnpowerup <name> - spawn a powerup of the given name at the player's position");
            console.print("startlava - start the lava hazard");
            return;
        }

        // instant die
        if(lower.equals("die")) {
            if (client != null && client.clientId > 0){
                try {
                    client.send(new DamageRequest(client.clientId, 9999f));
                } catch (Exception e) {
                    console.print("failed to send damage request: " + e.getMessage());
                }
            } else {
                console.print("cmd failed");
                return;
            }
            console.print("player died");
            return;
        }

        // kill all other players (should result in a round draw)
        if(lower.equals("killall")) {
            if(client != null && client.clientId > 0){
                try {
                    for (Integer id : playerSprites.keySet()) {
                        client.send(new DamageRequest(id, 9999f));
                    }
                    console.print("all players killed");
                } catch (Exception e) {
                    console.print("failed to send damage requests: " + e.getMessage());
                }
            } else {
                console.print("cmd failed");
            }
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
            console.print("god mode toggled");
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

        // spawn spell
        if (lower.startsWith("spawnspell ")) {
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                console.print("usage: spawnspell <name>");
                console.print("valid spells: " + validSpellNames);
                return;
            }
            String spellName = parts[1].toLowerCase();
            if (!validSpellNames.contains(spellName)) {
                console.print("invalid spell name: " + spellName);
                console.print("valid spells: " + validSpellNames);
                return;
            }

            if (client != null && client.clientId > 0) {
                try {
                    client.send(new SpawnSpellPickupRequest(client.clientId, spellName));
                    console.print("spawned spell: " + spellName);
                } catch (Exception e) {
                    console.print("failed to send spawn spell request: " + e.getMessage());
                }
            } else {
                console.print("cmd failed");
            }
            return;
        }

        // spawn powerup
        if (lower.startsWith("spawnpowerup ")) {
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                console.print("usage: spawnpowerup <name>");
                console.print("valid powerups: " + validPowerupNames);
                return;
            }
            String powerupName = parts[1].toLowerCase();
            if (!validPowerupNames.contains(powerupName)) {
                console.print("invalid powerup name: " + powerupName);
                console.print("valid powerups: " + validPowerupNames);
                return;
            }

            if (client != null && client.clientId > 0) {
                try {
                    client.send(new SpawnPowerupRequest(client.clientId, powerupName));
                    console.print("spawned powerup: " + powerupName);
                } catch (Exception e) {
                    console.print("failed to send spawn powerup request: " + e.getMessage());
                }
            } else {
                console.print("cmd failed");
            }
            return;
        }

        if (lower.equals("startlava")) {
            if (client != null && client.clientId > 0) {
                try {
                    client.send(new LavaStartRequest(client.clientId));
                    console.print("sent start lava request");
                } catch (Exception e) {
                    console.print("failed to send start lava request: " + e.getMessage());
                }
            } else {
                console.print("cmd failed");
            }
            return;
        }

        console.print("unknown command: " + line);
    }

    private String getPlayerNameById(int id) {
        String name = playerNames.get(id);
        if (name == null || name.trim().isEmpty()) {
            return "Player " + id;
        }
        return name;
    }

    private Texture getWizTextById(int id) {
        switch (id) {
            case 1:
                return redWizardTexture;
            case 2:
                return blueWizardTexture;
            case 3:
                return greenWizardTexture;
            case 4:
                return yellowWizardTexture;
            default:
                return redWizardTexture;
        }
    }

    private void applyPlayerTex(int id, Player p) {
        Texture tex = getWizTextById(id);
        //p.setTexture(tex);
        p.animatePlayer(id);
    }
}
