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
package org.spongepowered.common.event.tracking.phase.plugin;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.event.tracking.TrackingUtil;

import java.util.List;
import java.util.stream.Collectors;

final class ServerTickPhaseState extends ListenerPhaseState<ServerTickContext> {

    private final String desc;

    ServerTickPhaseState(String name) {
        this.desc = TrackingUtil.phaseStateToString("Plugin", name, this);
    }

    @Override
    public ServerTickContext createPhaseContext() {
        return new ServerTickContext(this).addCaptures().player();
    }

    @Override
    public void unwind(ServerTickContext phaseContext) {

        final Object listener = phaseContext.getSource(Object.class)
            .orElseThrow(TrackingUtil.throwWithContext("Expected to be capturing a ServerTickEvent listener!", phaseContext));

        // TODO - Determine if we need to pass the supplier or perform some parameterized
        //  process if not empty method on the capture object.
        TrackingUtil.processBlockCaptures(this, phaseContext);
        // This could be happening regardless whether block bulk captures are done or not.
        // Would depend on whether entity captures are done.
        phaseContext.getBlockItemDropSupplier()
            .acceptAndClearIfNotEmpty(map -> map.asMap().forEach((key, value) -> {
                try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                    frame.pushCause(listener);
                    frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.DROPPED_ITEM);
                    final List<Entity> items = value.stream().map(entity -> (Entity) entity).collect(Collectors.toList());
                    SpongeCommonEventFactory.callDropItemDestruct(items, phaseContext);
                }
            }));
    }

    @Override
    public boolean doesDenyChunkRequests() {
        return true;
    }

    @Override
    public boolean doesBulkBlockCapture(ServerTickContext context) {
        return false;
    }

    @Override
    public boolean doesCaptureEntitySpawns() {
        return false;
    }

    @Override
    public boolean doesCaptureEntityDrops(ServerTickContext context) {
        return false;
    }

    @Override
    public String toString() {
        return this.desc;
    }
}
