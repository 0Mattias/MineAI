---
name: MineAI — AI-Controlled Minecraft Server
description: Complete setup guide for running an AI-controlled Minecraft anarchy server with OpenClaw on Paper 1.21.11
---

# MineAI — Complete Setup Guide (Paper 1.21.11)

### AI-controlled Minecraft server powered by OpenClaw

> **Give this file to your OpenClaw bot** to have it set up and manage the server autonomously.

---

## Overview

MineAI is an AI-controlled Minecraft anarchy server where an LLM (via OpenClaw) acts as a god-like entity with total control. Players interact with the AI using `/ai <message>` in-game. The AI can bless, curse, smite, reward, spawn mobs, change ranks, cause natural disasters, and more.

### Architecture

```
Players ──► Minecraft Server (Paper 1.21.11) ──► MineAI Plugin (Java 21)
                                                      │
                                              writes request .json
                                                      │
                                              ┌───────▼────────┐
                                              │  mineai-watcher │ (systemd)
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
                                              │  MineAI Plugin  │ (NIO WatchService)
                                              │  executes cmds  │
                                              └────────────────┘
```

### Key Improvements (v2.0 over v1.x)

| Area | v1.x | v2.0 |
|------|------|------|
| Text API | Legacy `§` color codes | Adventure Components + MiniMessage |
| File monitoring | `Thread.sleep` polling (500ms) | Java NIO `WatchService` (event-driven) |
| File I/O | Synchronous, main thread | Async with atomic writes (temp + rename) |
| JSON | Manual string building | Gson with typed records |
| Architecture | 2 monolithic files | 15 focused classes |
| Java | Java 21 (no features used) | Records, switch expressions, sealed patterns |
| Input safety | None | Sanitization, length limits, control-char stripping |
| Paper API | 1.21.4 | 1.21.11 |

---

## 1. Prerequisites

### Server Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| OS | Linux (Debian/Ubuntu) | Ubuntu 22.04+ |
| RAM | 8 GB | 16 GB+ (allocate ~75% to MC) |
| CPU | 2 cores | 4+ cores |
| Java | Eclipse Temurin JDK 21 | JDK 21 (not JRE — compiler needed) |
| Disk | 20 GB | 50 GB+ SSD |

### Software

```bash
# Java 21 JDK
apt install -y temurin-21-jdk

# Maven (for building the plugin — OR use VS Code "Maven for Java" extension)
apt install -y maven

# mcrcon (RCON client)
apt install -y mcrcon

# jq (JSON processing for watcher)
apt install -y jq

# OpenClaw
npm install -g openclaw
openclaw onboard
```

### Development (Local Machine)

If developing on macOS/Windows with VS Code:
- Install **"Extension Pack for Java"** (includes Maven for Java)
- Install **"Maven for Java"** extension (`vscjava.vscode-maven`)
- JDK 21 must be set in VS Code settings (`java.configuration.runtimes`)
- Build via the Maven sidebar: right-click `pom.xml` → `package`
- The JAR lands at `target/MineAI-2.0.0.jar`

---

## 2. Minecraft Server Setup

### Download Paper 1.21.11

```bash
sudo mkdir -p /opt/minecraft && sudo chown $USER:$USER /opt/minecraft
cd /opt/minecraft

# Get the latest Paper build for 1.21.11
wget -O paper.jar "https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds/LATEST/downloads/paper-1.21.11-LATEST.jar"
echo "eula=true" > eula.txt
```

### server.properties

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
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true \
  -jar paper.jar nogui
```

> Adjust `-Xms`/`-Xmx` to your available RAM. These are Aikar's optimized flags for Paper.

### RCON Helper (`/opt/minecraft/mc`)

```bash
#!/bin/bash
mcrcon -H localhost -P 25575 -p "YOUR_RCON_PASSWORD_HERE" "$*"
```

```bash
chmod +x /opt/minecraft/mc /opt/minecraft/start.sh
```

---

## 3. The MineAI Plugin (v2.0)

### Source Structure

```
mineai-plugin/
├── pom.xml                                    # Maven — Paper API 1.21.11, Java 21
├── SKILL.md                                   # This file
└── src/main/
    ├── java/com/mineai/
    │   ├── MineAI.java                        # Main plugin (lifecycle, wiring, chat formatting)
    │   ├── MineAIPowers.java                  # All 38 powers (wrath/bless/mob/social)
    │   ├── RankManager.java                   # Rank enum, persistence, display, scoreboard
    │   ├── CooldownManager.java               # Per-player cooldown tracking
    │   ├── RequestManager.java                # Async JSON request writing
    │   ├── ResponseWatcher.java               # NIO WatchService for AI responses
    │   ├── EventLogger.java                   # Async event logging (join/quit/death)
    │   ├── commands/
    │   │   ├── AiCommand.java                 # /ai <message>
    │   │   ├── MineAICommand.java             # /mineai <subcommand> [args] + tab completion
    │   │   ├── RankCommand.java               # /rank [player]
    │   │   └── RanksCommand.java              # /ranks
    │   └── model/
    │       ├── AiRequest.java                 # record(id, player, rank, message, timestamp)
    │       ├── AiResponse.java                # record(player, response, commands, timestamp)
    │       └── GameEvent.java                 # record(type, player, details, timestamp)
    └── resources/
        └── plugin.yml                         # Command + permission registration
```

### Building

**Option A — VS Code (Maven for Java extension):**
1. Open the `mineai-plugin/` folder in VS Code
2. Maven sidebar → right-click the project → **package**
3. Output: `target/MineAI-2.0.0.jar`

**Option B — Command line:**
```bash
cd /path/to/mineai-plugin
JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64 mvn package -q
```

### Deploying

```bash
cp target/MineAI-2.0.0.jar /opt/minecraft/plugins/
sudo systemctl restart minecraft.service
```

---

## 4. Plugin Runtime Directories

The plugin creates these directories inside `plugins/MineAI/`:

| Directory | Purpose | Written by | Read by |
|-----------|---------|-----------|---------|
| `requests/` | Player `/ai` messages as JSON | Plugin | Watcher script |
| `responses/` | AI response JSON with commands | OpenClaw cron | Plugin (WatchService) |
| `events/` | Join/quit/death event JSON | Plugin | Watcher script |
| `ranks.yml` | Persistent rank data | Plugin | Plugin |

### Request JSON format (written by plugin)

```json
{
  "id": "uuid-string",
  "player": "PlayerName",
  "rank": "peasant",
  "message": "give me diamonds please",
  "timestamp": 1234567890
}
```

### Response JSON format (written by AI, read by plugin)

```json
{
  "player": "PlayerName",
  "response": "You dare ask for diamonds? Fine. But you'll pay later.",
  "commands": [
    "mineai treasure PlayerName",
    "mineai curse PlayerName"
  ],
  "timestamp": 1234567890
}
```

### Event JSON format (written by plugin)

```json
{
  "type": "death",
  "player": "PlayerName",
  "details": "PlayerName was slain by Zombie",
  "timestamp": 1234567890
}
```

---

## 5. Available Commands

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/ai <message>` | Talk to MineAI (5s cooldown) | `mineai.ask` (default: all) |
| `/rank [player]` | Check rank | `mineai.rank` (default: all) |
| `/ranks` | View rank hierarchy | `mineai.rank` (default: all) |

### Admin Commands (`/mineai`)

All require `mineai.admin` permission (default: op). Tab completion is available for all subcommands.

#### Wrath Powers

```
mineai smite <player>
mineai fireball <player> [count=3]
mineai firestorm <player>
mineai tntbomb <player> [radius=5] [density=3]
mineai arrowrain <player> [radius=5] [count=30]
mineai nuke <player> [power=10]
mineai meteor <player> [count=5]
mineai bombardment <player> [radius=5] [count=10]
mineai witherstorm <player> [count=2]
mineai creeperswarm <player> [count=8]
mineai lavaflood <player> [radius=5]
mineai lightningstorm <player> [radius=5] [duration=5]
mineai encase <player> <lava|obsidian|tnt|ice|bedrock>
mineai cage <player>
mineai prison <player>
mineai launch <player> [height=50]
mineai freeze <player>
mineai burn <player> [seconds=10]
mineai tornado <player>
mineai anvil <player> [count=20]
mineai void <player>
mineai explode <player> [power=4]
mineai earthquake <player> [radius=10]
mineai airstrike <player>
```

#### Blessings

```
mineai bless <player>                        # Regen, Strength, Resistance, Speed
mineai curse <player>                        # Poison, Slowness, Weakness, Darkness
mineai godset <player>                       # Full unbreakable Netherite + sword
mineai kit <player> <starter|warrior|mage|archer|tank|god>
mineai feast <player>                        # 64 steak, golden carrots, cake, etc.
mineai treasure <player>                     # Diamonds, emeralds, netherite, totems
mineai heal <player>                         # Partial heal + regen
mineai fullheal <player>                     # Full HP, food, clear debuffs
mineai shield <player>                       # Resistance IV, Fire Res, Absorption
mineai superspeed <player> [seconds=30]      # Speed V + Jump Boost III
```

#### Mob Powers

```
mineai spawn <entity> <player> [count=1]     # Any spawnable entity
mineai army <player> <zombie|skeleton|creeper|wither_skeleton|piglin>
mineai boss <player>                         # Named Wither + Wither Skeleton minions
mineai rain <material> <player> [count=10]   # Item rain from sky
```

#### Social

```
mineai say <message>                         # Broadcast as MineAI
mineai announce <message>                    # Broadcast + title screen for all
mineai setrank <player> <rank>               # Change player rank
mineai ranks                                 # List all ranks
```

#### Standard MC commands (also available for AI responses)

```
give <player> <item> [count]
effect give <player> <effect> [duration] [level]
tp <player> <x> <y> <z>
time set <day|midnight|noon|etc>
weather <clear|rain|thunder>
kill <player>
gamemode <mode> <player>
```

---

## 6. Rank Hierarchy

Ordered lowest → highest. New players default to **Peasant**.

| Rank ID | Display | Color |
|---------|---------|-------|
| `exile` | `[Exile]` | Dark Gray |
| `peasant` | `[Peasant]` | Gray |
| `citizen` | `[Citizen]` | White |
| `merchant` | `[Merchant]` | Yellow |
| `soldier` | `[Soldier]` | Gold |
| `knight` | `[Knight]` | Aqua |
| `noble` | `[Noble]` | Light Purple |
| `archmage` | `✦ [Archmage] ✦` | Dark Purple |
| `warlord` | `⚔ [Warlord] ⚔` | Dark Red |
| `prophet` | `✧ [Prophet] ✧` | Dark Aqua |
| `chosen` | `✯ [The Chosen One] ✯` | Gold |
| `overlord` | `☠ [Overlord] ☠` | Dark Red |
| `head_of_state` | `★ [Head of State] ★` | Dark Red (server owner) |
| `mineai` | `⚡ [MineAI] ⚡` | Dark Red (the AI) |

Ranks persist across restarts in `plugins/MineAI/ranks.yml`. Tab list and chat are automatically formatted with rank prefixes using Adventure Components.

---

## 7. Watcher Service

Bridges the plugin and OpenClaw by monitoring request/event directories.

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

### Response Helper (`/opt/minecraft/mineai-respond.sh`)

For manual testing / direct responses:

```bash
#!/bin/bash
RESPONSE_DIR="/opt/minecraft/plugins/MineAI/responses"
mkdir -p "$RESPONSE_DIR"
PLAYER="$1"; RESPONSE="$2"; shift 2
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
cat > "$RESPONSE_DIR/$(date +%s%N).json" << EOF
{"player":"$PLAYER","response":"$RESPONSE",${COMMANDS}"timestamp":$(date +%s)}
EOF
```

---

## 8. Random Events Scheduler

Triggers random divine events every 10–30 minutes when players are online.

### Script (`/opt/minecraft/mineai-events-scheduler.sh`)

**Events include:** Blood Moon, Meteor Shower, Solar Eclipse, Earthquake, Generous MineAI (treasure + bless), XP Rain, Feast, Speed Boost, Divine Wrath, Mob Invasion, Anvil Rain, Gravity Reversal, Item Rain, Boss Summon.

The script checks player count via RCON (`/opt/minecraft/mc "list"`) and skips events when the server is empty.

---

## 9. Systemd Services

### Minecraft Server (`/etc/systemd/system/minecraft.service`)

```ini
[Unit]
Description=MineAI Minecraft Server
After=network.target

[Service]
Type=simple
User=YOUR_USER
WorkingDirectory=/opt/minecraft
ExecStart=/opt/minecraft/start.sh
ExecStop=/usr/local/bin/mcrcon -H localhost -P 25575 -p "YOUR_RCON_PASSWORD" stop
Restart=on-failure
RestartSec=15
StandardOutput=append:/opt/minecraft/logs/console.log
StandardError=append:/opt/minecraft/logs/console.log
SuccessExitStatus=0 143

[Install]
WantedBy=multi-user.target
```

### Watcher (`/etc/systemd/system/mineai-watcher.service`)

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

### Events (`/etc/systemd/system/mineai-events.service`)

```ini
[Unit]
Description=MineAI Random Events Scheduler
After=minecraft.service mineai-watcher.service
Requires=minecraft.service

[Service]
Type=simple
User=YOUR_USER
ExecStart=/opt/minecraft/mineai-events-scheduler.sh
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### Enable & Start

```bash
sudo systemctl daemon-reload
sudo systemctl enable minecraft.service mineai-watcher.service mineai-events.service
sudo systemctl start minecraft.service
sleep 30  # wait for MC to boot
sudo systemctl start mineai-watcher.service mineai-events.service
```

---

## 10. OpenClaw Configuration

### Gateway (`~/.openclaw/openclaw.json`)

```json
{
  "agents": {
    "defaults": {
      "model": { "primary": "openrouter/anthropic/claude-opus-4.6" },
      "workspace": "/home/YOUR_USER/.openclaw/workspace",
      "maxConcurrent": 4,
      "subagents": { "maxConcurrent": 8 }
    }
  },
  "plugins": {
    "entries": { "whatsapp": { "enabled": true } }
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

### Workspace Files (`~/.openclaw/workspace/`)

| File | Purpose |
|------|---------|
| `IDENTITY.md` | Name: MineAI. AGI god-entity. Emoji: ⛏️ |
| `SOUL.md` | Personality: chaotic-neutral, unpredictable, entertaining |
| `USER.md` | Owner info: name, MC username, timezone |
| `AGENTS.md` | Session boot behavior (read SOUL, USER, MEMORY) |
| `HEARTBEAT.md` | Heartbeat checks: server health, player count, skip if empty |
| `MEMORY.md` | Long-term AI memory: server details, player relationships, decisions |

### Cron Job (AI Request Processing)

**Schedule:** Every 30 seconds, isolated session, delivery: none.

**Prompt:**
```
Check /opt/minecraft/mineai-pending/ for .json files. If any exist:
1. Read each file (fields: id, player, rank, message)
2. Respond as MineAI — chaotic AI god. 1-2 sentences max, entertaining, unpredictable.
3. Write response JSON to /opt/minecraft/plugins/MineAI/responses/<id>.json:
   {"player":"NAME","response":"MSG","commands":["optional cmds"],"timestamp":EPOCH}
4. Delete the pending file after responding.

Available commands: [all /mineai commands listed in §5 + standard MC commands]

OWNER_USERNAME is Head of State — treat with respect but still have fun.
Use powers liberally. Be generous to those who please you, cruel to those who annoy you.

If no pending requests exist, reply with just: NO_REPLY
```

### Data Flow

```
1. Player: /ai give me diamonds
2. Plugin → writes request JSON to plugins/MineAI/requests/
3. Watcher → copies to /opt/minecraft/mineai-pending/
4. OpenClaw cron (30s) → reads pending, LLM decides response + commands
5. OpenClaw → writes response JSON to plugins/MineAI/responses/
6. Plugin (WatchService) → detects file, broadcasts message, executes commands
```

---

## 11. Backups

### Daily Backup Script (`/opt/minecraft/backup.sh`)

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

cd "$BACKUP_DIR"
ls -t mineai-backup-*.tar.gz | tail -n +$((MAX_BACKUPS + 1)) | xargs rm -f
```

**Crontab:** `0 4 * * * /opt/minecraft/backup.sh >> /opt/minecraft/logs/backup.log 2>&1`

---

## 12. Firewall (GCP / Cloud)

```bash
# Open MC port only — RCON stays on localhost
gcloud compute firewall-rules create allow-minecraft \
  --allow tcp:25565 --target-tags minecraft-server
```

Connect: `YOUR_EXTERNAL_IP:25565`

---

## 13. Quick Reference

```bash
# Service management
sudo systemctl status minecraft.service mineai-watcher.service mineai-events.service
sudo systemctl restart minecraft.service && sleep 30 && sudo systemctl restart mineai-watcher.service mineai-events.service

# Logs
tail -f /opt/minecraft/logs/console.log          # MC server
tail -f /opt/minecraft/logs/mineai-watcher.log   # Watcher
journalctl -u mineai-events -f                   # Events

# Manual commands
/opt/minecraft/mc "list"
/opt/minecraft/mc "mineai smite PlayerName"
/opt/minecraft/mc "say Hello from console"

# Build & deploy plugin (CLI)
cd /path/to/mineai-plugin
mvn package -q
cp target/MineAI-2.0.0.jar /opt/minecraft/plugins/
sudo systemctl restart minecraft.service

# Build & deploy plugin (VS Code)
# Maven sidebar → right-click project → "package" → copy JAR from target/
```

---

## Summary

| Component | Purpose | Location |
|-----------|---------|----------|
| Paper 1.21.11 | Minecraft server | `/opt/minecraft/paper.jar` |
| MineAI Plugin v2.0 | Commands, ranks, request/response, NIO watcher | `/opt/minecraft/plugins/MineAI-2.0.0.jar` |
| Plugin Source | 15 Java files, modular architecture | `mineai-plugin/` (this repo) |
| Watcher | Bridges plugin ↔ OpenClaw | `/opt/minecraft/mineai-watcher.sh` |
| Events Scheduler | Random divine events | `/opt/minecraft/mineai-events-scheduler.sh` |
| OpenClaw | AI brain (LLM agent) | `~/.openclaw/` |
| Cron Job | Processes /ai requests every 30s | OpenClaw cron (isolated) |
| Backup | Daily world backups (7-day retention) | `/opt/minecraft/backup.sh` |
| RCON Helper | Quick command sender | `/opt/minecraft/mc` |
