package me.mtynnn.valerinutils.network;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record NetworkEnvelope(int schemaVersion, UUID eventId, String networkId, String sourceServer,
                              String type, long createdAt, String payload) {
    private static final int MAX_BYTES = 65_536;
    private static final Set<String> TYPES = Set.of("BROADCAST", "HELPOP", "PRESENCE", "REMOTE_ACTION",
            "INVALIDATE_PLAYER", "INVALIDATE_SERVER", "EVENT_START", "EVENT_STOP");
    private static final Pattern JSON = Pattern.compile("\\{\"schemaVersion\":(\\d+),\"eventId\":\"([^\"]+)\","
            + "\"networkId\":\"([^\"]+)\",\"sourceServer\":\"([^\"]+)\",\"type\":\"([^\"]+)\","
            + "\"createdAt\":(\\d+),\"payload\":\"([^\"]*)\"}");

    public static NetworkEnvelope create(CrossServerConfig config, String type, String payload) {
        return new NetworkEnvelope(1, UUID.randomUUID(), config.networkId(), config.serverId(),
                type, System.currentTimeMillis(), payload);
    }

    public String encode() {
        String body = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return "{\"schemaVersion\":" + schemaVersion + ",\"eventId\":\"" + eventId
                + "\",\"networkId\":\"" + networkId + "\",\"sourceServer\":\"" + sourceServer
                + "\",\"type\":\"" + type + "\",\"createdAt\":" + createdAt
                + ",\"payload\":\"" + body + "\"}";
    }

    public static NetworkEnvelope decode(String json, CrossServerConfig config) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return null;
        Matcher matcher = JSON.matcher(json);
        if (!matcher.matches()) return null;
        try {
            int schema = Integer.parseInt(matcher.group(1));
            UUID id = UUID.fromString(matcher.group(2));
            String network = matcher.group(3);
            String source = matcher.group(4);
            String type = matcher.group(5);
            long created = Long.parseLong(matcher.group(6));
            String payload = new String(Base64.getDecoder().decode(matcher.group(7)), StandardCharsets.UTF_8);
            if (schema != 1 || !network.equals(config.networkId()) || !TYPES.contains(type)
                    || !source.matches("[a-zA-Z0-9._-]{1,64}") || created <= 0) return null;
            return new NetworkEnvelope(schema, id, network, source, type, created, payload);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    public boolean loopback(CrossServerConfig config) {
        return sourceServer.equals(config.serverId());
    }
}
