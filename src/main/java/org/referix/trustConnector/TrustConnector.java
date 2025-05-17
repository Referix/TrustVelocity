package org.referix.trustConnector;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.common.io.ByteArrayDataOutput;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import com.google.inject.Inject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(id = "trustconnector", name = "TrustConnector", version = BuildConstants.VERSION)
public class TrustConnector {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("trust:reputation");

    @Inject
    private Logger logger;

    @Inject
    private ProxyServer server;

    // Кеш: UUID → команда
    private final Map<UUID, String> playerCommands = new ConcurrentHashMap<>();


    public boolean hasPendingCommand(UUID uuid) {
        return playerCommands.containsKey(uuid);
    }


    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(CHANNEL);
        logger.info("Plugin messaging channel registered: " + CHANNEL.getId());
        server.getEventManager().register(this, new ServerConnectedListener(this,logger,server));


        // Для прикладу: перевірка команд кожні 10 секунд
//        server.getScheduler()
//                .buildTask(this, this::checkAndExecuteCommands)
//                .repeat(10, TimeUnit.SECONDS)
//                .schedule();
    }

    // Обробка вхідного повідомлення
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        try {
            String uuidString = in.readUTF();     // Наприклад: "a4f23a8c-..."
            String commandTemplate = in.readUTF(); // Наприклад: "/ban {player}"

            UUID uuid = UUID.fromString(uuidString);
            playerCommands.put(uuid, commandTemplate);

            logger.info("Received command for UUID {}: {}", uuid, commandTemplate);
        } catch (Exception e) {
            logger.warn("Invalid plugin message format", e);
        }
    }

    // Виконання команд
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

        // Очищення після виконання (опціонально)
        playerCommands.clear();
    }



    // Можна викликати з інших частин плагіна
    public String getCommandForPlayer(UUID uuid) {
        return playerCommands.get(uuid);
    }
}
