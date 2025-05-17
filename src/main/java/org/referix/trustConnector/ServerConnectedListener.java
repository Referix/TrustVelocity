package org.referix.trustConnector;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ServerConnectedListener {

    private final TrustConnector plugin;
    private final Logger logger;
    private final ProxyServer server;

    public ServerConnectedListener(TrustConnector plugin, Logger logger, ProxyServer server1) {
        this.plugin = plugin;
        this.logger = logger;
        this.server = server1;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.hasPendingCommand(uuid)) return;

        RegisteredServer server = event.getServer();
        String commandTemplate = plugin.getCommandForPlayer(uuid);
        String command = commandTemplate.replace("{player}", player.getUsername());

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(uuid.toString());
        out.writeUTF(command);

        this.server.getScheduler().buildTask(plugin, () -> {
            Collection<Player> players = server.getPlayersConnected();
            if (players.isEmpty()) return;
            if (players.contains(player)){
                server.sendPluginMessage(TrustConnector.CHANNEL, out.toByteArray());
                logger.info("Delayed: Sent plugin message to server {} for player {}: {}", server.getServerInfo().getName(), player.getUsername(), command);

            }
        }).delay(1, TimeUnit.SECONDS).schedule();

        logger.info("Sent plugin message to server {} for player {}: {}", server.getServerInfo().getName(), player.getUsername(), command);
    }
}
