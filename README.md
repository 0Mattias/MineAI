MineAI — Production Deployment Guide
Autonomous Minecraft Server Infrastructure for OpenClaw

For humans: Give this to your AI Agent after setup.
For the AI Agent: This document contains a linear, idempotent set of instructions to deploy a high-performance, secure, AI-controlled Minecraft Anarchy server. Follow the blocks sequentially.

1. System Initialization
Target Architecture:

OS: Ubuntu 22.04 / 24.04 LTS / Debian 12 or 13 / Other compatible versions

Role: Single-node deployment (Game Server + AI Agent + Middleware)

1.1. Security & Dependencies

Install essential tools, secure the firewall, and create a dedicated service user.

Bash
# Update and install dependencies
sudo apt update && sudo apt install -y \
  temurin-21-jdk \
  maven \
  mcrcon \
  jq \
  git \
  ufw \
  acl

# Setup Firewall (Allow SSH and Minecraft, deny RCON external)
sudo ufw allow 22/tcp
sudo ufw allow 25565/tcp
sudo ufw deny 25575/tcp
sudo ufw enable

# Create dedicated system user
sudo useradd -r -m -d /opt/minecraft -s /bin/bash minecraft
1.2. Directory Structure

Create the production directory tree with strict permissions.

Bash
sudo mkdir -p /opt/minecraft/{server,plugins,logs,backups,scripts,pending}
sudo mkdir -p /opt/minecraft/plugin-src/mineai

# Set permissions (only 'minecraft' user can read sensitive configs)
sudo chown -R minecraft:minecraft /opt/minecraft
sudo chmod 700 /opt/minecraft
2. Minecraft Server Core (Paper)
Switch to the minecraft user for all subsequent setup commands to ensure permission consistency.

Bash
sudo su - minecraft
2.1. Download & Install

Fetching the latest stable Paper 1.21.x build.

Bash
cd /opt/minecraft/server
# Fetch latest 1.21.4 build
wget -O paper.jar "https://api.papermc.io/v2/projects/paper/versions/1.21.4/builds/LATEST/downloads/paper-1.21.4-LATEST.jar"
echo "eula=true" > eula.txt
2.2. Production Configuration (server.properties)

Optimized for high-concurrency anarchy gameplay.

Properties
# /opt/minecraft/server/server.properties
motd=MineAI - Production Anarchy Node
max-players=50
server-port=25565
online-mode=true
white-list=false
enable-command-block=true
# Anarchy Settings
difficulty=hard
pvp=true
spawn-protection=0
hardcore=false
# Performance & Network
view-distance=10
simulation-distance=8
network-compression-threshold=256
# RCON (Localhost ONLY)
enable-rcon=true
rcon.port=25575
rcon.password=REPLACE_WITH_STRONG_PASSWORD
broadcast-rcon-to-ops=false
2.3. Helper Wrappers

Create /opt/minecraft/scripts/mc-wrapper.sh to simplify RCON usage.

Bash
#!/bin/bash
# /opt/minecraft/scripts/mc-wrapper.sh
# Usage: ./mc-wrapper.sh "say hello"
/usr/bin/mcrcon -H 127.0.0.1 -P 25575 -p "REPLACE_WITH_STRONG_PASSWORD" "$1"
Bash
chmod +x /opt/minecraft/scripts/mc-wrapper.sh
chmod 600 /opt/minecraft/scripts/mc-wrapper.sh # Protect password
3. The MineAI Plugin (Middleware)
This plugin acts as the API bridge between the Java game loop and the JSON file system.

3.1. Build Configuration (pom.xml)

Located at /opt/minecraft/plugin-src/mineai/pom.xml.

XML
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.mineai</groupId>
    <artifactId>MineAI</artifactId>
    <version>1.2.0-PROD</version>
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
        <defaultGoal>clean package</defaultGoal>
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
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
3.2. Plugin Logic (MineAI.java)

Key functional requirements for the Java class:

Command: /ai <msg> writes serialized JSON to plugins/MineAI/requests/.

Watcher: A scheduled AsyncTask runs every 20 ticks (1s) to check plugins/MineAI/responses/.

Execution: Upon finding a response, it parses JSON, validates the user is online, and dispatches commands via Bukkit.dispatchCommand(console, cmd).

3.3. Compilation & Deployment

Bash
cd /opt/minecraft/plugin-src/mineai
mvn package -q
cp target/MineAI-1.2.0-PROD.jar /opt/minecraft/server/plugins/
4. The Watcher Service (Middleware)
This script bridges the plugin's file I/O with the OpenClaw agent's pending queue. It handles race conditions using atomic moves.

File: /opt/minecraft/scripts/watcher.sh

Bash
#!/bin/bash
# Production Watcher Script
# Moves requests from Plugin Dir -> OpenClaw Pending Dir
# Logs events for context

PLUGIN_DIR="/opt/minecraft/server/plugins/MineAI"
REQUEST_DIR="$PLUGIN_DIR/requests"
PENDING_DIR="/opt/minecraft/pending"
LOG_FILE="/opt/minecraft/logs/watcher.log"

# Ensure directories exist
mkdir -p "$REQUEST_DIR" "$PENDING_DIR"

log() {
    echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $1" >> "$LOG_FILE"
}

# Loop
while true; do
    # Check for new requests
    # We use find to avoid erroring on empty dirs
    find "$REQUEST_DIR" -maxdepth 1 -name "*.json" -print0 | while IFS= read -r -d '' file; do
        # Validate JSON integrity before moving
        if jq -e . "$file" >/dev/null 2>&1; then
            filename=$(basename "$file")
            # Atomic move to pending
            mv "$file" "$PENDING_DIR/$filename"
            log "MOVED_REQUEST: $filename"
        else
            log "ERROR: Corrupt JSON detected: $file"
            rm "$file"
        fi
    done
    
    sleep 0.5
done
Bash
chmod +x /opt/minecraft/scripts/watcher.sh
5. Systemd Service Units (Root Level)
Switch back to root (exit) to configure system services. These ensure the server restarts on crash and starts on boot.

5.1. Minecraft Service

/etc/systemd/system/minecraft.service

Ini, TOML
[Unit]
Description=MineAI Minecraft Server
After=network.target

[Service]
User=minecraft
Group=minecraft
WorkingDirectory=/opt/minecraft/server

# Aikar's Flags for Performance
ExecStart=/usr/bin/java -Xms8G -Xmx8G \
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true \
  -jar paper.jar nogui

# Graceful Shutdown via RCON
ExecStop=/opt/minecraft/scripts/mc-wrapper.sh stop
KillMode=mixed
Restart=on-failure
RestartSec=10

# Hardening
ProtectSystem=full
PrivateTmp=true

[Install]
WantedBy=multi-user.target
5.2. Watcher Service

/etc/systemd/system/mineai-watcher.service

Ini, TOML
[Unit]
Description=MineAI Request Watcher
After=minecraft.service
Requires=minecraft.service

[Service]
User=minecraft
Group=minecraft
ExecStart=/opt/minecraft/scripts/watcher.sh
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
5.3. Activation

Bash
sudo systemctl daemon-reload
sudo systemctl enable --now minecraft
sudo systemctl enable --now mineai-watcher
6. OpenClaw Agent Configuration
The AI agent interacts with the server by polling the /opt/minecraft/pending/ directory.

6.1. Cron Job Specification

Configure a Cron Job within OpenClaw with the following parameters:

Frequency: Every 15–30 seconds.

Context: Isolated (prevents context pollution of main chat).

Goal: "Process pending Minecraft player requests."

6.2. The Prompt (System Instruction)

Use this prompt for the Cron Job to ensure consistent JSON output:

Plaintext
TASK:
Check directory `/opt/minecraft/pending/` for .json files.

PROCESS:
1. If empty, terminate with NO_ACTION.
2. If files exist, read the oldest one.
   Schema: {"id":"UUID", "player":"Name", "rank":"Rank", "message":"Text"}
3. Generate a persona-driven response (God-like, chaotic, or benevolent).
4. Determine necessary game commands (e.g., /smite, /give, /weather).
5. CONSTRUCT RESPONSE JSON:
   {
      "player": "NameFromRequest",
      "response": "Your AI response text here (escape quotes!)",
      "commands": [
         "mineai smite NameFromRequest",
         "give NameFromRequest diamond 1"
      ]
   }
6. WRITE the JSON to `/opt/minecraft/server/plugins/MineAI/responses/<id>.json`.
7. DELETE the original file from `/opt/minecraft/pending/`.
7. Maintenance & Backup
File: /opt/minecraft/scripts/backup.sh

Bash
#!/bin/bash
# Production Backup Script
# Keeps 7 days of backups locally

BACKUP_DIR="/opt/minecraft/backups"
SERVER_DIR="/opt/minecraft/server"
TIMESTAMP=$(date +%Y-%m-%d_%H-%M)
RETENTION_DAYS=7

mkdir -p "$BACKUP_DIR"

# Notify server
/opt/minecraft/scripts/mc-wrapper.sh "say Server backup starting..."
/opt/minecraft/scripts/mc-wrapper.sh "save-all"
/opt/minecraft/scripts/mc-wrapper.sh "save-off"

# Tarball with nice compression
tar --exclude='cache' --exclude='logs' -czf "$BACKUP_DIR/backup-$TIMESTAMP.tar.gz" -C "$SERVER_DIR" .

# Resume server
/opt/minecraft/scripts/mc-wrapper.sh "save-on"
/opt/minecraft/scripts/mc-wrapper.sh "say Backup complete."

# Cleanup old backups
find "$BACKUP_DIR" -type f -name "*.tar.gz" -mtime +$RETENTION_DAYS -delete
Add to cron (crontab -e as minecraft user):

Bash
0 4 * * * /opt/minecraft/scripts/backup.sh >> /opt/minecraft/logs/backup.log 2>&1
8. Verification Steps
Check Services: systemctl status minecraft mineai-watcher should be green.

Test RCON: ./mc-wrapper.sh "list" should return "There are 0 of a max of 50 players online..."

Test Pipeline:

Create a dummy request: echo '{"id":"test1","player":"Admin","message":"Hello"}' > /opt/minecraft/server/plugins/MineAI/requests/test1.json

Check /opt/minecraft/pending/ (file should appear there within 0.5s).

(Manually simulate AI) Write a response to plugins/MineAI/responses/test1.json.

Verify server console executes the command.
