package net.okocraft.villagertradeflag;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private PlayerListener playerListener;

    private StateFlag villagerTradeFlag;

    @Override
    public void onLoad() {
        villagerTradeFlag = registerStateFlag("villager-trade", false, RegionGroup.NON_MEMBERS);
    }

    private StateFlag registerStateFlag(String name, boolean def, RegionGroup group) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag(name, def, group);
            registry.register(flag);
            return flag;
        } catch (FlagConflictException | IllegalStateException e) {
            // some other plugin registered a flag by the same name already.
            // you can use the existing flag, but this may cause conflicts - be sure to
            // check type
            Flag<?> existing = registry.get(name);
            if (existing instanceof StateFlag) {
                return (StateFlag) existing;
            } else {
                // types don't match - this is bad news! some other plugin conflicts with you
                // hopefully this never actually happens
                Bukkit.getPluginManager().disablePlugin(this);
                System.out.println("flag is null.");
                return null;
            }
        }
    }

    @Override
    public void onEnable() {
        playerListener = new PlayerListener(this);
        playerListener.start();
    }

    @Override
    public void onDisable() {
        playerListener.stop();
    };

    public StateFlag getVillagerTradeFlag() {
        return villagerTradeFlag;
    }
}
