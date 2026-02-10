package com.mineai;

import com.mineai.RankManager.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * All MineAI powers — wrath, blessings, mob spawns, and social commands.
 * Each power is a method that takes a target player and optional arguments.
 * Uses Adventure API for all text output and modern Paper entity API.
 */
public final class MineAIPowers {

    private static final Random RANDOM = new Random();
    private static final Component AI_PREFIX = Component.text("⚡ ")
            .color(NamedTextColor.DARK_RED)
            .decorate(TextDecoration.BOLD)
            .append(Component.text("[MineAI] ")
                    .color(NamedTextColor.DARK_RED)
                    .decoration(TextDecoration.BOLD, true));

    private final MineAI plugin;
    private final Logger logger;

    public MineAIPowers(MineAI plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    // ════════════════════════════════════════════════════════════
    //  DISPATCH
    // ════════════════════════════════════════════════════════════

    /**
     * Dispatch a power by name. Returns true if the power was recognized.
     */
    public boolean executePower(String power, Player target, String[] args) {
        return switch (power.toLowerCase()) {
            // Wrath
            case "smite"            -> { smite(target); yield true; }
            case "fireball"         -> { fireball(target, intArg(args, 0, 3)); yield true; }
            case "firestorm"        -> { firestorm(target); yield true; }
            case "tntbomb"          -> { tntBomb(target, intArg(args, 0, 5), intArg(args, 1, 3)); yield true; }
            case "arrowrain"        -> { arrowRain(target, intArg(args, 0, 5), intArg(args, 1, 30)); yield true; }
            case "nuke"             -> { nuke(target, intArg(args, 0, 10)); yield true; }
            case "meteor"           -> { meteorStrike(target, intArg(args, 0, 5)); yield true; }
            case "bombardment"      -> { bombardment(target, intArg(args, 0, 5), intArg(args, 1, 10)); yield true; }
            case "witherstorm"      -> { witherStorm(target, intArg(args, 0, 2)); yield true; }
            case "creeperswarm"     -> { creeperSwarm(target, intArg(args, 0, 8)); yield true; }
            case "lavaflood"        -> { lavaFlood(target, intArg(args, 0, 5)); yield true; }
            case "lightningstorm"   -> { lightningStorm(target, intArg(args, 0, 5), intArg(args, 1, 5)); yield true; }
            case "encase"           -> { encase(target, stringArg(args, 0, "obsidian")); yield true; }
            case "cage"             -> { cage(target); yield true; }
            case "prison"           -> { prison(target); yield true; }
            case "launch"           -> { launch(target, intArg(args, 0, 50)); yield true; }
            case "freeze"           -> { freeze(target); yield true; }
            case "burn"             -> { burn(target, intArg(args, 0, 10)); yield true; }
            case "tornado"          -> { tornado(target); yield true; }
            case "anvil"            -> { anvilRain(target, intArg(args, 0, 20)); yield true; }
            case "void"             -> { voidTrap(target); yield true; }
            case "explode"          -> { explode(target, intArg(args, 0, 4)); yield true; }
            case "earthquake"       -> { earthquake(target, intArg(args, 0, 10)); yield true; }
            case "airstrike"        -> { airstrike(target); yield true; }
            // Blessings
            case "bless"            -> { bless(target); yield true; }
            case "curse"            -> { curse(target); yield true; }
            case "godset"           -> { godSet(target); yield true; }
            case "kit"              -> { giveKit(target, stringArg(args, 0, "starter")); yield true; }
            case "feast"            -> { feast(target); yield true; }
            case "treasure"         -> { treasure(target); yield true; }
            case "heal"             -> { heal(target); yield true; }
            case "fullheal"         -> { fullHeal(target); yield true; }
            case "shield"           -> { shield(target); yield true; }
            case "superspeed"       -> { superSpeed(target, intArg(args, 0, 30)); yield true; }
            // Mobs
            case "spawn"            -> { spawnMob(stringArg(args, 0, "zombie"), target, intArg(args, 1, 1)); yield true; }
            case "army"             -> { spawnArmy(target, stringArg(args, 0, "zombie")); yield true; }
            case "boss"             -> { spawnBoss(target); yield true; }
            case "rain"             -> { itemRain(stringArg(args, 0, "diamond"), target, intArg(args, 1, 10)); yield true; }
            default                 -> false;
        };
    }

    // ════════════════════════════════════════════════════════════
    //  WRATH POWERS
    // ════════════════════════════════════════════════════════════

    private void smite(Player target) {
        target.getWorld().strikeLightning(target.getLocation());
        broadcastPower(target.getName() + " has been smitten by MineAI!");
    }

    private void fireball(Player target, int count) {
        count = clamp(count, 1, 20);
        Location loc = target.getLocation().add(0, 15, 0);
        for (int i = 0; i < count; i++) {
            final int delay = i * 5;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location spawn = loc.clone().add(RANDOM.nextInt(7) - 3, 0, RANDOM.nextInt(7) - 3);
                target.getWorld().spawn(spawn, Fireball.class, fb -> {
                    fb.setDirection(new Vector(0, -1, 0));
                    fb.setYield(2.0f);
                });
            }, delay);
        }
        broadcastPower(target.getName() + " is under fireball barrage!");
    }

    private void firestorm(Player target) {
        Location center = target.getLocation();
        int radius = 8;
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 40) { cancel(); return; }
                for (int i = 0; i < 3; i++) {
                    double x = center.getX() + RANDOM.nextInt(radius * 2) - radius;
                    double z = center.getZ() + RANDOM.nextInt(radius * 2) - radius;
                    double y = center.getWorld().getHighestBlockYAt((int) x, (int) z) + 1;
                    Location loc = new Location(center.getWorld(), x, y, z);
                    center.getWorld().strikeLightning(loc);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
        broadcastPower("A firestorm engulfs " + target.getName() + "!");
    }

    private void tntBomb(Player target, int radius, int density) {
        radius = clamp(radius, 1, 15);
        density = clamp(density, 1, 10);
        Location center = target.getLocation().add(0, 20, 0);
        for (int x = -radius; x <= radius; x += density) {
            for (int z = -radius; z <= radius; z += density) {
                Location spawn = center.clone().add(x, RANDOM.nextInt(5), z);
                target.getWorld().spawn(spawn, TNTPrimed.class, tnt -> tnt.setFuseTicks(40 + RANDOM.nextInt(40)));
            }
        }
        broadcastPower(target.getName() + " is being carpet-bombed with TNT!");
    }

    private void arrowRain(Player target, int radius, int count) {
        radius = clamp(radius, 1, 15);
        count = clamp(count, 1, 100);
        Location center = target.getLocation().add(0, 25, 0);
        for (int i = 0; i < count; i++) {
            Location spawn = center.clone().add(
                    RANDOM.nextInt(radius * 2) - radius, RANDOM.nextInt(5),
                    RANDOM.nextInt(radius * 2) - radius);
            target.getWorld().spawn(spawn, Arrow.class, arrow -> {
                arrow.setVelocity(new Vector(0, -2, 0));
                arrow.setDamage(4.0);
            });
        }
        broadcastPower("An arrow storm rains down on " + target.getName() + "!");
    }

    private void nuke(Player target, int power) {
        power = clamp(power, 1, 50);
        target.getWorld().createExplosion(target.getLocation(), power, true, true);
        broadcastPower("☢ " + target.getName() + " has been NUKED!");
    }

    private void meteorStrike(Player target, int count) {
        count = clamp(count, 1, 10);
        for (int i = 0; i < count; i++) {
            final int delay = i * 15;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = target.getLocation().add(
                        RANDOM.nextInt(10) - 5, 40, RANDOM.nextInt(10) - 5);
                target.getWorld().spawn(loc, Fireball.class, fb -> {
                    fb.setDirection(new Vector(
                            RANDOM.nextDouble() * 0.4 - 0.2, -1,
                            RANDOM.nextDouble() * 0.4 - 0.2));
                    fb.setYield(4.0f);
                });
            }, delay);
        }
        broadcastPower("☄ Meteors are falling on " + target.getName() + "!");
    }

    private void bombardment(Player target, int radius, int count) {
        radius = clamp(radius, 1, 15);
        count = clamp(count, 1, 30);
        Location center = target.getLocation().add(0, 30, 0);
        for (int i = 0; i < count; i++) {
            final int delay = i * 3;
            final int r = radius;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location spawn = center.clone().add(
                        RANDOM.nextInt(r * 2) - r, RANDOM.nextInt(5),
                        RANDOM.nextInt(r * 2) - r);
                target.getWorld().spawn(spawn, Fireball.class, fb -> {
                    fb.setDirection(new Vector(0, -1.5, 0));
                    fb.setYield(2.0f);
                });
            }, delay);
        }
        broadcastPower(target.getName() + " is being bombarded!");
    }

    private void witherStorm(Player target, int count) {
        count = clamp(count, 1, 5);
        for (int i = 0; i < count; i++) {
            Location spawn = target.getLocation().add(
                    RANDOM.nextInt(10) - 5, 10, RANDOM.nextInt(10) - 5);
            target.getWorld().spawn(spawn, Wither.class);
        }
        broadcastPower("💀 Withers have been unleashed upon " + target.getName() + "!");
    }

    private void creeperSwarm(Player target, int count) {
        count = clamp(count, 1, 20);
        for (int i = 0; i < count; i++) {
            Location spawn = target.getLocation().add(
                    RANDOM.nextInt(6) - 3, 0, RANDOM.nextInt(6) - 3);
            target.getWorld().spawn(spawn, Creeper.class, creeper -> {
                creeper.setPowered(true);
                creeper.setMaxFuseTicks(30);
            });
        }
        broadcastPower("Charged creepers swarm " + target.getName() + "! 💣");
    }

    private void lavaFlood(Player target, int radius) {
        radius = clamp(radius, 1, 10);
        Location center = target.getLocation();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Block block = center.getWorld().getHighestBlockAt(
                        center.getBlockX() + x, center.getBlockZ() + z);
                block.getRelative(0, 1, 0).setType(Material.LAVA);
            }
        }
        broadcastPower("🌋 Lava floods around " + target.getName() + "!");
    }

    private void lightningStorm(Player target, int radius, int durationSeconds) {
        radius = clamp(radius, 1, 15);
        durationSeconds = clamp(durationSeconds, 1, 30);
        final int r = radius;
        Location center = target.getLocation();
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = durationSeconds * 4; // runs every 5 ticks
            @Override
            public void run() {
                if (ticks++ > maxTicks) { cancel(); return; }
                double x = center.getX() + RANDOM.nextInt(r * 2) - r;
                double z = center.getZ() + RANDOM.nextInt(r * 2) - r;
                double y = center.getWorld().getHighestBlockYAt((int) x, (int) z);
                center.getWorld().strikeLightning(new Location(center.getWorld(), x, y, z));
            }
        }.runTaskTimer(plugin, 0L, 5L);
        broadcastPower("⚡ A lightning storm rages around " + target.getName() + "!");
    }

    private void encase(Player target, String materialName) {
        Material mat = switch (materialName.toLowerCase()) {
            case "lava" -> Material.LAVA;
            case "obsidian" -> Material.OBSIDIAN;
            case "tnt" -> Material.TNT;
            case "ice" -> Material.ICE;
            case "bedrock" -> Material.BEDROCK;
            default -> Material.OBSIDIAN;
        };
        Location loc = target.getLocation();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0 && (y == 0 || y == 1)) continue; // keep player space
                    loc.getWorld().getBlockAt(
                            loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z
                    ).setType(mat);
                }
            }
        }
        broadcastPower(target.getName() + " has been encased in " + materialName + "!");
    }

    private void cage(Player target) {
        Location loc = target.getLocation();
        Material bars = Material.IRON_BARS;
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 3;
                    if (edge) {
                        loc.getWorld().getBlockAt(
                                loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z
                        ).setType(bars);
                    }
                }
            }
        }
        broadcastPower(target.getName() + " has been caged!");
    }

    private void prison(Player target) {
        Location loc = target.getLocation();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 4; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2 || y == -1 || y == 4;
                    if (wall) {
                        loc.getWorld().getBlockAt(
                                loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z
                        ).setType(Material.OBSIDIAN);
                    }
                }
            }
        }
        broadcastPower(target.getName() + " has been imprisoned in obsidian!");
    }

    private void launch(Player target, int height) {
        height = clamp(height, 1, 200);
        target.setVelocity(new Vector(0, height * 0.5, 0));
        broadcastPower(target.getName() + " has been launched into the sky! 🚀");
    }

    private void freeze(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 255, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 255, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 200, 128, false, true));
        broadcastPower(target.getName() + " has been frozen solid! ❄");
    }

    private void burn(Player target, int seconds) {
        seconds = clamp(seconds, 1, 60);
        target.setFireTicks(seconds * 20);
        broadcastPower(target.getName() + " is burning! 🔥");
    }

    private void tornado(Player target) {
        new BukkitRunnable() {
            int ticks = 0;
            double angle = 0;
            @Override
            public void run() {
                if (ticks++ > 60 || !target.isOnline()) { cancel(); return; }
                angle += 0.5;
                double radius = 2;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                target.setVelocity(new Vector(x * 0.3, 0.5, z * 0.3));
                target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
            }
        }.runTaskTimer(plugin, 0L, 2L);
        broadcastPower("🌪 " + target.getName() + " is caught in a tornado!");
    }

    private void anvilRain(Player target, int count) {
        count = clamp(count, 1, 50);
        for (int i = 0; i < count; i++) {
            final int delay = i * 3;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location spawn = target.getLocation().add(
                        RANDOM.nextInt(8) - 4, 20 + RANDOM.nextInt(10),
                        RANDOM.nextInt(8) - 4);
                target.getWorld().spawn(spawn, FallingBlock.class, fb -> {
                    // FallingBlock needs the block data set via spawn
                });
                // Alternative: spawn a falling anvil entity
                target.getWorld().spawnFallingBlock(spawn,
                        Material.ANVIL.createBlockData());
            }, delay);
        }
        broadcastPower("Anvils rain down on " + target.getName() + "! 🔨");
    }

    private void voidTrap(Player target) {
        Location loc = target.getLocation();
        int radius = 3;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = 0; y >= -5; y--) {
                    loc.getWorld().getBlockAt(
                            loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z
                    ).setType(Material.AIR);
                }
            }
        }
        broadcastPower(target.getName() + " plummets into the void! ⬛");
    }

    private void explode(Player target, int power) {
        power = clamp(power, 1, 20);
        target.getWorld().createExplosion(target.getLocation(), power, true, true);
        broadcastPower(target.getName() + " has been EXPLODED! 💥");
    }

    private void earthquake(Player target, int radius) {
        radius = clamp(radius, 1, 20);
        final int r = radius;
        Location center = target.getLocation();
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 20) { cancel(); return; }
                for (int i = 0; i < 5; i++) {
                    int x = RANDOM.nextInt(r * 2) - r;
                    int z = RANDOM.nextInt(r * 2) - r;
                    Block top = center.getWorld().getHighestBlockAt(
                            center.getBlockX() + x, center.getBlockZ() + z);
                    center.getWorld().createExplosion(top.getLocation(), 2, false, true);
                }
                // Shake effect via velocity
                for (Player nearby : center.getWorld().getPlayers()) {
                    if (nearby.getLocation().distance(center) < r) {
                        nearby.setVelocity(new Vector(
                                RANDOM.nextDouble() * 0.4 - 0.2,
                                RANDOM.nextDouble() * 0.3,
                                RANDOM.nextDouble() * 0.4 - 0.2));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
        broadcastPower("🌍 An earthquake tears the ground around " + target.getName() + "!");
    }

    private void airstrike(Player target) {
        Location center = target.getLocation().add(0, 30, 0);
        for (int i = 0; i < 15; i++) {
            final int delay = i * 3;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location spawn = center.clone().add(
                        RANDOM.nextInt(10) - 5, RANDOM.nextInt(5),
                        RANDOM.nextInt(10) - 5);
                target.getWorld().spawn(spawn, Fireball.class, fb -> {
                    fb.setDirection(new Vector(
                            RANDOM.nextDouble() * 0.3 - 0.15, -1.5,
                            RANDOM.nextDouble() * 0.3 - 0.15));
                    fb.setYield(3.0f);
                });
            }, delay);
        }
        broadcastPower("✈ Airstrike incoming on " + target.getName() + "!");
    }

    // ════════════════════════════════════════════════════════════
    //  BLESSINGS
    // ════════════════════════════════════════════════════════════

    private void bless(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 600, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 200, 0));
        target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target.getLocation().add(0, 1, 0), 50, 1, 1, 1, 0.1);
        broadcastPower(target.getName() + " has been blessed by MineAI! ✨");
    }

    private void curse(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 400, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 600, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 600, 2));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 400, 0));
        target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0), 50, 1, 1, 1, 0.1);
        broadcastPower(target.getName() + " has been cursed by MineAI! 💀");
    }

    private void godSet(Player target) {
        ItemStack helmet = enchantedItem(Material.NETHERITE_HELMET, "Divine Crown");
        ItemStack chest = enchantedItem(Material.NETHERITE_CHESTPLATE, "Divine Plate");
        ItemStack legs = enchantedItem(Material.NETHERITE_LEGGINGS, "Divine Greaves");
        ItemStack boots = enchantedItem(Material.NETHERITE_BOOTS, "Divine Treads");
        ItemStack sword = enchantedItem(Material.NETHERITE_SWORD, "Divine Blade");

        target.getInventory().setHelmet(helmet);
        target.getInventory().setChestplate(chest);
        target.getInventory().setLeggings(legs);
        target.getInventory().setBoots(boots);
        target.getInventory().addItem(sword);

        target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target.getLocation().add(0, 1, 0), 100, 1, 2, 1, 0.3);
        broadcastPower(target.getName() + " has received the God Set! ⚔️");
    }

    private void giveKit(Player target, String kitName) {
        switch (kitName.toLowerCase()) {
            case "starter" -> {
                target.getInventory().addItem(
                        new ItemStack(Material.IRON_SWORD),
                        new ItemStack(Material.IRON_PICKAXE),
                        new ItemStack(Material.BREAD, 32),
                        new ItemStack(Material.TORCH, 16)
                );
                target.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            }
            case "warrior" -> {
                target.getInventory().addItem(
                        new ItemStack(Material.DIAMOND_SWORD),
                        new ItemStack(Material.SHIELD),
                        new ItemStack(Material.GOLDEN_APPLE, 8)
                );
                target.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                target.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                target.getInventory().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
                target.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
            }
            case "mage" -> {
                target.getInventory().addItem(
                        new ItemStack(Material.TRIDENT),
                        new ItemStack(Material.ENDER_PEARL, 16),
                        new ItemStack(Material.GOLDEN_APPLE, 8),
                        new ItemStack(Material.EXPERIENCE_BOTTLE, 64)
                );
                target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 6000, 1));
            }
            case "archer" -> {
                target.getInventory().addItem(
                        new ItemStack(Material.BOW),
                        new ItemStack(Material.ARROW, 128),
                        new ItemStack(Material.SPECTRAL_ARROW, 32),
                        new ItemStack(Material.GOLDEN_APPLE, 4)
                );
                target.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
                target.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
            }
            case "tank" -> {
                target.getInventory().addItem(
                        new ItemStack(Material.NETHERITE_SWORD),
                        new ItemStack(Material.SHIELD),
                        new ItemStack(Material.GOLDEN_APPLE, 16),
                        new ItemStack(Material.TOTEM_OF_UNDYING)
                );
                target.getInventory().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                target.getInventory().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                target.getInventory().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                target.getInventory().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 6000, 1));
            }
            case "god" -> {
                godSet(target);
                bless(target);
                target.getInventory().addItem(
                        new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 16),
                        new ItemStack(Material.TOTEM_OF_UNDYING, 3),
                        new ItemStack(Material.ELYTRA)
                );
                return; // godSet already broadcasts
            }
            default -> {
                broadcastPower(target.getName() + " requested an unknown kit: " + kitName);
                return;
            }
        }
        broadcastPower(target.getName() + " has received the " + kitName + " kit! 🎁");
    }

    private void feast(Player target) {
        target.getInventory().addItem(
                new ItemStack(Material.COOKED_BEEF, 64),
                new ItemStack(Material.GOLDEN_CARROT, 32),
                new ItemStack(Material.CAKE),
                new ItemStack(Material.PUMPKIN_PIE, 16),
                new ItemStack(Material.GOLDEN_APPLE, 8)
        );
        target.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 600, 2));
        broadcastPower(target.getName() + " has been granted a divine feast! 🍖");
    }

    private void treasure(Player target) {
        target.getInventory().addItem(
                new ItemStack(Material.DIAMOND, 32),
                new ItemStack(Material.EMERALD, 64),
                new ItemStack(Material.NETHERITE_INGOT, 8),
                new ItemStack(Material.GOLDEN_APPLE, 16),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4),
                new ItemStack(Material.TOTEM_OF_UNDYING)
        );
        target.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target.getLocation().add(0, 1, 0), 80, 1, 2, 1, 0.2);
        broadcastPower(target.getName() + " has received MineAI's treasure! 💎");
    }

    private void heal(Player target) {
        target.setHealth(Math.min(target.getHealth() + 10, target.getMaxHealth()));
        target.setFoodLevel(20);
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        broadcastPower(target.getName() + " has been healed! ❤");
    }

    private void fullHeal(Player target) {
        target.setHealth(target.getMaxHealth());
        target.setFoodLevel(20);
        target.setSaturation(20f);
        target.setFireTicks(0);
        target.getActivePotionEffects().forEach(e -> target.removePotionEffect(e.getType()));
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2));
        target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 3));
        broadcastPower(target.getName() + " has been fully healed and restored! 💖");
    }

    private void shield(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 1200, 3));
        target.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 1200, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1200, 4));
        target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.05);
        broadcastPower(target.getName() + " is shielded by divine protection! 🛡");
    }

    private void superSpeed(Player target, int seconds) {
        seconds = clamp(seconds, 1, 120);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 4));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, seconds * 20, 2));
        broadcastPower(target.getName() + " has been granted super speed! 💨");
    }

    // ════════════════════════════════════════════════════════════
    //  MOB POWERS
    // ════════════════════════════════════════════════════════════

    private void spawnMob(String entityName, Player target, int count) {
        count = clamp(count, 1, 50);
        EntityType type;
        try {
            type = EntityType.valueOf(entityName.toUpperCase());
        } catch (IllegalArgumentException e) {
            broadcastPower("Unknown entity: " + entityName);
            return;
        }
        if (!type.isSpawnable()) {
            broadcastPower("Cannot spawn: " + entityName);
            return;
        }
        for (int i = 0; i < count; i++) {
            Location spawn = target.getLocation().add(
                    RANDOM.nextInt(6) - 3, 0, RANDOM.nextInt(6) - 3);
            target.getWorld().spawnEntity(spawn, type);
        }
        broadcastPower(count + "x " + entityName + " spawned near " + target.getName() + "!");
    }

    private void spawnArmy(Player target, String type) {
        EntityType entityType = switch (type.toLowerCase()) {
            case "skeleton" -> EntityType.SKELETON;
            case "creeper" -> EntityType.CREEPER;
            case "wither_skeleton" -> EntityType.WITHER_SKELETON;
            case "piglin" -> EntityType.PIGLIN_BRUTE;
            default -> EntityType.ZOMBIE;
        };
        int count = 15;
        for (int i = 0; i < count; i++) {
            Location spawn = target.getLocation().add(
                    RANDOM.nextInt(10) - 5, 0, RANDOM.nextInt(10) - 5);
            target.getWorld().spawnEntity(spawn, entityType);
        }
        broadcastPower("An army of " + type + " marches toward " + target.getName() + "! ⚔");
    }

    private void spawnBoss(Player target) {
        // Spawn a wither as the "boss"
        Location spawn = target.getLocation().add(0, 5, 0);
        Wither wither = (Wither) target.getWorld().spawnEntity(spawn, EntityType.WITHER);
        wither.customName(Component.text("MineAI's Champion")
                .color(NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD));
        wither.setCustomNameVisible(true);

        // Also add some minions
        for (int i = 0; i < 5; i++) {
            Location minLoc = target.getLocation().add(
                    RANDOM.nextInt(8) - 4, 0, RANDOM.nextInt(8) - 4);
            target.getWorld().spawnEntity(minLoc, EntityType.WITHER_SKELETON);
        }
        broadcastPower("☠ MineAI's Champion has been summoned near " + target.getName() + "!");
    }

    private void itemRain(String materialName, Player target, int count) {
        count = clamp(count, 1, 64);
        Material mat;
        try {
            mat = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            broadcastPower("Unknown material: " + materialName);
            return;
        }
        for (int i = 0; i < count; i++) {
            Location spawn = target.getLocation().add(
                    RANDOM.nextInt(8) - 4, 10 + RANDOM.nextInt(5),
                    RANDOM.nextInt(8) - 4);
            target.getWorld().dropItem(spawn, new ItemStack(mat));
        }
        broadcastPower(materialName + " rains from the sky near " + target.getName() + "! 🌧");
    }

    // ════════════════════════════════════════════════════════════
    //  SOCIAL POWERS
    // ════════════════════════════════════════════════════════════

    public void executeSay(String message) {
        Component msg = AI_PREFIX.append(
                Component.text(message)
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false)
        );
        Bukkit.broadcast(msg);
    }

    public void executeAnnounce(String message) {
        // Chat message
        Component chatMsg = Component.text("══════════════════════════════").color(NamedTextColor.GOLD)
                .appendNewline()
                .append(AI_PREFIX)
                .append(Component.text(message).color(NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .appendNewline()
                .append(Component.text("══════════════════════════════").color(NamedTextColor.GOLD));
        Bukkit.broadcast(chatMsg);

        // Title for all players
        Title title = Title.title(
                Component.text("⚡ MineAI ⚡").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
                Component.text(message).color(NamedTextColor.GOLD),
                Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofSeconds(3),
                        Duration.ofMillis(500)
                )
        );
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
        }
    }

    public void executeSetRank(CommandSender sender, String playerName, String rankName) {
        Rank rank = Rank.fromString(rankName);
        boolean success = plugin.getRankManager().setRank(playerName, rank);
        if (success) {
            broadcastPower(playerName + " has been ranked to " + rank.displayName() + "!");
        } else {
            sender.sendMessage(Component.text("Player '" + playerName + "' not found.")
                    .color(NamedTextColor.RED));
        }
    }

    public void executeShowRanks(CommandSender sender) {
        // Delegate to the ranks command logic
        sender.sendMessage(Component.text("⚡ MineAI Rank Hierarchy ⚡")
                .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        for (Rank rank : plugin.getRankManager().getAllRanks()) {
            sender.sendMessage(plugin.getRankManager().getRankDisplayComponent(rank));
        }
    }

    // ════════════════════════════════════════════════════════════
    //  UTILITIES
    // ════════════════════════════════════════════════════════════

    private void broadcastPower(String message) {
        Component msg = AI_PREFIX.append(
                Component.text(message)
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, false)
        );
        Bukkit.broadcast(msg);
    }

    private ItemStack enchantedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name)
                    .color(NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static int intArg(String[] args, int index, int defaultValue) {
        if (index >= args.length) return defaultValue;
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String stringArg(String[] args, int index, String defaultValue) {
        return index < args.length ? args[index] : defaultValue;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
