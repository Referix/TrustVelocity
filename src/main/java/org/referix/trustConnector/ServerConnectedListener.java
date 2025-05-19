package org.referix.trustConnector;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ServerConnectedListener {

    private final TrustConnector plugin;
    private final Logger logger;
    private final ProxyServer server;
    private final DatabaseManager databaseManager;

    public ServerConnectedListener(TrustConnector plugin, Logger logger, ProxyServer server1, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.logger = logger;
        this.server = server1;
        this.databaseManager = databaseManager;
    }
    private final Map<UUID, List<PendingCommandProgress>> commandProgressMap = new ConcurrentHashMap<>();

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String currentServer = event.getServer().getServerInfo().getName();

        // Якщо ще не завантажено
        commandProgressMap.computeIfAbsent(uuid, key -> plugin.getAllCommandsForPlayer(uuid).stream()
                .map(PendingCommandProgress::new)
                .collect(Collectors.toList())
        );

        Set<String> requiredServers = server.getAllServers().stream()
                .map(registeredServer -> registeredServer.getServerInfo().getName())
                .collect(Collectors.toSet()); // список із velocity.toml або іншого джерела
        logger.info("Required servers: " + requiredServers);
        List<PendingCommandProgress> progresses = commandProgressMap.get(uuid);
        for (PendingCommandProgress progress : progresses) {
            TrustConnector.PendingCommand cmd = progress.getCommand();

            if (!progress.getVisitedServers().contains(currentServer)) {
                // Надсилаємо команду
                String parsed = cmd.command().replace("{player}", player.getUsername());
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF(cmd.category());
                out.writeUTF(uuid.toString());
                out.writeUTF(parsed);

                event.getServer().sendPluginMessage(TrustConnector.CHANNEL, out.toByteArray());
                logger.info("Sent command to " + currentServer + ": " + parsed);
                progress.markVisited(currentServer);
            }

            // Якщо гравець відвідав усі потрібні сервери
            if (progress.isComplete(requiredServers)) {
                plugin.removePendingCommand(cmd.category(), uuid);
                try {
                    databaseManager.deleteCommand(cmd.category(), uuid);
                } catch (SQLException e) {
                    logger.error("Failed to delete command from DB", e);
                }
            }
        }
    }


}
