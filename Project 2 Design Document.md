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

**4\. Visual Entities:<br/>**
Visual entities include all of the connected players (wizards), spells that are cast and moving around the screen, blocks/walls around the arena, and the outside lava circle.

**5\. Interactions:<br/>**
Players will be able to damage each other with various types of spells, including themselves. For example, if a player casts an exploding fireball at a wall they are directly standing on, they will hit themselves, possibly for reduced damage. Spells may also be able to collide mid-air and neutralize each other, or act as shields. We want to have different basic elemental debuffs, with at least fire and ice being low bar goals. They will kind of counteract and interact with each other: if a player hits you with a fire spell, you might start burning and taking additional damage over time. If you get hit with a water spell, either from yourself or someone else, you will negate the burn. And vice versa, if you're hit with an ice spell, you will be slowed down until it either expires or you warm up. Players will also drop their inventory of spells when they die, so other players have the option to take their spells if they wish.

**6\. Why it’s interesting/fun:**  
Wizard Arena aims to have short high-intensity duels with the game being easy to learn and pick up but having enough skill expression to keep returning players engaged. Each spell requires precision and timing, and dodging attacks should feel just as rewarding as landing a hit. The multiplayer chaos ensures that every round plays out differently. The game blends the spell interactions of Magicka and Spellbreak so for example if a player hits you with a fireball, you will have a burning debuff. To counteract this you could shoot yourself with an ice orb or a water spout to douse yourself. The map design borrows from arena shooters such as Halo and Unreal Tournament where stronger spells and power ups will spawn in areas that are easily contested. The short rounds and gameplay loop hopes to create a satisfying and arcade-like experience.

**Development Strategy:**  
The first milestone is to get networking implemented, and have players control basic moving shapes. Every group member will likely be working on networking as we all decide what route we want to take and how to best implement it.
<br/>The second milestone will probably look towards implementing a couple of basic spells with a health/damaging system. We plan to borrow some existing code from the Breakout homework as it’s a good baseline for the physics logic and some of our spells.
<br/>The final milestone will be having the main game loop working, so the battle royale elements with the closing lava circle and declaring a winner after some amount of rounds.
<br/> All throughout the development process, there will be work done for sprites/animations for the spells, players, and arena.

**High Bar:**

**Low Bar Checklist:**
**<br/>1\. Enough different spells to fill an inventory** 
<br/> The player will be able to hold 3-4 spells in their inventory, so there should be at least this many different spells in the game.
**<br/>2\. Smooth player movement**
<br/>Players will be 'sliding' around the arena smoothly, and not get stuck on objects.
**<br/>3\. Player damage/death**
<br/> Players will be able to attack and damage each other, and die until the current round is over.