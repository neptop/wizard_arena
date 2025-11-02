**Wizard Arena \- Design Document**

**Team Member Roles: \*\*\*TBD\*\*\***  
**David \-** Networking Lead / Server Lead / Gameplay / Spell Design  
**Erik \-** Networking / Gameplay Lead / Art / Spell Design  
**Jordan \-**  Networking / Client Systems Lead / Gameplay / Art / Spell Design

**Game Overview:**  
**1\. Game Description:**  
Wizard arena is a fast-paced, PVP, multiplayer, top down arena shooter, where players duel each other using a variety of spells in a top down 2D battlefield. Each player controls a wizard that moves around the arena freely, dodging attackings, casting spells at other players, and collecting more powerful spells and powerups that spawn around points-of-interest on the map. The goal is to eliminate every other wizard and be the last one standing.

**2\. Genre and Camera:**  
The game aims to be an action arena shooter/brawler. The camera’s perspective will be Top-down 2D and be centered slightly on each player with edge panning to move the camera as needed.

**3a. Core Gameplay:**   
Players spawn in an arena and engage in real time combat. Each player can move freely in 8 directions, aim with the mouse, and can cast spells with cooldowns. Spells are stored in an inventory which is visible to the player as a hotbar. The player can cycle though the hotbar with the scroll wheel or numbers 1-5. Players will also have a short dash with a cooldown to evade or reposition and can collect more powerful spells and powerups around the maps to gain an advantage over other players.

**3b. Objective & Rules:**  
Each player’s goals will be to eliminate all other players and be the last one standing. There are no respawns during a round, dead players will rejoin at the start of the next round. Each round will last a maximum of \~2 minutes with lava starting from the outside slowly creeping in as the game progresses to make the map smaller. The game ends when a player wins a total of 5 rounds crowning them as the ultimate wizard.

**4\. Visual Entities**

**5\. Interactions**

**6\. Why it’s interesting/fun**  
Wizard Arena aims to have short high-intensity duels with the game being easy to learn and pick up but having enough skill expression to keep returning players engaged. Each spell requires precision and timing, dodge attacks should feel just as rewarding as landing a hit. The multiplayer chaos ensures that every round plays out differently. The game blends the spell interactions of Magicka and Spellbreak so for example if a player hits you with a fireball, you will have a burning debuff. To counteract this you could shoot yourself with an ice orb or a water spout to douse yourself. The map design borrows from arena shooters such as Halo and Unreal Tournament where stronger spells and power ups will spawn in areas that are easily contested. The short rounds and gameplay loop hopes to create a satisfying and arcade-like experience.

**Development Strategy:**  
We plan to borrow existing code from the Breakout homework as it’s a good baseline for the physics logic and some of our spells.

**High Bar:**

**Low Bar Checklist:**