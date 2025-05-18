package org.referix.trustConnector;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;
import com.google.inject.Inject;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(id = "trustconnector", name = "TrustConnector", version = BuildConstants.VERSION)
public class TrustConnector {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("trust:reputation");

    @Inject private Logger logger;
    @Inject private ProxyServer server;

    private final Map<UUID, String> playerCommands = new ConcurrentHashMap<>();
    private DatabaseManager databaseManager;

    public boolean hasPendingCommand(UUID uuid) {
        return playerCommands.containsKey(uuid);
    }

    public void removePendingCommand(UUID uuid) {
        playerCommands.remove(uuid);
    }

    public String getCommandForPlayer(UUID uuid) {
        return playerCommands.get(uuid);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(CHANNEL);

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
        if (!event.getIdentifier().equals(CHANNEL)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        try {
            String uuidString = in.readUTF();
            String commandTemplate = in.readUTF();
            UUID uuid = UUID.fromString(uuidString);
            playerCommands.put(uuid, commandTemplate);
            logger.info("Received command for UUID {}: {}", uuid, commandTemplate);
        } catch (Exception e) {
            logger.warn("Invalid plugin message format", e);
        }
    }

    public void checkAndExecuteCommands() {
        for (Map.Entry<UUID, String> entry : playerCommands.entrySet()) {
            UUID uuid = entry.getKey();
            String commandTemplate = entry.getValue();
            server.getPlayer(uuid).ifPresent(player -> {
                String command = commandTemplate.replace("{player}", player.getUsername());
                server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
                logger.info("Executed command for {}: {}", player.getUsername(), command);
            });
        }
        playerCommands.clear();
    }
}
