package org.referix.trustConnector;

import java.util.HashSet;
import java.util.Set;

public class PendingCommandProgress {
    private final TrustConnector.PendingCommand command;

    public Set<String> getVisitedServers() {
        return visitedServers;
    }

    private final Set<String> visitedServers = new HashSet<>();

    public PendingCommandProgress(TrustConnector.PendingCommand command) {
        this.command = command;
    }

    public void markVisited(String server) {
        visitedServers.add(server);
    }

    public boolean isComplete(Set<String> allRequiredServers) {
        return visitedServers.containsAll(allRequiredServers);
    }

    public TrustConnector.PendingCommand getCommand() {
        return command;
    }
}
