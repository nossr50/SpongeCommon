/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.mixin.realtime.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.mixin.realtime.IMixinRealTimeTicking;

@Mixin(NetHandlerPlayServer.class)
public abstract class MixinNetHandlerPlayServer_RealTime {

    @Shadow private int chatSpamThresholdCount;
    @Shadow private int itemDropThreshold;
    @Shadow @Final private MinecraftServer server;
    @Shadow public EntityPlayerMP player;

    @Redirect(
        method = "update",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/network/NetHandlerPlayServer;chatSpamThresholdCount:I",
            opcode = Opcodes.PUTFIELD,
            ordinal = 0
        )
    )
    private void adjustForRealTimeChatSpamCheck(NetHandlerPlayServer self, int modifier) {
        if (SpongeImplHooks.isFakePlayer(this.player) || ((WorldBridge) this.player.world).isFake()) {
            this.chatSpamThresholdCount = modifier;
            return;
        }
        int ticks = (int) ((IMixinRealTimeTicking) this.server).getRealTimeTicks();
        this.chatSpamThresholdCount = Math.max(0, this.chatSpamThresholdCount - ticks);
    }

    @Redirect(
        method = "update",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/network/NetHandlerPlayServer;itemDropThreshold:I",
            opcode = Opcodes.PUTFIELD, ordinal = 0
        )
    )
    private void adjustForRealTimeDropSpamCheck(NetHandlerPlayServer self, int modifier) {
        if (SpongeImplHooks.isFakePlayer(this.player) || ((WorldBridge) this.player.world).isFake()) {
            this.itemDropThreshold = modifier;
            return;
        }
        int ticks = (int) ((IMixinRealTimeTicking) this.server).getRealTimeTicks();
        this.itemDropThreshold = Math.max(0, this.itemDropThreshold - ticks);
    }

}
