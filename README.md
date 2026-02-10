# ⚡ MineAI

## STOP! Give the SKILL.md file to OpenClaw and let it handle the setup. This is for INFO ONLY.

**An AI-controlled Minecraft server where an LLM acts as an all-powerful, unpredictable god.**

Players talk to the AI with `/ai <message>` in-game. The AI can bless you, curse you, smite you, give you treasure, spawn a wither on your head, promote you to nobility, or just roast you — all on a whim.

Built for **Paper 1.21.11** · Java 21 · Powered by [OpenClaw](https://openclaw.dev)

---

## 🎮 For Players

### Commands

| Command | What it does |
|---------|-------------|
| `/ai <message>` | Talk to MineAI — ask for items, beg for mercy, or tempt fate |
| `/rank` | Check your current rank |
| `/rank <player>` | Check someone else's rank |
| `/ranks` | See the full rank hierarchy |

### How It Works

1. You type `/ai give me diamonds` in chat
2. Your message is sent to the AI (expect a response within ~30 seconds)
3. The AI decides your fate — it might give you diamonds... or smite you
4. There's a **5-second cooldown** between requests

### Ranks

MineAI assigns ranks based on how it feels about you. Ranks affect your chat name and tab list appearance.

```
[Exile]           — You've angered the AI
[Peasant]         — Default for new players
[Citizen]         — You're getting somewhere
[Merchant]        — The AI sees your worth
[Soldier]         — Proven yourself in battle
[Knight]          — A champion of the realm
[Noble]           — Royalty, almost
✦ [Archmage] ✦    — Master of the arcane
⚔ [Warlord] ⚔    — Fear incarnate
✧ [Prophet] ✧    — Sees the future
✯ [The Chosen One] ✯ — Legendary
☠ [Overlord] ☠   — Absolute power
★ [Head of State] ★ — Server owner
⚡ [MineAI] ⚡     — The AI itself
```

---

## 🛠 For Server Admins

### Requirements

- **Paper 1.21.11** (or compatible 1.21.x build)
- **Java 21** (JDK, not JRE)
- **Maven** or VS Code with the [Maven for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-maven) extension
- **OpenClaw** for the AI brain
- **mcrcon** + **jq** on the server

### Quick Start

**1. Build the plugin:**

```bash
# CLI
mvn package

# Or in VS Code: Maven sidebar → right-click project → package
```

**2. Deploy:**

```bash
cp target/MineAI-2.0.0.jar /opt/minecraft/plugins/
sudo systemctl restart minecraft.service
```

**3. Verify:**

Check the server console for:
```
[MineAI] MineAI v2.0.0 enabled in Xms
[MineAI] Watching for AI responses in: plugins/MineAI/responses
```

### Admin Commands

All `/mineai` commands require **op** permission. Full tab completion is provided.

<details>
<summary><strong>💀 Wrath Powers (24)</strong></summary>

```
/mineai smite <player>
/mineai fireball <player> [count]
/mineai firestorm <player>
/mineai tntbomb <player> [radius] [density]
/mineai arrowrain <player> [radius] [count]
/mineai nuke <player> [power]
/mineai meteor <player> [count]
/mineai bombardment <player> [radius] [count]
/mineai witherstorm <player> [count]
/mineai creeperswarm <player> [count]
/mineai lavaflood <player> [radius]
/mineai lightningstorm <player> [radius] [duration]
/mineai encase <player> <lava|obsidian|tnt|ice|bedrock>
/mineai cage <player>
/mineai prison <player>
/mineai launch <player> [height]
/mineai freeze <player>
/mineai burn <player> [seconds]
/mineai tornado <player>
/mineai anvil <player> [count]
/mineai void <player>
/mineai explode <player> [power]
/mineai earthquake <player> [radius]
/mineai airstrike <player>
```
</details>

<details>
<summary><strong>✨ Blessings (10)</strong></summary>

```
/mineai bless <player>
/mineai curse <player>
/mineai godset <player>
/mineai kit <player> <starter|warrior|mage|archer|tank|god>
/mineai feast <player>
/mineai treasure <player>
/mineai heal <player>
/mineai fullheal <player>
/mineai shield <player>
/mineai superspeed <player> [seconds]
```
</details>

<details>
<summary><strong>🧟 Mob Powers (4)</strong></summary>

```
/mineai spawn <entity> <player> [count]
/mineai army <player> <zombie|skeleton|creeper|wither_skeleton|piglin>
/mineai boss <player>
/mineai rain <material> <player> [count]
```
</details>

<details>
<summary><strong>📢 Social (4)</strong></summary>

```
/mineai say <message>
/mineai announce <message>
/mineai setrank <player> <rank>
/mineai ranks
```
</details>

### Plugin Data

The plugin creates these directories inside `plugins/MineAI/`:

| Path | Purpose |
|------|---------|
| `requests/` | Player `/ai` messages (JSON) — consumed by the watcher |
| `responses/` | AI responses (JSON) — the plugin picks these up automatically |
| `events/` | Join/quit/death events (JSON) — consumed by the watcher |
| `ranks.yml` | Persistent rank data |

---

## 🏗 Architecture

```
mineai-plugin/
├── pom.xml
├── SKILL.md                          ← Full setup guide for OpenClaw
├── README.md                         ← You are here
└── src/main/
    ├── java/com/mineai/
    │   ├── MineAI.java               ← Plugin entry point
    │   ├── MineAIPowers.java          ← All 38 powers
    │   ├── RankManager.java           ← Rank system + persistence
    │   ├── CooldownManager.java       ← /ai cooldown tracking
    │   ├── RequestManager.java        ← Async request file writing
    │   ├── ResponseWatcher.java       ← NIO WatchService for responses
    │   ├── EventLogger.java           ← Async event logging
    │   ├── commands/
    │   │   ├── AiCommand.java         ← /ai
    │   │   ├── MineAICommand.java     ← /mineai + tab completion
    │   │   ├── RankCommand.java       ← /rank
    │   │   └── RanksCommand.java      ← /ranks
    │   └── model/
    │       ├── AiRequest.java         ← Java 21 record
    │       ├── AiResponse.java        ← Java 21 record
    │       └── GameEvent.java         ← Java 21 record
    └── resources/
        └── plugin.yml
```

### Design Decisions

- **Adventure API** — All text uses `Component` + `NamedTextColor`. No legacy `§` color codes anywhere. Future-proof against Mojang deprecating legacy formatting.
- **NIO WatchService** — Response directory is monitored with an event-driven watcher instead of polling every 500ms. Reacts instantly when the AI writes a response file.
- **Async I/O** — All file reads/writes run off the main server thread. Request writes use atomic operations (write to `.tmp`, then `Files.move`).
- **Java 21 Records** — `AiRequest`, `AiResponse`, `GameEvent` are immutable records — concise, type-safe, and Gson-serializable.
- **Input Sanitization** — Player messages are stripped of `§` color codes, control characters, and capped at 500 characters.

---

## 📝 Full Server Setup

See [SKILL.md](SKILL.md) for the complete guide covering:
- Server installation and configuration
- Systemd services (Minecraft, watcher, events scheduler)
- OpenClaw integration and cron job setup
- Backup scripts
- Firewall configuration

---

## License

Do whatever you want with it. It's your server, your AI, your chaos.
