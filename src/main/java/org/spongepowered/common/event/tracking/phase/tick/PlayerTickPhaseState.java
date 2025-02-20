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
package org.spongepowered.common.event.tracking.phase.tick;

import net.minecraft.entity.item.EntityItem;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.CauseStackManager.StackFrame;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.event.tracking.phase.general.ExplosionContext;

import java.util.ArrayList;
import java.util.List;

class PlayerTickPhaseState extends TickPhaseState<PlayerTickContext> {

    PlayerTickPhaseState() {
    }

    @Override
    public PlayerTickContext createPhaseContext() {
        return new PlayerTickContext()
                .addCaptures()
                .addEntityDropCaptures()
                ;
    }

    @Override
    public void unwind(PlayerTickContext context) {
        final Player player = context.getSource(Player.class)
                .orElseThrow(TrackingUtil.throwWithContext("Not ticking on a Player!", context));
        try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(player);
            frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.PASSIVE);
            context.getCapturedEntitySupplier().acceptAndClearIfNotEmpty(entities -> {
                SpongeCommonEventFactory.callSpawnEntity(entities, context);
            });
            frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.DROPPED_ITEM);
            context.getCapturedItemsSupplier().acceptAndClearIfNotEmpty(entities -> {
                final ArrayList<Entity> capturedEntities = new ArrayList<>();
                for (EntityItem entity : entities) {
                    capturedEntities.add((Entity) entity);
                }

                SpongeCommonEventFactory.callSpawnEntity(capturedEntities, context);
            });
            // TODO - Determine if we need to pass the supplier or perform some parameterized
            //  process if not empty method on the capture object.
            TrackingUtil.processBlockCaptures(this, context);
        }
    }

    @Override
    public void appendContextPreExplosion(ExplosionContext explosionContext, PlayerTickContext context) {
        final Player player = context.getSource(Player.class)
                .orElseThrow(TrackingUtil.throwWithContext("Expected to be processing over a ticking TileEntity!", context));
        explosionContext.owner(player);
        explosionContext.notifier(player);
        explosionContext.source(player);
    }

    @Override
    public boolean spawnEntityOrCapture(PlayerTickContext context, Entity entity, int chunkX, int chunkZ) {
        final Player player = context.getSource(Player.class)
                .orElseThrow(TrackingUtil.throwWithContext("Not ticking on a Player!", context));
        try (StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(player);
            frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.PASSIVE);
            final List<Entity> entities = new ArrayList<>(1);
            entities.add(entity);
            return SpongeCommonEventFactory.callSpawnEntity(entities, context);
        }
    }

    @Override
    public boolean doesCaptureEntitySpawns() {
        return false;
    }

    @Override
    public boolean doesDenyChunkRequests() {
        return false;
    }
}
