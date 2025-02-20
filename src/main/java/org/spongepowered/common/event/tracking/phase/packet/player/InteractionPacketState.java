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
package org.spongepowered.common.event.tracking.phase.packet.player;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.data.type.HandType;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.projectile.Projectile;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.api.event.item.inventory.DropItemEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.block.SpongeBlockSnapshot;
import org.spongepowered.common.bridge.inventory.ContainerBridge;
import org.spongepowered.common.bridge.world.ServerWorldBridge;
import org.spongepowered.common.event.ShouldFire;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.event.tracking.context.ItemDropData;
import org.spongepowered.common.event.tracking.phase.packet.PacketState;
import org.spongepowered.common.item.inventory.util.ContainerUtil;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public final class InteractionPacketState extends PacketState<InteractionPacketContext> {


    @Override
    public InteractionPacketContext createPhaseContext() {
        return new InteractionPacketContext(this);
    }

    @Override
    public boolean isInteraction() {
        return true;
    }

    @Override
    public void populateContext(EntityPlayerMP playerMP, Packet<?> packet, InteractionPacketContext context) {
        final ItemStack stack = ItemStackUtil.cloneDefensive(playerMP.getHeldItemMainhand());
        if (stack != null) {
            context.itemUsed(stack);
        }
        final BlockPos target = ((CPacketPlayerDigging) packet).getPosition();
        if (!playerMP.world.isBlockLoaded(target)) {
            context.targetBlock(BlockSnapshot.NONE);
        } else {
            context.targetBlock(((ServerWorldBridge) playerMP.world).bridge$createSnapshot(target, BlockChangeFlags.NONE));
        }
        context.handUsed(HandTypes.MAIN_HAND);
    }

    @Override
    public boolean spawnEntityOrCapture(InteractionPacketContext context, Entity entity, int chunkX, int chunkZ) {
        return context.captureEntity(entity);
    }

    @Override
    public boolean shouldCaptureEntity() {
        return true;
    }

    @Override
    public boolean doesCaptureEntityDrops(InteractionPacketContext context) {
        return true;
    }

    @Override
    public boolean tracksTileEntityChanges(InteractionPacketContext currentContext) {
        return true;
    }

    @Override
    public boolean hasSpecificBlockProcess(InteractionPacketContext context) {
        return true;
    }

    @Override
    public boolean doesCaptureNeighborNotifications(InteractionPacketContext context) {
        return true;
    }

    @Override
    public boolean tracksBlockSpecificDrops(InteractionPacketContext context) {
        return true;
    }

    @Override
    public boolean alreadyProcessingBlockItemDrops() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void unwind(InteractionPacketContext phaseContext) {

        final EntityPlayerMP player = phaseContext.getPacketPlayer();
        final ItemStack usedStack = phaseContext.getItemUsed();
        final HandType usedHand = phaseContext.getHandUsed();
        final ItemStackSnapshot usedSnapshot = ItemStackUtil.snapshotOf(usedStack);
        final Entity spongePlayer = (Entity) player;
        final BlockSnapshot targetBlock = phaseContext.getTargetBlock();

        try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(spongePlayer);
            frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.DROPPED_ITEM);
            frame.addContext(EventContextKeys.USED_ITEM, usedSnapshot);
            frame.addContext(EventContextKeys.USED_HAND, usedHand);
            frame.addContext(EventContextKeys.BLOCK_HIT, targetBlock);
            final boolean hasBlocks = !phaseContext.getCapturedBlockSupplier().isEmpty();
            final List<SpongeBlockSnapshot> capturedBlcoks = phaseContext.getCapturedOriginalBlocksChanged();
            final @Nullable BlockSnapshot firstBlockChange = hasBlocks ? capturedBlcoks.isEmpty()? null : capturedBlcoks.get(0) : null;
            if (hasBlocks) {
                if (!TrackingUtil.processBlockCaptures(this, phaseContext)) {
                    // Stop entities like XP from being spawned
                    phaseContext.getBlockItemDropSupplier().get().clear();
                    phaseContext.getCapturedItems().clear();
                    phaseContext.getPerEntityItemDropSupplier().get().clear();
                    phaseContext.getCapturedEntities().clear();
                    return;
                }
            }
            phaseContext.getBlockItemDropSupplier().acceptAndClearIfNotEmpty(map -> {
                if (ShouldFire.DROP_ITEM_EVENT_DESTRUCT) {
                    for (Map.Entry<BlockPos, Collection<EntityItem>> entry : map.asMap().entrySet()) {
                        if (!entry.getValue().isEmpty()) {
                            final List<Entity> items = entry.getValue().stream().map(entity -> (Entity) entity).collect(Collectors.toList());
                            final DropItemEvent.Destruct event =
                                SpongeEventFactory.createDropItemEventDestruct(Sponge.getCauseStackManager().getCurrentCause(), items);
                            SpongeImpl.postEvent(event);
                            if (!event.isCancelled()) {
                                processSpawnedEntities(player, event);
                            }
                        }
                    }
                } else {
                    for (Map.Entry<BlockPos, Collection<EntityItem>> entry : map.asMap().entrySet()) {
                        if (!entry.getValue().isEmpty()) {
                            processEntities(player, (Collection<Entity>) (Collection<?>) entry.getValue());
                        }
                    }
                }
            });


            phaseContext.getCapturedItemsSupplier()
                .acceptAndClearIfNotEmpty(items -> {
                    final ArrayList<Entity> entities = new ArrayList<>();
                    for (EntityItem item : items) {
                        entities.add((Entity) item);
                    }
                    final DropItemEvent.Dispense dispense =
                        SpongeEventFactory.createDropItemEventDispense(Sponge.getCauseStackManager().getCurrentCause(), entities);
                    SpongeImpl.postEvent(dispense);
                    if (!dispense.isCancelled()) {
                        processSpawnedEntities(player, dispense);
                    }
                });
            phaseContext.getPerEntityItemDropSupplier()
                .acceptAndClearIfNotEmpty(map -> {
                    if (map.isEmpty()) {
                        return;
                    }
                    final PrettyPrinter printer = new PrettyPrinter(80);
                    printer.add("Processing Interaction").centre().hr();
                    printer.add("The item stacks captured are: ");
                    for (Map.Entry<UUID, Collection<ItemDropData>> entry : map.asMap().entrySet()) {
                        printer.add("  - Entity with UUID: %s", entry.getKey());
                        for (ItemDropData stack : entry.getValue()) {
                            printer.add("    - %s", stack);
                        }
                    }
                    printer.trace(System.err);
                });
            phaseContext.getCapturedEntitySupplier().acceptAndClearIfNotEmpty(entities -> {
                throwEntitySpawnEvents(phaseContext, player, usedSnapshot, firstBlockChange, entities);
            });

            phaseContext.getPerEntityItemEntityDropSupplier().acceptAndClearIfNotEmpty((multimap -> {
                for (Map.Entry<UUID, Collection<EntityItem>> entry : multimap.asMap().entrySet()) {
                    if (entry.getKey().equals(player.getUniqueID())) {
                        throwEntitySpawnEvents(phaseContext, player, usedSnapshot, firstBlockChange, (Collection<Entity>) (Collection<?>) entry.getValue());
                    } else {
                        final net.minecraft.entity.Entity spawnedEntity = ((WorldServer) player.world).getEntityFromUuid(entry.getKey());
                        if (spawnedEntity != null) {
                            try (CauseStackManager.StackFrame entityFrame = Sponge.getCauseStackManager().pushCauseFrame()) {
                                entityFrame.pushCause(spawnedEntity);
                                throwEntitySpawnEvents(phaseContext, player, usedSnapshot, firstBlockChange, (Collection<Entity>) (Collection<?>) entry.getValue());
                            }
                        }
                    }
                }
            }));

            final ContainerBridge mixinContainer = ContainerUtil.toMixin(player.openContainer);
            mixinContainer.setCaptureInventory(false);
            mixinContainer.bridge$getCapturedSlotTransactions().clear();
        }
    }

    private void throwEntitySpawnEvents(InteractionPacketContext phaseContext, EntityPlayerMP player, ItemStackSnapshot usedSnapshot,
        BlockSnapshot firstBlockChange, Collection<Entity> entities) {
        final List<Entity> projectiles = new ArrayList<>(entities.size());
        final List<Entity> spawnEggs = new ArrayList<>(entities.size());
        final List<Entity> xpOrbs = new ArrayList<>(entities.size());
        final List<Entity> normalPlacement = new ArrayList<>(entities.size());
        final List<Entity> items = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            if (entity instanceof Projectile || entity instanceof EntityThrowable) {
                projectiles.add(entity);
            } else if (usedSnapshot.getType() == ItemTypes.SPAWN_EGG) {
                spawnEggs.add(entity);
            } else if (entity instanceof EntityItem) {
                items.add(entity);
            } else if (entity instanceof EntityXPOrb) {
                xpOrbs.add(entity);
            } else {
                normalPlacement.add(entity);
            }
        }
        if (!projectiles.isEmpty()) {
            if (ShouldFire.SPAWN_ENTITY_EVENT) {
                try (CauseStackManager.StackFrame frame2 = Sponge.getCauseStackManager().pushCauseFrame()) {
                    frame2.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.PROJECTILE);
                    frame2.pushCause(usedSnapshot);
                    SpongeCommonEventFactory.callSpawnEntity(projectiles, phaseContext);
                }
            } else {
                processEntities(player, projectiles);
            }
        }
        if (!spawnEggs.isEmpty()) {
            if (ShouldFire.SPAWN_ENTITY_EVENT) {
                try (CauseStackManager.StackFrame frame2 = Sponge.getCauseStackManager().pushCauseFrame()) {
                    frame2.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.SPAWN_EGG);
                    frame2.pushCause(usedSnapshot);
                    SpongeCommonEventFactory.callSpawnEntity(spawnEggs, phaseContext);
                }
            } else {
                processEntities(player, spawnEggs);
            }
        }
        if (!items.isEmpty()) {
            if (ShouldFire.DROP_ITEM_EVENT_DISPENSE) {
                final DropItemEvent.Dispense dispense = SpongeEventFactory
                    .createDropItemEventDispense(Sponge.getCauseStackManager().getCurrentCause(), items);
                if (!SpongeImpl.postEvent(dispense)) {
                    processSpawnedEntities(player, dispense);
                }
            } else {
                processEntities(player, items);
            }
        }
        if (!xpOrbs.isEmpty()) {
            if (ShouldFire.SPAWN_ENTITY_EVENT) {
                try (final CauseStackManager.StackFrame stackFrame = Sponge.getCauseStackManager().pushCauseFrame()) {
                    if (firstBlockChange != null) {
                        stackFrame.pushCause(firstBlockChange);
                    }
                    stackFrame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.EXPERIENCE);
                    SpongeCommonEventFactory.callSpawnEntity(xpOrbs, phaseContext);
                }
            } else {
                processEntities(player, xpOrbs);
            }
        }
        if (!normalPlacement.isEmpty()) {
            if (ShouldFire.SPAWN_ENTITY_EVENT) {
                try (final CauseStackManager.StackFrame stackFrame = Sponge.getCauseStackManager().pushCauseFrame()) {
                    if (firstBlockChange != null) {
                        stackFrame.pushCause(firstBlockChange);
                    }
                    SpongeCommonEventFactory.callSpawnEntity(normalPlacement, phaseContext);
                }
            } else {
                processEntities(player, normalPlacement);
            }
        }
    }

    @Override
    public boolean tracksEntitySpecificDrops() {
        return true;
    }

}
