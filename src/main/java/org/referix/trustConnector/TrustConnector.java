package org.referix.trustConnector;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;
import com.google.inject.Inject;

import java.io.File;
import java.util.*;

@Plugin(id = "trustconnector", name = "TrustConnector", version = BuildConstants.VERSION)
public class TrustConnector {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("trust:reputation");
    private static final MinecraftChannelIdentifier CACHE_CHANNEL = MinecraftChannelIdentifier.from("trust:cache");
    @Inject private Logger logger;
    @Inject private ProxyServer server;

    // Outer key: category (e.g. server name), inner key: player UUID, value: command
    private final Map<String, Map<UUID, String>> playerCommands = new HashMap<>();
    private DatabaseManager databaseManager;

    public boolean hasPendingCommand(String category, UUID uuid) {
        Map<UUID, String> cmds = playerCommands.get(category);
        return cmds != null && cmds.containsKey(uuid);
    }

    public List<PendingCommand> getAllCommandsForPlayer(UUID uuid) {
        List<PendingCommand> list = new ArrayList<>();
        for (Map.Entry<String, Map<UUID, String>> entry : playerCommands.entrySet()) {
            String category = entry.getKey();
            Map<UUID, String> cmds = entry.getValue();
            if (cmds.containsKey(uuid)) {
                list.add(new PendingCommand(category, uuid, cmds.get(uuid)));
            }
        }
        return list;
    }


    public void removePendingCommand(String category, UUID uuid) {
        Map<UUID, String> cmds = playerCommands.get(category);
        if (cmds != null) {
            cmds.remove(uuid);
            if (cmds.isEmpty()) {
                playerCommands.remove(category);
            }
        }
    }

    public String getCommandForPlayer(String category, UUID uuid) {
        Map<UUID, String> cmds = playerCommands.get(category);
        return (cmds != null) ? cmds.get(uuid) : null;
    }

    public void addCommand(String category, UUID uuid, String command) {
        playerCommands.computeIfAbsent(category, k -> new HashMap<>()).put(uuid, command);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(CHANNEL);
        server.getChannelRegistrar().register(CACHE_CHANNEL);  // Реєструємо канал кешу

        databaseManager = new DatabaseManager(new File("plugins/trustconnector"));
        try {
            databaseManager.connect();
            databaseManager.loadCommands(playerCommands);
            logger.info("Loaded commands from database.");
        } catch (Exception e) {
            logger.error("Failed to load commands from database.", e);
        }

        server.getEventManager().register(this, new ServerConnectedListener(this, logger, server, databaseManager));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        try {
            databaseManager.saveCommands(playerCommands);
            databaseManager.disconnect();
            logger.info("Saved commands to database and disconnected.");
        } catch (Exception e) {
            logger.error("Failed to save commands on shutdown.", e);
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        ChannelIdentifier channel = event.getIdentifier();

        if (channel.equals(CHANNEL)) {
            // Логіка для trust:reputation (команди)
            ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
            try {
                String category = in.readUTF();
                String uuidString = in.readUTF();
                String commandTemplate = in.readUTF();

                UUID uuid = UUID.fromString(uuidString);
                addCommand(category, uuid, commandTemplate);

                logger.info("Received command for category '{}', UUID {}: {}", category, uuid, commandTemplate);
            } catch (Exception e) {
                logger.warn("Invalid plugin message format", e);
            }
        } else if (channel.equals(CACHE_CHANNEL)) {
            // Обробка кеш-повідомлень trust:cache — ретрансляція всім серверам
            byte[] data = event.getData();
            for (var targetServer : server.getAllServers()) {
                targetServer.sendPluginMessage(CACHE_CHANNEL, data);
            }
            logger.info("Redistributed cache message to all servers.");
        }
    }

    public void checkAndExecuteCommands() {
        for (Map.Entry<String, Map<UUID, String>> categoryEntry : playerCommands.entrySet()) {
            String category = categoryEntry.getKey();
            Map<UUID, String> commandMap = categoryEntry.getValue();

            for (Map.Entry<UUID, String> entry : commandMap.entrySet()) {
                UUID uuid = entry.getKey();
                String commandTemplate = entry.getValue();

                server.getPlayer(uuid).ifPresent(player -> {
                    String command = commandTemplate.replace("{player}", player.getUsername());
                    server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
                    logger.info("Executed command in category '{}' for {}: {}", category, player.getUsername(), command);
                });
            }
        }

        playerCommands.clear(); // Clear after execution if necessary
    }

    public record PendingCommand(String category, UUID uuid, String command) {
    }
}
