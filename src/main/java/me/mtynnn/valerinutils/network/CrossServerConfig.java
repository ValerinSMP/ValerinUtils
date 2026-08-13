package me.mtynnn.valerinutils.network;

import org.bukkit.configuration.ConfigurationSection;

public record CrossServerConfig(boolean enabled, String networkId, String serverId,
                                String mysqlUrl, String mysqlUser, String mysqlPassword,
                                String redisHost, int redisPort, String redisPassword,
                                int redisDatabase, int redisTimeoutMs) {
    public static CrossServerConfig parse(ConfigurationSection root) {
        if (root == null || !root.getBoolean("enabled", false)) return disabled();
        return parseEnabled(root);
    }

    public static CrossServerConfig migrationTarget(ConfigurationSection root) {
        if (root == null) throw new IllegalArgumentException("cross-server configuration is required");
        return parseEnabled(root);
    }

    private static CrossServerConfig parseEnabled(ConfigurationSection root) {
        String network = required(root, "network-id");
        String server = required(root, "server-id");
        ConfigurationSection mysql = requiredSection(root, "mysql");
        ConfigurationSection redis = requiredSection(root, "redis");
        String host = required(mysql, "host");
        String database = required(mysql, "database");
        String user = required(mysql, "username");
        String password = required(mysql, "password");
        int mysqlPort = port(mysql, "port", 3306);
        String parameters = mysql.getString("parameters", "").trim();
        String url = "jdbc:mysql://" + host + ":" + mysqlPort + "/" + database
                + (parameters.isEmpty() ? "" : "?" + parameters);
        return new CrossServerConfig(true, safeId(network, "network-id"), safeId(server, "server-id"),
                url, user, password, required(redis, "host"), port(redis, "port", 6379),
                redis.getString("password", ""), Math.max(0, redis.getInt("database", 0)),
                Math.max(250, redis.getInt("timeout-ms", 2000)));
    }

    public static CrossServerConfig disabled() {
        return new CrossServerConfig(false, "local", "local", "", "", "", "", 0, "", 0, 2000);
    }

    public String namespace() {
        return "valerin:" + networkId + ":valerinutils";
    }

    private static ConfigurationSection requiredSection(ConfigurationSection root, String path) {
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null) throw new IllegalArgumentException("cross-server." + path + " is required");
        return section;
    }

    private static String required(ConfigurationSection root, String path) {
        String value = root.getString(path, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException(path + " is required");
        return value;
    }

    private static int port(ConfigurationSection root, String path, int fallback) {
        int value = root.getInt(path, fallback);
        if (value < 1 || value > 65535) throw new IllegalArgumentException(path + " must be a valid port");
        return value;
    }

    private static String safeId(String value, String path) {
        if (!value.matches("[a-zA-Z0-9._-]{1,64}")) {
            throw new IllegalArgumentException(path + " contains invalid characters");
        }
        return value;
    }
}
