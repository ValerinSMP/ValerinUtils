package me.mtynnn.valerinutils.utils;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

public class LuckPermsHelper {

    /**
     * Verifica si un jugador tiene un grupo específico (ignorando case) usando
     * reflexión.
     * Esto permite que el plugin funcione sin LuckPerms instalado.
     */
    public static boolean hasGroup(Player player, String groupName, ValerinUtils plugin) {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            return false;
        }
        try {
            // Class.forName("net.luckperms.api.LuckPermsProvider")
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object api = getMethod.invoke(null);

            // api.getUserManager()
            Class<?> apiClass = api.getClass();
            Method getUserManagerMethod = apiClass.getMethod("getUserManager");
            Object userManager = getUserManagerMethod.invoke(api);

            // userManager.getUser(UUID)
            Class<?> userManagerClass = userManager.getClass();
            Method getUserMethod = userManagerClass.getMethod("getUser", UUID.class);
            Object user = getUserMethod.invoke(userManager, player.getUniqueId());

            if (user == null)
                return false;

            // QueryOptions.defaultContextualOptions()
            Class<?> queryOptionsClass = Class.forName("net.luckperms.api.query.QueryOptions");
            Method defaultContextualMethod = queryOptionsClass.getMethod("defaultContextualOptions");
            Object queryOptions = defaultContextualMethod.invoke(null);

            // user.getInheritedGroups(queryOptions)
            Method getInheritedGroupsMethod = user.getClass().getMethod("getInheritedGroups", queryOptionsClass);
            Object groups = getInheritedGroupsMethod.invoke(user, queryOptions);

            if (groups instanceof Collection<?>) {
                for (Object group : (Collection<?>) groups) {
                    Method getNameMethod = group.getClass().getMethod("getName");
                    String name = (String) getNameMethod.invoke(group);
                    if (name.equalsIgnoreCase(groupName)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("Error checking LuckPerms group: " + e.getMessage());
            }
            return false;
        }

        return false;
    }
}
