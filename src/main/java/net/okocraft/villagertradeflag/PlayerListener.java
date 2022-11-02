package net.okocraft.villagertradeflag;

import com.sk89q.worldedit.util.formatting.text.BlockNbtComponent;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.ComponentBuilder;
import com.sk89q.worldedit.util.formatting.text.KeybindComponent;
import com.sk89q.worldedit.util.formatting.text.NbtComponent;
import com.sk89q.worldedit.util.formatting.text.SelectorComponent;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.formatting.text.serializer.gson.GsonComponentSerializer;
import java.util.Arrays;
import java.util.List;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.cause.Cause;
import com.sk89q.worldguard.bukkit.event.DelegateEvent;
import com.sk89q.worldguard.bukkit.event.entity.UseEntityEvent;
import com.sk89q.worldguard.bukkit.internal.WGMetadata;
import com.sk89q.worldguard.bukkit.util.Entities;
import com.sk89q.worldguard.bukkit.util.InteropUtils;
import com.sk89q.worldguard.commands.CommandUtils;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.regions.RegionQuery;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Event.Result;

public class PlayerListener extends AbstractListener {

    private static final String DENY_MESSAGE_KEY = "worldguard.region.lastMessage";
    private static final int LAST_MESSAGE_DELAY = 500;

    private final Main plugin;

    PlayerListener(Main plugin) {
        super(WorldGuardPlugin.inst());
        this.plugin = plugin;
    }
    
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onUseEntity(UseEntityEvent event) {
        if (event.getResult() == Result.ALLOW) return; // Don't care about events that have been pre-allowed
        if (!isRegionSupportEnabled(event.getWorld())) return; // Region support disabled
        if (isWhitelisted(event.getCause(), event.getWorld(), false)) return; // Whitelisted cause
        if (!Entities.isNPC(event.getEntity())) return;

        Location target = event.getTarget();
        RegionAssociable associable = createRegionAssociable(event.getCause());

        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        State state = query.queryState(BukkitAdapter.adapt(target), associable, combine(event, plugin.getVillagerTradeFlag()));
        if (state != State.ALLOW) {
            tellErrorMessage(event, event.getCause(), target, TranslatableComponent.of("worldguard.error.denied.what.use-that"));
            event.setCancelled(true);
        }
    }

    private void tellErrorMessage(DelegateEvent event, Cause cause, Location location, TranslatableComponent what) {
        if (event.isSilent() || cause.isIndirect()) {
            return;
        }

        Object rootCause = cause.getRootCause();

        if (rootCause instanceof Player) {
            Player player = (Player) rootCause;

            long now = System.currentTimeMillis();
            Long lastTime = WGMetadata.getIfPresent(player, DENY_MESSAGE_KEY, Long.class);
            if (lastTime == null || now - lastTime >= LAST_MESSAGE_DELAY) {
                RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
                LocalPlayer localPlayer = getPlugin().wrapPlayer(player);
                Component message = (Component) query.queryValue(
                        BukkitAdapter.adapt(location),
                        localPlayer,
                        Flags.fuzzyMatchFlag(WorldGuard.getInstance().getFlagRegistry(), "deny-message-component")
                );
                formatAndSendDenyMessage(what, localPlayer, message);
                WGMetadata.put(player, DENY_MESSAGE_KEY, now);
            }
        }
    }

    static void formatAndSendDenyMessage(TranslatableComponent what, LocalPlayer localPlayer, Component message) {
        if (message == null) return;

        String str = GsonComponentSerializer.INSTANCE.serialize(putTranslatableArgs(message, what));
        localPlayer.print(GsonComponentSerializer.INSTANCE.deserialize(CommandUtils.replaceColorMacros(
                WorldGuard.getInstance().getPlatform().getMatcher().replaceMacros(localPlayer, str))));
    }

    private static ComponentBuilder<?, ?> toBuilder(Component component) {
        if (component instanceof BlockNbtComponent) {
            return ((TextComponent) component).toBuilder();
        } else if (component instanceof NbtComponent) {
            return ((NbtComponent<?, ?>) component).toBuilder();
        } else if (component instanceof KeybindComponent) {
            return ((KeybindComponent) component).toBuilder();
        } else if (component instanceof SelectorComponent) {
            return ((SelectorComponent) component).toBuilder();
        } else if (component instanceof TextComponent) {
            return ((TextComponent) component).toBuilder();
        } else if (component instanceof TranslatableComponent) {
            return ((TranslatableComponent) component).toBuilder();
        } else {
            return null;
        }
    }

    private static Component putTranslatableArgs(Component component, Component... args) {
        ComponentBuilder<?, ?> builder = toBuilder(component);
        if (builder == null) return component;

        return builder.mapChildrenDeep(c -> {
            if (c instanceof TranslatableComponent) {
                return ((TranslatableComponent) c).args(args);
            } else {
                return c;
            }
        }).build();
    }


    private boolean isWhitelisted(Cause cause, World world, boolean pvp) {
        Object rootCause = cause.getRootCause();

        if (rootCause instanceof Player) {
            Player player = (Player) rootCause;
            WorldConfiguration config = getWorldConfig(world);

            if (config.fakePlayerBuildOverride && InteropUtils.isFakePlayer(player)) {
                return true;
            }

            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            return !pvp && WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(localPlayer, localPlayer.getWorld());
        } else {
            return false;
        }
    }

    private static StateFlag[] combine(DelegateEvent event, StateFlag... flag) {
        List<StateFlag> extra = event.getRelevantFlags();
        StateFlag[] flags = Arrays.copyOf(flag, flag.length + extra.size());
        for (int i = 0; i < extra.size(); i++) {
            flags[flag.length + i] = extra.get(i);
        }
        return flags;
    }
}