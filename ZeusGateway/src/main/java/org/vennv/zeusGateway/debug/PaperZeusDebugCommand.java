package org.vennv.zeusGateway.debug;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;

/**
 * Paper plugins register commands through the Paper Command API rather than paper-plugin.yml.
 */
public final class PaperZeusDebugCommand implements BasicCommand {
    private final ZeusDebugCommand delegate;

    public PaperZeusDebugCommand(ZeusDebugCommand delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        delegate.execute(source.getSender(), args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return delegate.complete(source.getSender(), args);
    }

    @Override
    public String permission() {
        return "zeusgateway.debug.self";
    }
}
