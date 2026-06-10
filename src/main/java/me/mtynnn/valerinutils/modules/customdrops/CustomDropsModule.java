package me.mtynnn.valerinutils.modules.customdrops;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.BaseModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class CustomDropsModule extends BaseModule implements Listener, CommandExecutor, TabCompleter {

    private final Map<EntityType, DropRule> rulesByType = new HashMap<>();
    private final Map<String, DropRule> mythicRulesByType = new HashMap<>();
    private final Map<String, Template> templates = new HashMap<>();
    private NexoBridge nexoBridge;
    private MythicBridge mythicBridge;
    private double defaultNearbyRadius;
    private int defaultNearbyMax;
    private int soundRadius;
    private boolean ignoreMythicForVanillaDrops;

    public CustomDropsModule(ValerinUtils plugin) {
        super(plugin);
    }

    @Override
    public String getId() {
        return "customdrops";
    }

    @Override
    protected void onEnableModule() {
        if (!isEnabledInConfig()) {
            return;
        }

        this.nexoBridge = NexoBridge.tryCreate();
        if (nexoBridge == null) {
            throw new IllegalStateException("Nexo API no disponible");
        }
        this.mythicBridge = MythicBridge.tryCreate();

        loadRules();
        registerListener(this);
        registerCommand("customdrops", this, this);
        plugin.getLogger().info("[CustomDrops] Modulo habilitado con " + rulesByType.size()
                + " drops vanilla y " + mythicRulesByType.size() + " drops mythic.");
    }

    @Override
    protected void onDisableModule() {
        rulesByType.clear();
        mythicRulesByType.clear();
        templates.clear();
        nexoBridge = null;
        mythicBridge = null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (rulesByType.isEmpty()) {
            return;
        }

        LivingEntity dead = event.getEntity();
        String mythicType = mythicBridge == null ? null : mythicBridge.getMythicType(dead);
        DropRule rule = null;
        if (mythicType != null) {
            rule = mythicRulesByType.get(mythicType.toLowerCase(Locale.ROOT));
            if (rule == null && ignoreMythicForVanillaDrops) {
                return;
            }
        }
        if (rule == null) {
            rule = rulesByType.get(dead.getType());
        }
        if (rule == null) {
            return;
        }

        Player killer = dead.getKiller();
        if (rule.killerRequired && killer == null) {
            return;
        }

        if (killer == null) {
            return;
        }

        if (!passesNearbyLimit(dead, rule)) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble(100.0d) >= rule.chancePercent) {
            return;
        }

        grantDrop(dead, killer, rule, mythicType);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("valerinutils.admin")) {
            sender.sendMessage(comp("<red>No tienes permiso."));
            return true;
        }

        if (args.length < 2 || !"test".equalsIgnoreCase(args[0])) {
            sender.sendMessage(comp("<yellow>Uso: /customdrops test <entity|mythic> <tipo> [jugador]"));
            return true;
        }

        boolean mythicTest = "mythic".equalsIgnoreCase(args[1]);
        EntityType type;
        DropRule rule;
        String mythicType = null;
        if (mythicTest) {
            if (args.length < 3) {
                sender.sendMessage(comp("<yellow>Uso: /customdrops test mythic <mobType> [jugador]"));
                return true;
            }
            mythicType = args[2];
            rule = mythicRulesByType.get(mythicType.toLowerCase(Locale.ROOT));
            if (rule == null) {
                sender.sendMessage(comp("<red>No hay drop mythic configurado para: <white>" + mythicType));
                return true;
            }
            type = null;
        } else {
            try {
                type = EntityType.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (Exception ex) {
                sender.sendMessage(comp("<red>Entity invalida: <white>" + args[1]));
                return true;
            }
            rule = rulesByType.get(type);
            if (rule == null) {
                sender.sendMessage(comp("<red>No hay drop configurado para: <white>" + type.name()));
                return true;
            }
        }

        Player target;
        int playerArgIndex = mythicTest ? 3 : 2;
        if (args.length > playerArgIndex) {
            target = Bukkit.getPlayerExact(args[playerArgIndex]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(comp("<red>Desde consola debes indicar jugador."));
            return true;
        }

        if (target == null || !target.isOnline()) {
            sender.sendMessage(comp("<red>Jugador no encontrado."));
            return true;
        }

        grantDrop(null, target, rule, mythicType);
        String kind = mythicTest ? ("mythic:" + mythicType) : type.name();
        sender.sendMessage(comp("<green>Test ejecutado para <white>" + target.getName() + "<green> con <white>" + kind));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("valerinutils.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return "test".startsWith(args[0].toLowerCase(Locale.ROOT))
                    ? List.of("test")
                    : Collections.emptyList();
        }
        if (args.length == 2) {
            String q = args[1].toUpperCase(Locale.ROOT);
            if ("MYTHIC".startsWith(q)) {
                return List.of("mythic");
            }
            return rulesByType.keySet().stream()
                    .map(Enum::name)
                    .filter(n -> n.startsWith(q))
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            if ("mythic".equalsIgnoreCase(args[1])) {
                String q = args[2].toLowerCase(Locale.ROOT);
                return mythicRulesByType.keySet().stream()
                        .filter(n -> n.startsWith(q))
                        .sorted()
                        .collect(Collectors.toList());
            }
            String q = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(q))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void grantDrop(LivingEntity dead, Player killer, DropRule rule, String mythicType) {
        ItemStack item = nexoBridge.buildItem(rule.itemId, 1);
        if (item == null) {
            throw new IllegalStateException("No se pudo construir item Nexo: " + rule.itemId);
        }

        HashMap<Integer, ItemStack> leftovers = killer.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            Location loc = killer.getLocation();
            for (ItemStack left : leftovers.values()) {
                killer.getWorld().dropItemNaturally(loc, left);
            }
        }

        if (rule.broadcastEnabled) {
            String msg = applyPlaceholders(rule.broadcastMessage, killer, dead, rule, mythicType);
            if (!msg.isBlank()) {
                Bukkit.broadcast(comp(msg));
            }
        }

        playSoundSequence(rule.soundSequence, killer.getLocation(), killer);
    }

    private boolean passesNearbyLimit(LivingEntity dead, DropRule rule) {
        double radius = rule.nearbyRadius > 0 ? rule.nearbyRadius : defaultNearbyRadius;
        int max = rule.nearbyMax > 0 ? rule.nearbyMax : defaultNearbyMax;
        if (radius <= 0 || max <= 0) {
            return true;
        }

        Collection<LivingEntity> nearby = dead.getWorld().getNearbyLivingEntities(
                dead.getLocation(), radius,
                living -> living != dead && living.getType() == dead.getType());
        return nearby.size() < max;
    }

    private void playSoundSequence(List<SoundStep> sequence, Location center, Player winner) {
        if (sequence.isEmpty()) {
            return;
        }

        Collection<Player> nearbyPlayers = center.getWorld().getNearbyPlayers(center, soundRadius);
        if (!nearbyPlayers.contains(winner)) {
            List<Player> withWinner = new ArrayList<>(nearbyPlayers.size() + 1);
            withWinner.addAll(nearbyPlayers);
            withWinner.add(winner);
            nearbyPlayers = withWinner;
        }
        final Collection<Player> recipients = nearbyPlayers;

        for (SoundStep step : sequence) {
            Runnable playTask = () -> {
                Sound sound = step.sound;
                for (Player p : recipients) {
                    p.playSound(p.getLocation(), sound, step.volume, step.pitch);
                }
            };
            if (step.delayTicks <= 0L) {
                playTask.run();
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, playTask, step.delayTicks);
            }
        }
    }

    private String applyPlaceholders(String text, Player killer, LivingEntity dead, DropRule rule, String mythicType) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Location l = dead != null ? dead.getLocation() : killer.getLocation();
        String entityName = dead != null ? dead.getType().name() : "TEST";
        return text
                .replace("%player%", killer.getName())
                .replace("%entity%", entityName)
                .replace("%item%", rule.itemId)
                .replace("%display%", rule.displayName)
                .replace("%number%", String.valueOf(rule.number))
                .replace("%mythic_type%", mythicType == null ? "" : mythicType)
                .replace("%world%", l.getWorld() == null ? "world" : l.getWorld().getName())
                .replace("%x%", String.valueOf(l.getBlockX()))
                .replace("%y%", String.valueOf(l.getBlockY()))
                .replace("%z%", String.valueOf(l.getBlockZ()));
    }

    private void loadRules() {
        rulesByType.clear();
        templates.clear();

        var cfg = cfg();
        if (cfg == null) {
            return;
        }

        defaultNearbyRadius = Math.max(0.0d, cfg.getDouble("defaults.anti-farm.radius", 16.0d));
        defaultNearbyMax = Math.max(0, cfg.getInt("defaults.anti-farm.max-same-type", 6));
        soundRadius = Math.max(1, cfg.getInt("defaults.sound.radius", 16));
        ignoreMythicForVanillaDrops = cfg.getBoolean("defaults.mythic.ignore-mythic-for-vanilla-drops", true);

        var templatesSec = cfg.getConfigurationSection("templates");
        if (templatesSec != null) {
            for (String key : templatesSec.getKeys(false)) {
                var sec = templatesSec.getConfigurationSection(key);
                if (sec == null) {
                    continue;
                }
                Template t = new Template(
                        Math.max(0.0d, sec.getDouble("chance-percent", 0.1d)),
                        sec.getBoolean("killer-required", true),
                        sec.getBoolean("broadcast-enabled", true),
                        sec.getString("broadcast", ""),
                        parseSoundSequence(sec.getConfigurationSection("sound-sequence")));
                templates.put(key.toLowerCase(Locale.ROOT), t);
            }
        }

        var dropsSec = cfg.getConfigurationSection("drops");
        if (dropsSec == null) {
            return;
        }

        for (String typeKey : dropsSec.getKeys(false)) {
            EntityType type;
            try {
                type = EntityType.valueOf(typeKey.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                plugin.getLogger().warning("[CustomDrops] EntityType invalido: " + typeKey);
                continue;
            }

            var sec = dropsSec.getConfigurationSection(typeKey);
            if (sec == null) {
                continue;
            }

            String templateId = sec.getString("template", "").toLowerCase(Locale.ROOT);
            Template template = templates.get(templateId);
            if (template == null) {
                plugin.getLogger().warning("[CustomDrops] Template no encontrado para " + typeKey + ": " + templateId);
                continue;
            }

            String itemId = sec.getString("item-id", "").trim();
            if (itemId.isEmpty()) {
                plugin.getLogger().warning("[CustomDrops] item-id vacio para " + typeKey);
                continue;
            }

            String display = sec.getString("display-name", itemId);
            int number = Math.max(1, sec.getInt("number", 0));

            double chance = sec.contains("chance-percent")
                    ? Math.max(0.0d, sec.getDouble("chance-percent", template.chancePercent))
                    : template.chancePercent;

            boolean killerRequired = sec.contains("killer-required")
                    ? sec.getBoolean("killer-required", template.killerRequired)
                    : template.killerRequired;

            boolean broadcastEnabled = sec.contains("broadcast-enabled")
                    ? sec.getBoolean("broadcast-enabled", template.broadcastEnabled)
                    : template.broadcastEnabled;

            String broadcast = sec.contains("broadcast")
                    ? sec.getString("broadcast", template.broadcastMessage)
                    : template.broadcastMessage;

            double nearbyRadius = sec.getDouble("anti-farm.radius", defaultNearbyRadius);
            int nearbyMax = sec.getInt("anti-farm.max-same-type", defaultNearbyMax);

            List<SoundStep> soundSeq = template.soundSequence;
            var seqSec = sec.getConfigurationSection("sound-sequence");
            if (seqSec != null) {
                soundSeq = parseSoundSequence(seqSec);
            }

            rulesByType.put(type, new DropRule(itemId, display, number, chance, killerRequired,
                    broadcastEnabled, broadcast == null ? "" : broadcast, nearbyRadius, nearbyMax, soundSeq));
        }

        var mythicDropsSec = cfg.getConfigurationSection("mythic-drops");
        if (mythicDropsSec == null) {
            return;
        }

        for (String mythicType : mythicDropsSec.getKeys(false)) {
            var sec = mythicDropsSec.getConfigurationSection(mythicType);
            if (sec == null) {
                continue;
            }

            String templateId = sec.getString("template", "").toLowerCase(Locale.ROOT);
            Template template = templates.get(templateId);
            if (template == null) {
                plugin.getLogger().warning("[CustomDrops] Template no encontrado para mythic " + mythicType + ": " + templateId);
                continue;
            }

            String itemId = sec.getString("item-id", "").trim();
            if (itemId.isEmpty()) {
                plugin.getLogger().warning("[CustomDrops] item-id vacio para mythic " + mythicType);
                continue;
            }

            String display = sec.getString("display-name", itemId);
            int number = Math.max(1, sec.getInt("number", 0));

            double chance = sec.contains("chance-percent")
                    ? Math.max(0.0d, sec.getDouble("chance-percent", template.chancePercent))
                    : template.chancePercent;
            boolean killerRequired = sec.contains("killer-required")
                    ? sec.getBoolean("killer-required", template.killerRequired)
                    : template.killerRequired;
            boolean broadcastEnabled = sec.contains("broadcast-enabled")
                    ? sec.getBoolean("broadcast-enabled", template.broadcastEnabled)
                    : template.broadcastEnabled;
            String broadcast = sec.contains("broadcast")
                    ? sec.getString("broadcast", template.broadcastMessage)
                    : template.broadcastMessage;
            double nearbyRadius = sec.getDouble("anti-farm.radius", defaultNearbyRadius);
            int nearbyMax = sec.getInt("anti-farm.max-same-type", defaultNearbyMax);
            List<SoundStep> soundSeq = template.soundSequence;
            var seqSec = sec.getConfigurationSection("sound-sequence");
            if (seqSec != null) {
                soundSeq = parseSoundSequence(seqSec);
            }

            mythicRulesByType.put(mythicType.toLowerCase(Locale.ROOT), new DropRule(
                    itemId, display, number, chance, killerRequired, broadcastEnabled,
                    broadcast == null ? "" : broadcast, nearbyRadius, nearbyMax, soundSeq));
        }
    }

    private List<SoundStep> parseSoundSequence(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyList();
        }
        var stepsSec = section.getConfigurationSection("steps");
        if (stepsSec == null) {
            return Collections.emptyList();
        }
        List<SoundStep> list = new ArrayList<>();
        for (String key : stepsSec.getKeys(false)) {
            var step = stepsSec.getConfigurationSection(key);
            if (step == null) {
                continue;
            }
            String soundName = step.getString("sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
            Sound sound;
            try {
                sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                continue;
            }
            float volume = (float) step.getDouble("volume", 1.0d);
            float pitch = (float) step.getDouble("pitch", 1.0d);
            long delay = Math.max(0L, step.getLong("delay-ticks", 0L));
            list.add(new SoundStep(sound, volume, pitch, delay));
        }
        return list;
    }

    private record Template(
            double chancePercent,
            boolean killerRequired,
            boolean broadcastEnabled,
            String broadcastMessage,
            List<SoundStep> soundSequence) {
    }

    private record DropRule(
            String itemId,
            String displayName,
            int number,
            double chancePercent,
            boolean killerRequired,
            boolean broadcastEnabled,
            String broadcastMessage,
            double nearbyRadius,
            int nearbyMax,
            List<SoundStep> soundSequence) {
    }

    private record SoundStep(Sound sound, float volume, float pitch, long delayTicks) {
    }

    private static final class NexoBridge {
        private final Method itemFromId;

        private NexoBridge(Method itemFromId) {
            this.itemFromId = itemFromId;
        }

        static NexoBridge tryCreate() {
            try {
                Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
                Method itemFromId = nexoItemsClass.getMethod("itemFromId", String.class);
                return new NexoBridge(itemFromId);
            } catch (Throwable ignored) {
                return null;
            }
        }

        ItemStack buildItem(String itemId, int amount) {
            try {
                Object raw = itemFromId.invoke(null, itemId);
                Object itemObj = unwrapOptional(raw);
                if (itemObj == null) {
                    return null;
                }
                ItemStack stack = extractItemStack(itemObj, amount);
                if (stack == null) {
                    return null;
                }
                stack.setAmount(amount);
                return stack;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private Object unwrapOptional(Object raw) {
            if (raw instanceof Optional<?> optional) {
                return optional.orElse(null);
            }
            return raw;
        }

        private ItemStack extractItemStack(Object itemObj, int amount) {
            if (itemObj instanceof ItemStack direct) {
                return direct.clone();
            }
            Method[] methods = itemObj.getClass().getMethods();
            for (Method m : methods) {
                String name = m.getName();
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class
                        && ("build".equals(name) || "buildItem".equals(name) || "asItemStack".equals(name)
                        || "createItemStack".equals(name))) {
                    try {
                        Object out = m.invoke(itemObj, amount);
                        if (out instanceof ItemStack stack) {
                            return stack.clone();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            for (Method m : methods) {
                String name = m.getName();
                if (m.getParameterCount() == 0
                        && ("build".equals(name) || "buildItem".equals(name)
                        || "getItemStack".equals(name) || "asItemStack".equals(name))) {
                    try {
                        Object out = m.invoke(itemObj);
                        if (out instanceof ItemStack stack) {
                            return stack.clone();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            return null;
        }
    }

    private static final class MythicBridge {
        private final Object mobManager;
        private final Method isMythicMobMethod;
        private final Method getActiveMobMethod;
        private final Method getMobTypeMethod;
        private final Method getInternalNameMethod;

        private MythicBridge(Object mobManager, Method isMythicMobMethod, Method getActiveMobMethod, Method getMobTypeMethod, Method getInternalNameMethod) {
            this.mobManager = mobManager;
            this.isMythicMobMethod = isMythicMobMethod;
            this.getActiveMobMethod = getActiveMobMethod;
            this.getMobTypeMethod = getMobTypeMethod;
            this.getInternalNameMethod = getInternalNameMethod;
        }

        static MythicBridge tryCreate() {
            try {
                Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                Object inst = mythicBukkitClass.getMethod("inst").invoke(null);
                Object mobManager = mythicBukkitClass.getMethod("getMobManager").invoke(inst);

                Method isMythicMob = mobManager.getClass().getMethod("isMythicMob", Entity.class);
                Method getActiveMob = mobManager.getClass().getMethod("getActiveMob", java.util.UUID.class);

                Class<?> activeMobClass = Class.forName("io.lumine.mythic.core.mobs.ActiveMob");
                Method getMobType = activeMobClass.getMethod("getMobType");
                Method getInternalName = getMobType.getReturnType().getMethod("getInternalName");

                return new MythicBridge(mobManager, isMythicMob, getActiveMob, getMobType, getInternalName);
            } catch (Throwable ignored) {
                return null;
            }
        }

        String getMythicType(Entity entity) {
            try {
                boolean isMythic = (boolean) isMythicMobMethod.invoke(mobManager, entity);
                if (!isMythic) {
                    return null;
                }
                Object optional = getActiveMobMethod.invoke(mobManager, entity.getUniqueId());
                if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) {
                    return null;
                }
                Object activeMob = opt.get();
                Object mobType = getMobTypeMethod.invoke(activeMob);
                Object internalName = getInternalNameMethod.invoke(mobType);
                return internalName == null ? null : internalName.toString();
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
