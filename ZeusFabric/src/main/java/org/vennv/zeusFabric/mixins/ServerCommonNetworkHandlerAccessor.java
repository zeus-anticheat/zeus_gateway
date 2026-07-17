package org.vennv.zeusFabric.mixins;

import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerCommonNetworkHandler.class)
public interface ServerCommonNetworkHandlerAccessor {
    @Accessor("waitingForKeepAlive")
    boolean zeus$isWaitingForKeepAlive();

    @Accessor("lastKeepAliveTime")
    long zeus$getLastKeepAliveTime();
}
