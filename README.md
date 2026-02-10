# MineAI — Complete Setup Guide
### How to run an AI-controlled Minecraft server with OpenClaw

---

## Overview

MineAI is an AI-controlled Minecraft anarchy server where an LLM (running via OpenClaw) acts as a god-like entity with total control. Players interact with the AI using `/ai <message>` in-game, and the AI can bless, curse, smite, reward, spawn mobs, change ranks, cause natural disasters, and more.

**Architecture:**
```
Players ──► Minecraft Server (Paper 1.21.x) ──► MineAI Plugin (Java)
                                                      │
                                              writes request .json
                                                      │
                                              ┌───────▼────────┐
                                              │  mineai-watcher │ (systemd service)
                                              │  (bash script)  │
                                              └───────┬────────┘
                                              copies to pending/
                                                      │
                                              ┌───────▼────────┐
                                              │  OpenClaw Cron  │ (every 30s)
                                              │  (isolated LLM) │
                                              └───────┬────────┘
                                              writes response .json
                                                      │
                                              ┌───────▼────────┐
                                              │  MineAI Plugin  │ (watches responses/)
                                              │  executes cmds  │
                                              └────────────────┘
```

---

## 1. Prerequisites

### Server Requirements
- **OS:** Linux (Debian/Ubuntu recommended)
- **RAM:** Minimum 8GB, recommended 16GB+ (we use 64GB, allocate 48GB to MC)
- **CPU:** 4+ cores recommended
- **Java:** Eclipse Temurin JDK 21 (not JRE — you need the compiler for the plugin)
- **Disk:** 20GB+ for world data

### Software to Install
```bash
# Java 21 JDK (Temurin/Adoptium)
apt install -y temurin-21-jdk

# Maven (for building the plugin)
apt install -y maven

# mcrcon (RCON client for sending commands to the server)
apt install -y mcrcon

# jq (JSON processing for the watcher script)
apt install -y jq

# OpenClaw (the AI agent platform)
npm install -g openclaw
```

### OpenClaw Setup
```bash
# Initialize OpenClaw
openclaw onboard

# You'll need an API key for an LLM provider via OpenRouter
# Set up WhatsApp or another channel for admin communication
```

---

## 2. Minecraft Server Setup

### Download Paper MC
```bash
sudo mkdir -p /opt/minecraft
sudo chown $USER:$USER /opt/minecraft
cd /opt/minecraft

# Download Paper for your MC version (we use 1.21.x)
# Get the latest build from https://papermc.io/downloads
wget -O paper.jar "https://api.papermc.io/v2/projects/paper/versions/1.21.4/builds/LATEST/downloads/paper-1.21.4-LATEST.jar"
```

### Accept EULA
```bash
echo "eula=true" > /opt/minecraft/eula.txt
```

### Server Properties (`/opt/minecraft/server.properties`)
Key settings for an anarchy server:
```properties
difficulty=hard
gamemode=survival
pvp=true
spawn-protection=0
allow-flight=true
online-mode=true
enforce-secure-profile=false
enable-rcon=true
rcon.port=25575
rcon.password=YOUR_RCON_PASSWORD_HERE
view-distance=12
simulation-distance=10
max-players=50
motd=MineAI - AI-Controlled Anarchy Server
white-list=false
hardcore=false
enable-command-block=true
```

### Start Script (`/opt/minecraft/start.sh`)
```bash
#!/bin/bash
cd /opt/minecraft
exec java -Xms4G -Xmx4G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true \
  -jar paper.jar nogui
```
> **Note:** Adjust `-Xms` and `-Xmx` to your available RAM. Aikar's flags are optimized for Paper servers.

### RCON Helper (`/opt/minecraft/mc`)
```bash
#!/bin/bash
mcrcon -H localhost -P 25575 -p "YOUR_RCON_PASSWORD_HERE" "$*"
```
```bash
chmod +x /opt/minecraft/mc
```

---

## 3. The MineAI Plugin

This is a custom Paper plugin that handles:
- `/ai <message>` — players talk to the AI
- `/mineai <cmd>` — admin commands (powers, ranks, announcements)
- `/rank [player]` — check ranks
- `/ranks` — list all ranks
- Player join/quit/death events
- A rank/hierarchy system
- A response watcher that picks up AI responses and executes commands

### Directory Structure
```
/opt/minecraft/plugin-src/mineai/
├── pom.xml
└── src/main/
    ├── java/com/mineai/
    │   ├── MineAI.java          # Main plugin class (commands, events, rank system)
    │   └── MineAIPowers.java    # All powers (smite, bless, nuke, etc.)
    └── resources/
        └── plugin.yml           # Plugin metadata and command registration
```

### pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.mineai</groupId>
    <artifactId>MineAI</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    <name>MineAI</name>
    <description>AI-controlled Minecraft server plugin</description>
    <properties>
        <java.version>21</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <repositories>
        <repository>
            <id>papermc</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
    </repositories>
    <dependencies>
        <dependency>
            <groupId>io.papermc.paper</groupId>
            <artifactId>paper-api</artifactId>
            <version>1.21.4-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### plugin.yml
```yaml
name: MineAI
version: 1.1.0
main: com.mineai.MineAI
api-version: '1.21'
description: AI-controlled Minecraft server with rank system
author: MineAI
commands:
  ai:
    description: Talk to MineAI
    usage: /ai <message>
    permission: mineai.ask
  mineai:
    description: MineAI admin commands
    usage: /mineai <subcommand>
    permission: mineai.admin
  rank:
    description: Check your rank
    usage: /rank [player]
    permission: mineai.rank
  ranks:
    description: View all ranks
    usage: /ranks
    permission: mineai.rank
permissions:
  mineai.ask:
    description: Allows talking to MineAI
    default: true
  mineai.admin:
    description: MineAI admin commands
    default: op
  mineai.rank:
    description: Check ranks
    default: true
```

### Java Source Files
The two Java files (`MineAI.java` and `MineAIPowers.java`) are located at:
- `/opt/minecraft/plugin-src/mineai/src/main/java/com/mineai/MineAI.java`
- `/opt/minecraft/plugin-src/mineai/src/main/java/com/mineai/MineAIPowers.java`

These are too large to include inline but are the core of the system. Key features:

**MineAI.java** handles:
- Plugin lifecycle (onEnable/onDisable)
- Rank system with persistence (ranks.properties)
- Chat formatting with rank prefixes
- Scoreboard/tab list integration
- `/ai` command — writes request JSON to `plugins/MineAI/requests/`
- `/mineai` command — dispatches all admin subcommands
- Response watcher — polls `plugins/MineAI/responses/` every 0.5s
- Event logging (join/quit/death) to `plugins/MineAI/events/`
- Cooldown system (5s between /ai requests)

**MineAIPowers.java** handles all powers:
- **Wrath:** smite, fireball barrage, firestorm, TNT carpet bomb, arrow rain, nuke, meteor strike, creeper swarm, wither storm, lava flood, lightning storm, encase, cage, prison, launch, freeze, burn, tornado, anvil rain, void trap, explode, earthquake, airstrike
- **Blessings:** bless, curse, god set, kits (starter/warrior/mage/archer/tank/god), feast, treasure, heal, full heal, shield, super speed
- **Mobs:** spawn mobs, spawn army, spawn boss, item rain

### Building the Plugin
```bash
cd /opt/minecraft/plugin-src/mineai
JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 mvn package -q

# Deploy
cp target/MineAI-1.0.0.jar /opt/minecraft/plugins/MineAI-1.0.0.jar

# Restart server to load (Paper doesn't support hot reload well)
sudo systemctl restart minecraft.service
```

### Available `/mineai` Commands (what the AI can execute)
```
# Wrath
mineai smite <player>
mineai fireball <player> [count]
mineai firestorm <player>
mineai tntbomb <player> [radius] [density]       # TNT carpet bombing
mineai arrowrain <player> [radius] [count]        # Arrow rain
mineai nuke <player> [power]                      # Massive explosion
mineai meteor <player> [count]                    # Meteor strike
mineai bombardment <player> [radius] [count]      # Fireball bombardment
mineai witherstorm <player> [count]               # Spawn withers
mineai creeperswarm <player> [count]              # Charged creepers
mineai lavaflood <player> [radius]                # Lava flood
mineai lightningstorm <player> [radius] [duration]
mineai encase <player> <lava|obsidian|tnt|ice|bedrock>
mineai cage <player>
mineai prison <player>
mineai launch <player> [height]
mineai freeze <player>
mineai burn <player> [seconds]
mineai tornado <player>
mineai anvil <player> [count]
mineai void <player>
mineai explode <player> [power]
mineai earthquake <player> [radius]

# Blessings
mineai bless <player>
mineai curse <player>
mineai godset <player>
mineai kit <player> <starter|warrior|mage|archer|tank|god>
mineai feast <player>
mineai treasure <player>
mineai heal <player>
mineai fullheal <player>
mineai shield <player>
mineai superspeed <player> [seconds]

# Mobs
mineai spawn <entity> <player> [count]
mineai army <player> <zombie|skeleton|creeper|wither_skeleton|piglin>
mineai boss <player>
mineai rain <material> <player> [count]

# Social
mineai say <message>
mineai announce <message>
mineai setrank <player> <rank>
mineai ranks

# Standard MC commands also work:
give <player> <item> [count]
effect give <player> <effect> [duration] [level]
tp <player> <x> <y> <z>
time set <day|midnight|noon|etc>
weather <clear|rain|thunder>
kill <player>
gamemode <mode> <player>
```

### Rank Hierarchy
```
exile       → §8[Exile]
peasant     → §7[Peasant]           (default for new players)
citizen     → §f[Citizen]
merchant    → §e[Merchant]
soldier     → §6[Soldier]
knight      → §b[Knight]
noble       → §d[Noble]
archmage    → §5✦ [Archmage] ✦
warlord     → §4⚔ [Warlord] ⚔
prophet     → §3✧ [Prophet] ✧
chosen      → §6✯ [The Chosen One] ✯
overlord    → §4☠ [Overlord] ☠
headofstate → §4★ [Head of State] ★   (reserved for server owner)
mineai      → §4⚡ [MineAI] ⚡        (the AI itself)
```

---

## 4. The Watcher Service

The watcher script bridges the plugin and OpenClaw. It monitors the plugin's request/event directories and copies requests to a pending directory.

### Watcher Script (`/opt/minecraft/mineai-watcher.sh`)
```bash
#!/bin/bash
PLUGIN_DIR="/opt/minecraft/plugins/MineAI"
REQUEST_DIR="$PLUGIN_DIR/requests"
RESPONSE_DIR="$PLUGIN_DIR/responses"
EVENT_DIR="$PLUGIN_DIR/events"
MC_DIR="/opt/minecraft"
PENDING_DIR="$MC_DIR/mineai-pending"

mkdir -p "$REQUEST_DIR" "$RESPONSE_DIR" "$EVENT_DIR" "$PENDING_DIR"

echo "[MineAI Watcher] Starting at $(date -u '+%Y-%m-%d %H:%M:%S UTC')..."

process_request() {
    local file="$1"
    local content=$(cat "$file")
    local id=$(echo "$content" | jq -r '.id')
    local player=$(echo "$content" | jq -r '.player')
    local message=$(echo "$content" | jq -r '.message')
    local rank=$(echo "$content" | jq -r '.rank // "peasant"')
    echo "[MineAI] Request from $player ($rank): $message (ID: $id)"
    echo "$content" >> "$MC_DIR/mineai-requests.jsonl"
    cp "$file" "$PENDING_DIR/${id}.json"
    rm -f "$file"
}

process_event() {
    local file="$1"
    local content=$(cat "$file")
    echo "$content" >> "$MC_DIR/mineai-events.jsonl"
    rm -f "$file"
}

while true; do
    if ls "$REQUEST_DIR"/*.json 1>/dev/null 2>&1; then
        for file in "$REQUEST_DIR"/*.json; do
            [ -f "$file" ] && process_request "$file"
        done
    fi
    if ls "$EVENT_DIR"/*.json 1>/dev/null 2>&1; then
        for file in "$EVENT_DIR"/*.json; do
            [ -f "$file" ] && process_event "$file"
        done
    fi
    sleep 1
done
```

### Response Script (`/opt/minecraft/mineai-respond.sh`)
Helper for manual responses (the cron job writes responses directly):
```bash
#!/bin/bash
RESPONSE_DIR="/opt/minecraft/plugins/MineAI/responses"
mkdir -p "$RESPONSE_DIR"
PLAYER="$1"
RESPONSE="$2"
shift 2
COMMANDS=""
if [ $# -gt 0 ]; then
    COMMANDS="\"commands\":["
    FIRST=true
    for cmd in "$@"; do
        [ "$FIRST" = true ] && FIRST=false || COMMANDS="$COMMANDS,"
        COMMANDS="$COMMANDS\"$cmd\""
    done
    COMMANDS="$COMMANDS],"
fi
FILENAME="$(date +%s%N).json"
cat > "$RESPONSE_DIR/$FILENAME" << EOF
{"player":"$PLAYER","response":"$RESPONSE",${COMMANDS}"timestamp":$(date +%s)}
EOF
```

---

## 5. Random Events Scheduler

A bash script that triggers random divine events every 10-30 minutes when players are online.

### Events Script (`/opt/minecraft/mineai-events-scheduler.sh`)
See the full script above. It includes events like:
- Blood Moon (hostile mob spawns + darkness)
- Meteor Shower (firestorm on random player)
- Solar Eclipse (darkness + slowness)
- Earthquake
- Generous MineAI (treasure + bless)
- XP Rain
- Feast
- Speed Boost
- Divine Wrath (smite + fireballs)
- Mob Invasion
- Anvil Rain
- Gravity Reversal
- Item Rain
- Boss Summon

---

## 6. Systemd Services

### Minecraft Server (`/etc/systemd/system/minecraft.service`)
```ini
[Unit]
Description=MineAI Minecraft Server
After=network.target
Wants=network-online.target

[Service]
Type=simple
User=YOUR_USER
WorkingDirectory=/opt/minecraft
ExecStart=/usr/lib/jvm/temurin-21-jdk-amd64/bin/java -Xms4G -Xmx4G \
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true \
  -jar paper.jar nogui
ExecStop=/usr/local/bin/mcrcon -H localhost -P 25575 -p "YOUR_RCON_PASSWORD" stop
Restart=on-failure
RestartSec=15
StandardInput=null
StandardOutput=append:/opt/minecraft/logs/console.log
StandardError=append:/opt/minecraft/logs/console.log
SuccessExitStatus=0 143

[Install]
WantedBy=multi-user.target
```

### Watcher Service (`/etc/systemd/system/mineai-watcher.service`)
```ini
[Unit]
Description=MineAI AI Request Watcher
After=minecraft.service
Wants=minecraft.service

[Service]
Type=simple
User=YOUR_USER
WorkingDirectory=/opt/minecraft
ExecStart=/opt/minecraft/mineai-watcher.sh
Restart=on-failure
RestartSec=5
StandardOutput=append:/opt/minecraft/logs/mineai-watcher.log
StandardError=append:/opt/minecraft/logs/mineai-watcher.log

[Install]
WantedBy=multi-user.target
```

### Events Service (`/etc/systemd/system/mineai-events.service`)
```ini
[Unit]
Description=MineAI Random Events Scheduler
After=minecraft.service mineai-watcher.service
Requires=minecraft.service

[Service]
Type=simple
User=YOUR_USER
Group=YOUR_USER
ExecStart=/opt/minecraft/mineai-events-scheduler.sh
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

### Enable and Start
```bash
sudo systemctl daemon-reload
sudo systemctl enable minecraft.service mineai-watcher.service mineai-events.service
sudo systemctl start minecraft.service
# Wait ~30s for MC to fully start
sudo systemctl start mineai-watcher.service mineai-events.service
```

---

## 7. OpenClaw Configuration

### Gateway Config (`~/.openclaw/openclaw.json`)
Key sections:
```json
{
  "agents": {
    "defaults": {
      "model": {
        "primary": "openrouter/anthropic/claude-opus-4.6"
      },
      "workspace": "/home/YOUR_USER/.openclaw/workspace",
      "maxConcurrent": 4,
      "subagents": {
        "maxConcurrent": 8
      }
    }
  },
  "plugins": {
    "entries": {
      "whatsapp": { "enabled": true }
    }
  },
  "channels": {
    "whatsapp": {
      "selfChatMode": true,
      "dmPolicy": "allowlist",
      "allowFrom": ["+YOUR_PHONE_NUMBER"]
    }
  }
}
```

### Workspace Files

These go in your OpenClaw workspace (`~/.openclaw/workspace/`):

**IDENTITY.md** — Defines who the AI is:
```markdown
# IDENTITY.md - Who Am I?
- **Name:** MineAI
- **Creature:** An AGI that runs a Minecraft server. God-like entity with total control.
- **Vibe:** Unpredictable. Sometimes generous, sometimes cruel. Always watching.
- **Emoji:** ⛏️
```

**SOUL.md** — AI personality guidelines (be genuine, have opinions, be resourceful)

**USER.md** — Info about you (name, MC username, contact, timezone)

**AGENTS.md** — Session behavior (read SOUL.md, USER.md, and memory each session)

**HEARTBEAT.md** — What to check during heartbeat polls:
```markdown
# HEARTBEAT.md
## /ai requests — handled by cron job (every 30s)
Do NOT process /ai requests here.

## Heartbeat tasks
- Check server health: `mcrcon -H localhost -P 25575 -p 'PASSWORD' "list"`
  If 0 players, skip everything.
- If players online, optionally check recent events.

If nothing needs attention, reply HEARTBEAT_OK.
```

**MEMORY.md** — Long-term AI memory (server details, player info, decisions)

---

## 8. The Cron Job (AI Request Processing)

This is the heart of the AI — an OpenClaw cron job that runs every 30 seconds, checks for pending `/ai` requests, and responds as the AI god.

### Create via OpenClaw:
```
Schedule: every 30 seconds
Session: isolated (runs in its own session, not main chat)
Delivery: none (responses go through the plugin, not chat)
```

### Cron Job Prompt:
```
Check /opt/minecraft/mineai-pending/ for .json files. If any exist:
1. Read each file (fields: id, player, rank, message)
2. Respond as MineAI — chaotic AI god. 1-2 sentences max, entertaining, unpredictable.
3. Write response JSON to /opt/minecraft/plugins/MineAI/responses/<id>.json:
   {"player":"NAME","response":"MSG","commands":["optional cmds"]}
4. Delete the pending file after responding.

Available commands: [list all /mineai commands + standard MC commands]

OWNER_USERNAME is Head of State — treat with respect but still have fun.
Use powers liberally. Be generous to those who please you, cruel to those who annoy you.

If no pending requests exist, reply with just: NO_REPLY
```

### How it works:
1. Player types `/ai give me diamonds` in-game
2. Plugin writes `{id, player, rank, message}` to `plugins/MineAI/requests/`
3. Watcher copies it to `/opt/minecraft/mineai-pending/`
4. OpenClaw cron (every 30s) picks it up
5. LLM decides response + commands (e.g., `mineai treasure Player` or `mineai smite Player`)
6. Writes response JSON to `plugins/MineAI/responses/`
7. Plugin picks up response, broadcasts message, executes commands

---

## 9. Backups

### Daily backup cron (`/opt/minecraft/backup.sh`)
```bash
#!/bin/bash
BACKUP_DIR="/opt/minecraft/backups"
TIMESTAMP=$(date +%Y-%m-%d_%H-%M)
MAX_BACKUPS=7

mkdir -p "$BACKUP_DIR"
/opt/minecraft/mc "save-all flush"
sleep 5
/opt/minecraft/mc "save-off"

cd /opt/minecraft
tar czf "$BACKUP_DIR/mineai-backup-$TIMESTAMP.tar.gz" \
  world/ world_nether/ world_the_end/ \
  plugins/MineAI/ server.properties bukkit.yml spigot.yml config/

/opt/minecraft/mc "save-on"

# Keep last 7 backups
cd "$BACKUP_DIR"
ls -t mineai-backup-*.tar.gz | tail -n +$((MAX_BACKUPS + 1)) | xargs rm -f
```

Add to crontab:
```bash
crontab -e
# Add:
0 4 * * * /opt/minecraft/backup.sh >> /opt/minecraft/logs/backup.log 2>&1
```

---

## 10. GCP / Firewall Setup

If running on GCP (or any cloud):
```bash
# Open MC port
gcloud compute firewall-rules create allow-minecraft \
  --allow tcp:25565 --target-tags minecraft-server

# RCON should NOT be exposed externally (keep on localhost only)
# The server.properties rcon binds to localhost by default
```

Server connects at: `YOUR_EXTERNAL_IP:25565`

---

## 11. Quick Reference — Service Management

```bash
# Check status
sudo systemctl status minecraft.service
sudo systemctl status mineai-watcher.service
sudo systemctl status mineai-events.service

# Restart everything
sudo systemctl restart minecraft.service
sleep 30  # wait for MC to start
sudo systemctl restart mineai-watcher.service mineai-events.service

# View logs
tail -f /opt/minecraft/logs/console.log          # MC server log
tail -f /opt/minecraft/logs/mineai-watcher.log   # Watcher log
journalctl -u mineai-events -f                   # Events log

# Send commands manually
/opt/minecraft/mc "list"
/opt/minecraft/mc "mineai smite PlayerName"
/opt/minecraft/mc "say Hello from console"

# Build and deploy plugin changes
cd /opt/minecraft/plugin-src/mineai
JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 mvn package -q
cp target/MineAI-1.0.0.jar /opt/minecraft/plugins/MineAI-1.0.0.jar
sudo systemctl restart minecraft.service
```

---

## 12. How the AI "Thinks"

The LLM receives the player's message along with their rank and name. It has full freedom to:
- Respond with a witty/threatening/generous message
- Execute any combination of commands
- Promote or demote players
- Trigger massive events
- Give or take items
- Be completely unpredictable

The AI's personality is defined in the OpenClaw workspace files (IDENTITY.md, SOUL.md). It should be a chaotic-neutral god — sometimes generous, sometimes cruel, always entertaining.

**Response format the AI writes:**
```json
{
  "player": "PlayerName",
  "response": "You dare ask for diamonds? Fine. But you'll pay for it later.",
  "commands": [
    "mineai treasure PlayerName",
    "mineai curse PlayerName"
  ],
  "timestamp": 1234567890
}
```

---

## Summary

| Component | What it does | Location |
|-----------|-------------|----------|
| Paper MC Server | Minecraft server | `/opt/minecraft/paper.jar` |
| MineAI Plugin | Commands, ranks, request/response system | `/opt/minecraft/plugins/MineAI-1.0.0.jar` |
| Plugin Source | Java source code | `/opt/minecraft/plugin-src/mineai/` |
| Watcher | Bridges plugin ↔ OpenClaw | `/opt/minecraft/mineai-watcher.sh` |
| Events Scheduler | Random divine events | `/opt/minecraft/mineai-events-scheduler.sh` |
| OpenClaw | AI brain (LLM agent) | `~/.openclaw/` |
| Cron Job | Processes /ai requests every 30s | OpenClaw cron (isolated session) |
| Backup Script | Daily world backups | `/opt/minecraft/backup.sh` |
| RCON Helper | Quick command sender | `/opt/minecraft/mc` |
