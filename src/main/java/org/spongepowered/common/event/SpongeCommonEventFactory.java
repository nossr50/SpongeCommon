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
package org.spongepowered.common.event;

import static org.spongepowered.common.event.tracking.phase.packet.PacketPhaseUtil.handleCustomCursor;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.ImmutableList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.state.BlockPistonStructureHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketCreativeInventoryAction;
import net.minecraft.network.play.server.SPacketOpenWindow;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.Level;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.type.HandType;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.Item;
import org.spongepowered.api.entity.Transform;
import org.spongepowered.api.entity.explosive.Explosive;
import org.spongepowered.api.entity.living.Agent;
import org.spongepowered.api.entity.living.Human;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.entity.projectile.source.ProjectileSource;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.CollideBlockEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.NotifyNeighborBlockEvent;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.api.event.entity.ChangeEntityEquipmentEvent;
import org.spongepowered.api.event.entity.CollideEntityEvent;
import org.spongepowered.api.event.entity.DestructEntityEvent;
import org.spongepowered.api.event.entity.InteractEntityEvent;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.entity.RotateEntityEvent;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.entity.ai.SetAITargetEvent;
import org.spongepowered.api.event.entity.explosive.DetonateExplosiveEvent;
import org.spongepowered.api.event.item.inventory.ChangeInventoryEvent;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.event.item.inventory.CraftItemEvent;
import org.spongepowered.api.event.item.inventory.DropItemEvent;
import org.spongepowered.api.event.item.inventory.InteractInventoryEvent;
import org.spongepowered.api.event.item.inventory.InteractItemEvent;
import org.spongepowered.api.event.item.inventory.UpdateAnvilEvent;
import org.spongepowered.api.event.message.MessageEvent;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.InventoryArchetype;
import org.spongepowered.api.item.inventory.InventoryArchetypes;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.crafting.CraftingInventory;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.api.item.inventory.type.CarriedInventory;
import org.spongepowered.api.item.recipe.crafting.CraftingRecipe;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.world.LocatableBlock;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.explosion.Explosion;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.block.SpongeBlockSnapshot;
import org.spongepowered.common.block.SpongeBlockSnapshotBuilder;
import org.spongepowered.common.bridge.OwnershipTrackedBridge;
import org.spongepowered.common.bridge.explosives.ExplosiveBridge;
import org.spongepowered.common.bridge.inventory.ContainerBridge;
import org.spongepowered.common.bridge.world.chunk.ActiveChunkReferantBridge;
import org.spongepowered.common.bridge.world.chunk.ChunkBridge;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.entity.PlayerTracker;
import org.spongepowered.common.event.inventory.UpdateAnvilEventCost;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.phase.general.GeneralPhase;
import org.spongepowered.common.event.tracking.phase.packet.PacketPhaseUtil;
import org.spongepowered.common.event.tracking.phase.tick.EntityTickContext;
import org.spongepowered.common.bridge.inventory.TrackedInventoryBridge;
import org.spongepowered.common.bridge.entity.EntityBridge;
import org.spongepowered.common.bridge.entity.player.ServerPlayerEntityBridge;
import org.spongepowered.common.interfaces.entity.player.IMixinInventoryPlayer;
import org.spongepowered.common.bridge.world.ServerWorldBridge;
import org.spongepowered.common.item.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.item.inventory.adapter.impl.slots.SlotAdapter;
import org.spongepowered.common.item.inventory.custom.CustomInventory;
import org.spongepowered.common.item.inventory.util.ContainerUtil;
import org.spongepowered.common.item.inventory.util.InventoryUtil;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;
import org.spongepowered.common.registry.provider.DirectionFacingProvider;
import org.spongepowered.common.text.SpongeTexts;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.world.SpongeLocatableBlockBuilder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

public class SpongeCommonEventFactory {

    // Set if any of the events fired during interaction with a block (open

    public static int lastAnimationPacketTick = 0;
    // For animation packet
    public static int lastSecondaryPacketTick = 0;
    public static int lastPrimaryPacketTick = 0;
    @Nullable public static WeakReference<EntityPlayerMP> lastAnimationPlayer;

    public static void callDropItemDispense(final List<EntityItem> items, final PhaseContext<?> context) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.DISPENSE);
            final ArrayList<Entity> entities = new ArrayList<>();
            for (final EntityItem item : items) {
                entities.add((Entity) item);
            }
            final DropItemEvent.Dispense dispense =
                SpongeEventFactory.createDropItemEventDispense(frame.getCurrentCause(), entities);
            SpongeImpl.postEvent(dispense);
            if (!dispense.isCancelled()) {
                EntityUtil.processEntitySpawnsFromEvent(context, dispense);
            }
        }
    }

    public static void callDropItemDrop(final List<EntityItem> items, final PhaseContext<?> context) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.DROPPED_ITEM);
            final ArrayList<Entity> entities = new ArrayList<>();
            for (final EntityItem item : items) {
                entities.add((Entity) item);
            }
            final DropItemEvent.Dispense dispense =
                SpongeEventFactory.createDropItemEventDispense(frame.getCurrentCause(), entities);
            SpongeImpl.postEvent(dispense);
            if (!dispense.isCancelled()) {
                EntityUtil.processEntitySpawnsFromEvent(context, dispense);
            }
        }
    }

    public static void callDropItemCustom(final List<Entity> items, final PhaseContext<?> context) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.DROPPED_ITEM);
            final DropItemEvent.Custom event =
                SpongeEventFactory.createDropItemEventCustom(frame.getCurrentCause(), items);
            SpongeImpl.postEvent(event);
            if (!event.isCancelled()) {
                EntityUtil.processEntitySpawnsFromEvent(context, event);
            }
        }
    }

    public static void callDropItemCustom(final List<Entity> items, final PhaseContext<?> context, final Supplier<Optional<User>> supplier) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.getCurrentContext().require(EventContextKeys.SPAWN_TYPE);
            final DropItemEvent.Custom event = SpongeEventFactory.createDropItemEventCustom(frame.getCurrentCause(), items);
            SpongeImpl.postEvent(event);
            if (!event.isCancelled()) {
                EntityUtil.processEntitySpawnsFromEvent(event, supplier);
            }
        }
    }

    public static void callDropItemClose(final List<Entity> items, final PhaseContext<?> context, final Supplier<Optional<User>> supplier) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.getCurrentContext().require(EventContextKeys.SPAWN_TYPE);
            final DropItemEvent.Close event = SpongeEventFactory.createDropItemEventClose(frame.getCurrentCause(), items);
            SpongeImpl.postEvent(event);
            if (!event.isCancelled()) {
                EntityUtil.processEntitySpawnsFromEvent(event, supplier);
            }
        }
    }

    public static boolean callSpawnEntitySpawner(final List<Entity> entities, final PhaseContext<?> context) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.WORLD_SPAWNER);

            final SpawnEntityEvent event = SpongeEventFactory.createSpawnEntityEventSpawner(frame.getCurrentCause(), entities);
            SpongeImpl.postEvent(event);
            if (!event.isCancelled() && event.getEntities().size() > 0) {
                return EntityUtil.processEntitySpawnsFromEvent(context, event);
            }
            return false;
        }
    }

    public static void callDropItemDestruct(final List<Entity> entities, final PhaseContext<?> context) {
        final DropItemEvent.Destruct destruct = SpongeEventFactory.createDropItemEventDestruct(Sponge.getCauseStackManager().getCurrentCause(), entities);
        SpongeImpl.postEvent(destruct);
        if (!destruct.isCancelled()) {
            EntityUtil.processEntitySpawnsFromEvent(context, destruct);
        }
    }

    public static boolean callSpawnEntity(final List<Entity> entities, final PhaseContext<?> context) {
        Sponge.getCauseStackManager().getCurrentContext().require(EventContextKeys.SPAWN_TYPE);
        try {
            final SpawnEntityEvent event = SpongeEventFactory.createSpawnEntityEvent(Sponge.getCauseStackManager().getCurrentCause(), entities);
            SpongeImpl.postEvent(event);
            return !event.isCancelled() && EntityUtil.processEntitySpawnsFromEvent(context, event);
        } catch (final Exception e) {
            final PrettyPrinter printer = new PrettyPrinter(60).add("Exception trying to create a Spawn Event").centre().hr()
                .addWrapped(
                    "Something did not go well trying to create an event or while trying to throw a SpawnEntityEvent. My bet is it's gremlins")
                .add()
                .add("At the very least here's some information about what's going to be directly spawned without an event:");
            printer.add("Entities:");
            for (final Entity entity : entities) {
                printer.add(" - " + entity);
            }
            printer.add("PhaseContext:");
            context.printCustom(printer, 4);
            printer.add();
            printer.add("Exception:");
            printer.add(e);
            printer.log(SpongeImpl.getLogger(), Level.ERROR);
            for (final Entity entity : entities) {
                EntityUtil.processEntitySpawn(entity, EntityUtil.ENTITY_CREATOR_FUNCTION.apply(context));
            }
            return true;
        }
    }

    public static boolean callSpawnEntityCustom(final List<Entity> entities, final PhaseContext<?> context) {
        final SpawnEntityEvent.Custom event = SpongeEventFactory.createSpawnEntityEventCustom(Sponge.getCauseStackManager().getCurrentCause(), entities);
        SpongeImpl.postEvent(event);
        return event.isCancelled() && EntityUtil.processEntitySpawnsFromEvent(context, event);
    }



    public static boolean callPlayerChangeInventoryPickupPreEvent(final EntityPlayer player, final EntityItem itemToPickup, final int pickupDelay) {
        final ItemStack stack = itemToPickup.getItem();
        Sponge.getCauseStackManager().pushCause(player);
        final ItemStackSnapshot snapshot = ItemStackUtil.snapshotOf(stack);
        final ChangeInventoryEvent.Pickup.Pre event =
                SpongeEventFactory.createChangeInventoryEventPickupPre(Sponge.getCauseStackManager().getCurrentCause(),
                        Optional.empty(), Collections.singletonList(snapshot), snapshot, ((Item) itemToPickup),
                        ((Inventory) player.inventory));
        SpongeImpl.postEvent(event);
        Sponge.getCauseStackManager().popCause();
        if (event.isCancelled()) {
            return false;
        }
        if (event.getCustom().isPresent()) {
            final List<ItemStackSnapshot> list = event.getCustom().get();
            if (list.isEmpty()) {
                itemToPickup.getItem().setCount(0);
                return false;
            }

            boolean fullTransfer = true;
            final IMixinInventoryPlayer capture = (IMixinInventoryPlayer) player.inventory;
            capture.setCapture(true);
            for (final ItemStackSnapshot item : list) {
                final org.spongepowered.api.item.inventory.ItemStack itemStack = item.createStack();
                player.inventory.addItemStackToInventory(ItemStackUtil.toNative(itemStack));
                if (!itemStack.isEmpty()) {
                    fullTransfer = false;
                    break;
                }

            }
            capture.setCapture(false);
            if (!fullTransfer) {
                for (final SlotTransaction trans : capture.getCapturedTransactions()) {
                    trans.getSlot().set(trans.getOriginal().createStack());
                }
                return false;
            }
            if (!callPlayerChangeInventoryPickupEvent(player, capture)) {
                return false;
            }
            itemToPickup.getItem().setCount(0);
        }
        return true;
    }

    public static boolean callPlayerChangeInventoryPickupEvent(final EntityPlayer player, final IMixinInventoryPlayer inventory) {
        if (inventory.getCapturedTransactions().isEmpty()) {
            return true;
        }
        Sponge.getCauseStackManager().pushCause(player);
        final ChangeInventoryEvent.Pickup event = SpongeEventFactory.createChangeInventoryEventPickup(Sponge.getCauseStackManager().getCurrentCause(), (Inventory) player.inventoryContainer,
                inventory.getCapturedTransactions());
        SpongeImpl.postEvent(event);
        Sponge.getCauseStackManager().popCause();
        applyTransactions(event);
        inventory.getCapturedTransactions().clear();
        return !event.isCancelled();
    }

    public static ItemStack callInventoryPickupEvent(final IInventory inventory, final EntityItem item, final ItemStack stack) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(inventory);

            final ItemStackSnapshot snapshot = ItemStackUtil.snapshotOf(stack);
            final ChangeInventoryEvent.Pickup.Pre event =
                    SpongeEventFactory.createChangeInventoryEventPickupPre(frame.getCurrentCause(),
                            Optional.empty(), Collections.singletonList(snapshot), snapshot, ((Item) item),
                            ((Inventory) inventory));
            SpongeImpl.postEvent(event);
            if (event.isCancelled()) {
                return stack;
            }

            final int size = inventory.getSizeInventory();
            final ItemStack[] prevInventory = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                prevInventory[i] = inventory.getStackInSlot(i);
            }

            if (event.getCustom().isPresent()) {
                if (event.getCustom().get().isEmpty()) {
                    return ItemStack.EMPTY;
                }

                boolean fullTransfer = true;
                for (final ItemStackSnapshot snap : event.getCustom().get()) {
                    final ItemStack stackToAdd = ItemStackUtil.fromSnapshotToNative(snap);
                    final ItemStack remaining = TileEntityHopper.putStackInInventoryAllSlots(null, inventory, stackToAdd, null);
                    if (!remaining.isEmpty()) {
                        fullTransfer = false;
                        break;
                    }
                }
                if (!fullTransfer) {
                    for (int i = 0; i < prevInventory.length; i++) {
                        inventory.setInventorySlotContents(i, prevInventory[i]);
                    }
                    return stack;
                }

                if (callInventoryPickupEvent(inventory, prevInventory)) {
                    return ItemStack.EMPTY;
                }
                return stack;
            } else {
                final ItemStack remainder = TileEntityHopper.putStackInInventoryAllSlots(null, inventory, stack, null);
                if (callInventoryPickupEvent(inventory, prevInventory)) {
                    return remainder;
                }
                return stack;
            }
        }
    }

    private static List<SlotTransaction> generateTransactions(@Nullable final Inventory inv, final IInventory inventory, final ItemStack[] previous) {
        if (inv == null) {
            return Collections.emptyList();
        }
        final List<SlotTransaction> trans = new ArrayList<>();
        final Iterator<Inventory> it = inv.slots().iterator();
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            final org.spongepowered.api.item.inventory.Slot slot = (org.spongepowered.api.item.inventory.Slot) it.next();
            final ItemStack newStack = inventory.getStackInSlot(i);
            final ItemStack prevStack = previous[i];
            if (!ItemStack.areItemStacksEqual(newStack, prevStack)) {
                trans.add(new SlotTransaction(slot, ItemStackUtil.snapshotOf(prevStack), ItemStackUtil.snapshotOf(newStack)));
            }
        }
        return trans;
    }

    public static boolean callInventoryPickupEvent(final IInventory inventory, final ItemStack[] prevInventory) {
        final Inventory spongeInventory = InventoryUtil.toInventory(inventory, null);
        final List<SlotTransaction> trans = generateTransactions(spongeInventory, inventory, prevInventory);
        if (trans.isEmpty()) {
            return true;
        }
        final ChangeInventoryEvent.Pickup event = SpongeEventFactory.createChangeInventoryEventPickup(Sponge.getCauseStackManager().getCurrentCause(), spongeInventory, trans);
        SpongeImpl.postEvent(event);
        applyTransactions(event);
        return !event.isCancelled();
    }

    private static void applyTransactions(final ChangeInventoryEvent.Pickup event) {
        if (event.isCancelled()) {
            for (final SlotTransaction trans : event.getTransactions()) {
                trans.getSlot().set(trans.getOriginal().createStack());
            }
            return;
        }
        for (final SlotTransaction trans : event.getTransactions()) {
            if (!trans.isValid()) {
                trans.getSlot().set(trans.getOriginal().createStack());
            } else if (trans.getCustom().isPresent()) {
                trans.getSlot().set(trans.getFinal().createStack());
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static CollideEntityEvent callCollideEntityEvent(final net.minecraft.world.World world, @Nullable final net.minecraft.entity.Entity sourceEntity,
            final List<net.minecraft.entity.Entity> entities) {

        final PhaseTracker phaseTracker = PhaseTracker.getInstance();
        final PhaseContext<?> currentContext = phaseTracker.getCurrentContext();
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            if (sourceEntity != null) {
                // We only want to push the source entity if it's not the current entity being ticked or "sourced". They will be already pushed.
                if (currentContext.getSource() != sourceEntity) {
                    frame.pushCause(sourceEntity);
                }
            } else {
                // If there is no source, then... well... find one and push it.
                final Object source = currentContext.getSource();
                if (source instanceof LocatableBlock) {
                    frame.pushCause(source);
                } else if (source instanceof TileEntity) {
                    frame.pushCause(source);
                } else if (source instanceof Entity) {
                    frame.pushCause(source);
                }
            }
            currentContext.addNotifierAndOwnerToCauseStack(frame);

            final List<Entity> spEntities = (List<Entity>) (List<?>) entities;
            final CollideEntityEvent event =
                    SpongeEventFactory.createCollideEntityEvent(Sponge.getCauseStackManager().getCurrentCause(), spEntities);
            SpongeImpl.postEvent(event);
            return event;
        }
    }

    public static ChangeBlockEvent.Pre callChangeBlockEventPre(final ServerWorldBridge worldIn, final BlockPos pos) {
        return callChangeBlockEventPre(worldIn, ImmutableList.of(new Location<>((World) worldIn, pos.getX(), pos.getY(), pos.getZ())), null);
    }

    public static ChangeBlockEvent.Pre callChangeBlockEventPre(final ServerWorldBridge worldIn, final BlockPos pos, final Object source) {
        return callChangeBlockEventPre(worldIn, ImmutableList.of(new Location<>((World) worldIn, pos.getX(), pos.getY(), pos.getZ())), source);
    }


    /**
     * Processes pre block event data then fires event.
     *
     * @param worldIn The world
     * @param locations The locations affected
     * @param source The source of event
     * @return The event
     */
    private static ChangeBlockEvent.Pre callChangeBlockEventPre(final ServerWorldBridge worldIn, final ImmutableList<Location<World>> locations, @Nullable Object source) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            final PhaseContext<?> phaseContext = PhaseTracker.getInstance().getCurrentContext();
            if (source == null) {
                source = phaseContext.getSource() == null ? worldIn : phaseContext.getSource();
            }

            // TODO - All of this bit should be nuked since PhaseContext has lazy initializing frames.
            EntityPlayer player = null;
            frame.pushCause(source);
            if (source instanceof Player) {
                player = (EntityPlayer) source;
                if (SpongeImplHooks.isFakePlayer(player)) {
                    frame.addContext(EventContextKeys.FAKE_PLAYER, (Player) player);
                }
            }

            final User owner = phaseContext.getOwner().orElse((User) player);
            if (owner != null) {
                frame.addContext(EventContextKeys.OWNER, owner);
            }

            phaseContext.applyNotifierIfAvailable(notifier -> frame.addContext(EventContextKeys.NOTIFIER, notifier));

            final ChangeBlockEvent.Pre event =
                SpongeEventFactory.createChangeBlockEventPre(frame.getCurrentCause(), locations);
            SpongeImpl.postEvent(event);
            return event;
        }
    }

    public static ChangeBlockEvent.Modify callChangeBlockEventModifyLiquidMix(
        final net.minecraft.world.World worldIn, final BlockPos pos, final IBlockState state, @Nullable Object source) {

        final BlockState fromState = (BlockState) worldIn.getBlockState(pos);
        final BlockState toState = (BlockState) state;
        boolean pushSource = false;
        if (source == null) {
            // If source is null the source is the block itself
            pushSource = true;
            source = new SpongeLocatableBlockBuilder().state(fromState).world((World) worldIn).position(pos.getX(), pos.getY(), pos.getZ()).build();
        }
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            if (!pushSource) {
                frame.pushCause(source);
            }
            frame.addContext(EventContextKeys.LIQUID_MIX, (World) worldIn);

            final WorldProperties world = ((World) worldIn).getProperties();
            final Vector3i position = new Vector3i(pos.getX(), pos.getY(), pos.getZ());

            final Transaction<BlockSnapshot> transaction = new Transaction<>(BlockSnapshot.builder().blockState(fromState).world(world).position(position).build(),
                                                                       BlockSnapshot.builder().blockState(toState).world(world).position(position).build());
            final ChangeBlockEvent.Modify event = SpongeEventFactory.createChangeBlockEventModify(frame.getCurrentCause(),
                    Collections.singletonList(transaction));

            SpongeImpl.postEvent(event);
            return event;
        }
    }

    public static ChangeBlockEvent.Break callChangeBlockEventModifyLiquidBreak(
        final net.minecraft.world.World worldIn, final BlockPos pos, final IBlockState targetState) {
        return callChangeBlockEventModifyLiquidBreak(worldIn, pos, worldIn.getBlockState(pos), targetState);
    }

    public static ChangeBlockEvent.Break callChangeBlockEventModifyLiquidBreak(
        final net.minecraft.world.World worldIn, final BlockPos pos, final IBlockState fromState, final IBlockState toState) {
        final PhaseContext<?> context = PhaseTracker.getInstance().getCurrentContext();
        Object source =context.getSource(LocatableBlock.class).orElse(null);
        if (source == null) {
            source = worldIn; // Fallback
        }
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(source);
            frame.addContext(EventContextKeys.LIQUID_BREAK, (World) worldIn);

            final WorldProperties world = ((World) worldIn).getProperties();
            final Vector3i position = new Vector3i(pos.getX(), pos.getY(), pos.getZ());

            final SpongeBlockSnapshot from = new SpongeBlockSnapshotBuilder().blockState(fromState).world(world).position(position).build();
            final SpongeBlockSnapshot to = new SpongeBlockSnapshotBuilder().blockState(toState).world(world).position(position).build();
            final Transaction<BlockSnapshot> transaction = new Transaction<>(from, to);
            final ChangeBlockEvent.Break event = SpongeEventFactory.createChangeBlockEventBreak(frame.getCurrentCause(),
                Collections.singletonList(transaction));

            SpongeImpl.postEvent(event);
            return event;
        }
    }


    /**
     * This simulates the blocks a piston moves and calls the event for saner
     * debugging.
     *
     * @return if the event was cancelled
     */
    public static boolean handlePistonEvent(
        final ServerWorldBridge world, final WorldServer.ServerBlockEventList list, final Object obj, final BlockPos pos, final Block blockIn,
            final int eventId, final int eventParam) {
        final boolean extending = (eventId == 0);
        final IBlockState blockstate = ((net.minecraft.world.World) world).getBlockState(pos);
        final EnumFacing direction = blockstate.getValue(BlockDirectional.FACING);
        final LocatableBlock locatable = new SpongeLocatableBlockBuilder().world((World) world).state((BlockState) blockstate).position(pos.getX(), pos.getY(), pos.getZ()).build();

        // Sets toss out duplicate values (even though there shouldn't be any)
        final HashSet<Location<org.spongepowered.api.world.World>> locations = new HashSet<>();
        locations.add(new Location<>((World) world, pos.getX(), pos.getY(), pos.getZ()));

        final BlockPistonStructureHelper movedBlocks = new BlockPistonStructureHelper((WorldServer) world, pos, direction, extending);
        movedBlocks.canMove(); // calculates blocks to be moved

        Stream.concat(movedBlocks.getBlocksToMove().stream(), movedBlocks.getBlocksToDestroy().stream())
                .map(block -> new Location<>((World) world, block.getX(), block.getY(), block.getZ()))
                .collect(Collectors.toCollection(() -> locations)); // SUPER
                                                                    // efficient
                                                                    // code!

        // If the piston is extending and there are no blocks to destroy, add the offset location for protection purposes
        if (extending && movedBlocks.getBlocksToDestroy().isEmpty()) {
            final List<BlockPos> movedPositions = movedBlocks.getBlocksToMove();
            final BlockPos offsetPos;
            // If there are no blocks to move, add the offset of piston
            if (movedPositions.isEmpty()) {
                offsetPos = pos.offset(direction);
            } else {
                // Add the offset of last block set to move
                offsetPos = movedPositions.get(movedPositions.size() - 1).offset(direction);
            }
            locations.add(new Location<>((World) world, offsetPos.getX(), offsetPos.getY(), offsetPos.getZ()));
        }

        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            if (extending) {
                frame.addContext(EventContextKeys.PISTON_EXTEND, (World) world);
            } else {
                frame.addContext(EventContextKeys.PISTON_RETRACT, (World) world);
            }
            return SpongeCommonEventFactory.callChangeBlockEventPre(world, ImmutableList.copyOf(locations), locatable)
                .isCancelled();
        }
    }

    @SuppressWarnings("rawtypes")
    @Nullable
    public static NotifyNeighborBlockEvent callNotifyNeighborEvent(final World world, final BlockPos sourcePos, final EnumSet<EnumFacing> notifiedSides) {
        final PhaseContext<?> context = PhaseTracker.getInstance().getCurrentContext();
        // Don't fire notify events during world gen or while restoring
        if (context.state.isWorldGeneration() || context.state.isRestoring()) {
            return null;
        }
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            final BlockState blockstate = (BlockState) ((net.minecraft.world.World) world).getBlockState(sourcePos);
            final LocatableBlock locatable = new SpongeLocatableBlockBuilder().world(world).position(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ())
                    .state(blockstate)
                    .build();
            if (context.getNotifier().isPresent()) {
                context.addNotifierAndOwnerToCauseStack(frame);
            } else {

                final ChunkBridge mixinChunk = (ChunkBridge) ((WorldServer) world).getChunk(sourcePos);
                mixinChunk.getBlockNotifier(sourcePos).ifPresent(user -> frame.addContext(EventContextKeys.NOTIFIER, user));
                mixinChunk.getBlockOwner(sourcePos).ifPresent(owner -> frame.addContext(EventContextKeys.OWNER, owner));
            }
            Sponge.getCauseStackManager().pushCause(locatable);

            final Map<Direction, BlockState> neighbors = new HashMap<>();
            for (final EnumFacing notificationSide : notifiedSides) {
                final BlockPos offset = sourcePos.offset(notificationSide);
                final Direction direction = DirectionFacingProvider.getInstance().getKey(notificationSide).get();
                final IBlockState notificationState = ((WorldServer) world).getBlockState(offset);
                neighbors.put(direction, (BlockState) notificationState);
            }

            final NotifyNeighborBlockEvent event =
                    SpongeEventFactory.createNotifyNeighborBlockEvent(Sponge.getCauseStackManager().getCurrentCause(), neighbors, neighbors);
            SpongeImpl.postEvent(event);
            return event;
        }
    }

    public static InteractEntityEvent.Primary callInteractEntityEventPrimary(final EntityPlayerMP player, final ItemStack stack, final net.minecraft.entity.Entity entity, final EnumHand
            hand, @Nullable final Vector3d hitVec) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(player);
            frame.addContext(EventContextKeys.OWNER, (User) player);
            frame.addContext(EventContextKeys.NOTIFIER, (User) player);
            frame.addContext(EventContextKeys.ENTITY_HIT, ((Entity) entity));
            if (!stack.isEmpty()) {
                frame.addContext(EventContextKeys.USED_ITEM, ItemStackUtil.snapshotOf(stack));
            }
            final InteractEntityEvent.Primary event;
            if (hand == EnumHand.MAIN_HAND) {
                event = SpongeEventFactory.createInteractEntityEventPrimaryMainHand(
                        frame.getCurrentCause(), HandTypes.MAIN_HAND, Optional.ofNullable(hitVec), (Entity) entity);
            } else {
                event = SpongeEventFactory.createInteractEntityEventPrimaryOffHand(
                        frame.getCurrentCause(), HandTypes.OFF_HAND, Optional.ofNullable(hitVec), (Entity) entity);
            }
            if (entity instanceof Player && !((World) player.world).getProperties().isPVPEnabled()) {
                event.setCancelled(true); // if PvP is disabled for world, cancel
            }
            SpongeImpl.postEvent(event);
            return event;
        }
    }

    public static InteractEntityEvent.Secondary callInteractEntityEventSecondary(final EntityPlayerMP player, final ItemStack stack, final net.minecraft.entity.Entity entity,
            final EnumHand hand, @Nullable final Vector3d hitVec) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(player);
            frame.addContext(EventContextKeys.OWNER, (User) player);
            frame.addContext(EventContextKeys.NOTIFIER, (User) player);
            frame.addContext(EventContextKeys.ENTITY_HIT, (Entity) entity);
            if (!stack.isEmpty()) {
                frame.addContext(EventContextKeys.USED_ITEM, ItemStackUtil.snapshotOf(stack));
            }
            final InteractEntityEvent.Secondary event;
            if (hand == EnumHand.MAIN_HAND) {
                event = SpongeEventFactory.createInteractEntityEventSecondaryMainHand(
                        frame.getCurrentCause(), HandTypes.MAIN_HAND, Optional.ofNullable(hitVec), (Entity) entity);
            } else {
                event = SpongeEventFactory.createInteractEntityEventSecondaryOffHand(
                        frame.getCurrentCause(), HandTypes.OFF_HAND, Optional.ofNullable(hitVec), (Entity) entity);
            }
            SpongeImpl.postEvent(event);
            return event;
        }
    }

    public static InteractItemEvent.Primary callInteractItemEventPrimary(final EntityPlayer player, final ItemStack stack, final EnumHand hand,
        @Nullable final Vector3d hitVec, final Object hitTarget) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(player);
            frame.addContext(EventContextKeys.OWNER, (User) player);
            frame.addContext(EventContextKeys.NOTIFIER, (User) player);
            if (hitTarget instanceof Entity) {
                frame.addContext(EventContextKeys.ENTITY_HIT, ((Entity) hitTarget));
            } else if (hitTarget instanceof BlockSnapshot) {
                frame.addContext(EventContextKeys.BLOCK_HIT, (BlockSnapshot) hitTarget);
            }
            if (!stack.isEmpty()) {
                frame.addContext(EventContextKeys.USED_ITEM, ItemStackUtil.snapshotOf(stack));
            }
            final HandType handType = (HandType) (Object) hand;
            frame.addContext(EventContextKeys.USED_HAND, handType);
            final InteractItemEvent.Primary event;
            if (hand == EnumHand.MAIN_HAND) {
                event = SpongeEventFactory.createInteractItemEventPrimaryMainHand(frame.getCurrentCause(),
                        HandTypes.MAIN_HAND, Optional.ofNullable(hitVec), ItemStackUtil.snapshotOf(stack));
            } else {
                event = SpongeEventFactory.createInteractItemEventPrimaryOffHand(frame.getCurrentCause(),
                        HandTypes.OFF_HAND, Optional.ofNullable(hitVec), ItemStackUtil.snapshotOf(stack));
            }
            SpongeImpl.postEvent(event);
            return event;
        }
    }

    public static InteractItemEvent.Secondary callInteractItemEventSecondary(final CauseStackManager.StackFrame frame, final EntityPlayer player,
        final ItemStack stack, final EnumHand hand,
        @Nullable final Vector3d hitVec, final Object hitTarget) {
        frame.pushCause(player);
        frame.addContext(EventContextKeys.OWNER, (User) player);
        frame.addContext(EventContextKeys.NOTIFIER, (User) player);
        if (hitTarget instanceof Entity) {
            frame.addContext(EventContextKeys.ENTITY_HIT, ((Entity) hitTarget));
        } else if (hitTarget instanceof BlockSnapshot) {
            frame.addContext(EventContextKeys.BLOCK_HIT, (BlockSnapshot) hitTarget);
        }
        final ItemStackSnapshot snapshot = ItemStackUtil.snapshotOf(stack);
        if (!stack.isEmpty()) {
            frame.addContext(EventContextKeys.USED_ITEM, snapshot);
        }
        final HandType handType = (HandType) (Object) hand;
        frame.addContext(EventContextKeys.USED_HAND, handType);
        final InteractItemEvent.Secondary event;
        if (hand == EnumHand.MAIN_HAND) {
            event = SpongeEventFactory.createInteractItemEventSecondaryMainHand(frame.getCurrentCause(), HandTypes.MAIN_HAND, Optional.ofNullable(hitVec), snapshot);
        } else {
            event = SpongeEventFactory.createInteractItemEventSecondaryOffHand(frame.getCurrentCause(), HandTypes.OFF_HAND, Optional.ofNullable(hitVec), snapshot);
        }
        SpongeImpl.postEvent(event);
        return event;

    }

    public static InteractBlockEvent.Primary callInteractBlockEventPrimary(
        final EntityPlayer player, final ItemStack heldItem, final EnumHand hand, @Nullable final Vector3d hitVec) {
        return callInteractBlockEventPrimary(player, heldItem, BlockSnapshot.NONE, hand, null, hitVec);
    }

    public static InteractBlockEvent.Primary callInteractBlockEventPrimary(final EntityPlayer player, final ItemStack heldItem, final BlockSnapshot blockSnapshot, final EnumHand hand,
            @Nullable final EnumFacing side, @Nullable final Vector3d hitVec) {
        final HandType handType = (HandType) (Object) hand;
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(player);
            frame.addContext(EventContextKeys.OWNER, (User) player);
            frame.addContext(EventContextKeys.NOTIFIER, (User) player);
            frame.addContext(EventContextKeys.BLOCK_HIT, blockSnapshot);
            frame.addContext(EventContextKeys.USED_HAND, handType);
            final InteractBlockEvent.Primary event;
            final Direction direction;
            if (side != null) {
                direction = DirectionFacingProvider.getInstance().getKey(side).get();
            } else {
                direction = Direction.NONE;
            }
            if (!heldItem.isEmpty()) {
                frame.addContext(EventContextKeys.USED_ITEM, ItemStackUtil.snapshotOf(heldItem));
            }
            if (hand == EnumHand.MAIN_HAND) {
                event = SpongeEventFactory.createInteractBlockEventPrimaryMainHand(frame.getCurrentCause(), handType,
                        Optional.ofNullable(hitVec), blockSnapshot, direction);
            } else {
                event = SpongeEventFactory.createInteractBlockEventPrimaryOffHand(frame.getCurrentCause(), handType,
                        Optional.ofNullable(hitVec), blockSnapshot, direction);
            }
            SpongeImpl.postEvent(event);
            return event;
        }
    }

    public static InteractBlockEvent.Secondary createInteractBlockEventSecondary(
        final EntityPlayer player, final ItemStack heldItem, @Nullable final Vector3d hitVec,
            final BlockSnapshot targetBlock, final Direction targetSide, final EnumHand hand) {
        return createInteractBlockEventSecondary(player, heldItem, Tristate.UNDEFINED, Tristate.UNDEFINED, Tristate.UNDEFINED, Tristate.UNDEFINED,
                hitVec, targetBlock, targetSide, hand);
    }

    public static InteractBlockEvent.Secondary createInteractBlockEventSecondary(final EntityPlayer player, final ItemStack heldItem, final Tristate originalUseBlockResult, final Tristate useBlockResult,
            final Tristate originalUseItemResult, final Tristate useItemResult, @Nullable final Vector3d hitVec, final BlockSnapshot targetBlock,
            final Direction targetSide, final EnumHand hand) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(player);
            frame.addContext(EventContextKeys.OWNER, (User) player);
            frame.addContext(EventContextKeys.NOTIFIER, (User) player);
            frame.addContext(EventContextKeys.BLOCK_HIT, targetBlock);
            if (!heldItem.isEmpty()) {
                frame.addContext(EventContextKeys.USED_ITEM, ItemStackUtil.snapshotOf(heldItem));
            }
            final HandType handType = (HandType) (Object) hand;
            frame.addContext(EventContextKeys.USED_HAND, handType);
            final InteractBlockEvent.Secondary event;
            if (hand == EnumHand.MAIN_HAND) {
                event = SpongeEventFactory.createInteractBlockEventSecondaryMainHand(frame.getCurrentCause(),
                        originalUseBlockResult, useBlockResult, originalUseItemResult, useItemResult, HandTypes.MAIN_HAND, Optional.ofNullable
                                (hitVec), targetBlock, targetSide);
            } else {
                event = SpongeEventFactory.createInteractBlockEventSecondaryOffHand(frame.getCurrentCause(),
                        originalUseBlockResult, useBlockResult, originalUseItemResult, useItemResult, HandTypes.OFF_HAND, Optional.ofNullable
                                (hitVec), targetBlock, targetSide);
            }
            return event;
        }
    }

    @Nullable
    public static Event callMoveEntityEvent(final net.minecraft.entity.Entity entity,
        final EntityTickContext context) {
        // Ignore movement event if entity is dead, a projectile, or item.
        // Note: Projectiles are handled with CollideBlockEvent.Impact
        if (entity.isDead || entity instanceof IProjectile || entity instanceof EntityItem) {
            return null;
        }

        final Entity spongeEntity = (Entity) entity;
        final double deltaX = context.prevX - entity.posX;
        final double deltaY = context.prevY - entity.posY;
        final double deltaZ = context.prevZ - entity.posZ;
        final double deltaChange = Math.pow(deltaX, 2) + Math.pow(deltaY, 2) + Math.pow(deltaZ, 2);


        if (deltaChange > 1f / 256 // Micro-optimization, avoids almost negligible position movement from floating point differences.
            || entity.rotationPitch != entity.prevRotationPitch
            || entity.rotationYaw != entity.prevRotationYaw) {
            try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                frame.pushCause(entity);
                // yes we have a move event.
                final double currentPosX = entity.posX;
                final double currentPosY = entity.posY;
                final double currentPosZ = entity.posZ;

                final Vector3d oldPositionVector = new Vector3d(context.prevX, context.prevY, context.prevZ);
                final Vector3d currentPositionVector = new Vector3d(currentPosX, currentPosY, currentPosZ);

                final Vector3d oldRotationVector = new Vector3d(entity.prevRotationPitch, entity.prevRotationYaw, 0);
                final Vector3d currentRotationVector = new Vector3d(entity.rotationPitch, entity.rotationYaw, 0);

                final World world = spongeEntity.getWorld();

                final Transform<World> oldTransform = new Transform<>(world, oldPositionVector, oldRotationVector, spongeEntity.getScale());
                final Transform<World> newTransform = new Transform<>(world, currentPositionVector, currentRotationVector, spongeEntity.getScale());
                Event event  = null;
                Transform<World> eventToTransform = null;
                if (!oldPositionVector.equals(currentPositionVector)) {
                    event = SpongeEventFactory.createMoveEntityEventPosition(frame.getCurrentCause(), oldTransform, newTransform, spongeEntity);
                    eventToTransform = ((MoveEntityEvent) event).getToTransform();
                } else {
                    event = SpongeEventFactory.createRotateEntityEvent(frame.getCurrentCause(), oldTransform, newTransform, spongeEntity);
                    eventToTransform = ((RotateEntityEvent) event).getToTransform();
                }

                if (SpongeImpl.postEvent(event)) { // Cancelled event, reset positions to previous position.
                    entity.posX = context.prevX;
                    entity.posY = context.prevY;
                    entity.posZ = context.prevZ;
                    entity.rotationPitch = entity.prevRotationPitch;
                    entity.rotationYaw = entity.prevRotationYaw;
                } else {
                    final Vector3d newPosition = eventToTransform.getPosition();
                    if (!newPosition.equals(currentPositionVector)) {
                        entity.posX = newPosition.getX();
                        entity.posY = newPosition.getY();
                        entity.posZ = newPosition.getZ();
                    }
                    if (!eventToTransform.getRotation().equals(currentRotationVector)) {
                        entity.rotationPitch = (float) currentRotationVector.getX();
                        entity.rotationYaw = (float) currentRotationVector.getY();
                    }
                }
                return event;
            }
        }

        return null;
    }
    public static Optional<DestructEntityEvent.Death> callDestructEntityEventDeath(final EntityLivingBase entity, @Nullable final DamageSource source, final boolean isMainThread) {
        final MessageEvent.MessageFormatter formatter = new MessageEvent.MessageFormatter();
        final MessageChannel originalChannel;
        final MessageChannel channel;
        final Text originalMessage;
        Optional<User> sourceCreator = Optional.empty();
        final boolean messageCancelled = false;

        if (entity instanceof EntityPlayerMP) {
            originalChannel = channel = ((ServerPlayerEntityBridge) entity).getDeathMessageChannel();
        } else {
            originalChannel = MessageChannel.TO_NONE;
            channel = MessageChannel.TO_NONE;
        }
        if (source instanceof EntityDamageSource) {
            final EntityDamageSource damageSource = (EntityDamageSource) source;
            if (damageSource.getImmediateSource() instanceof OwnershipTrackedBridge) {
                final OwnershipTrackedBridge ownerBridge = (OwnershipTrackedBridge) damageSource.getImmediateSource();
                if (ownerBridge != null) {
                    sourceCreator = ownerBridge.tracked$getOwnerReference();
                }
            }
        }

        originalMessage = SpongeTexts.toText(entity.getCombatTracker().getDeathMessage());
        formatter.getBody().add(new MessageEvent.DefaultBodyApplier(originalMessage));
        // Try-with-resources will not produce an NPE when trying to autoclose the frame if it is null. Client sided
        // checks need to be made here since entities can die on the client world.
        try (final CauseStackManager.StackFrame frame = isMainThread ? Sponge.getCauseStackManager().pushCauseFrame() : null) {
            if (isMainThread) {
                if (source != null) {
                    frame.pushCause(source);
                }
                if (sourceCreator.isPresent()) {
                    frame.addContext(EventContextKeys.OWNER, sourceCreator.get());
                }
            }

            final Cause cause = isMainThread ? Sponge.getCauseStackManager().getCurrentCause() : Cause.of(EventContext.empty(), source == null ? entity : source);
            final DestructEntityEvent.Death event = SpongeEventFactory.createDestructEntityEventDeath(cause,
                originalChannel, Optional.of(channel), formatter,
                (Living) entity, entity.world.getGameRules().getBoolean("keepInventory"), messageCancelled);
            SpongeImpl.postEvent(event, true); // Client code should be able to cancel the death event if server cancels it.
            final Text message = event.getMessage();
            // Check the event isn't cancelled either. If it is, then don't spawn the message.
            if (!event.isCancelled() && !event.isMessageCancelled() && !message.isEmpty()) {
                event.getChannel().ifPresent(eventChannel -> eventChannel.send(entity, event.getMessage()));
            }
            return Optional.of(event);
        }
    }

    public static boolean handleCollideBlockEvent(final Block block, final net.minecraft.world.World world, final BlockPos pos, final IBlockState state, final net.minecraft.entity.Entity entity, final Direction direction) {
        if (pos.getY() <= 0) {
            return false;
        }

        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause( entity);

            if (entity instanceof OwnershipTrackedBridge) {
                final OwnershipTrackedBridge spongeEntity = (OwnershipTrackedBridge) entity;
                spongeEntity.tracked$getOwnerReference().ifPresent(user -> frame.addContext(EventContextKeys.OWNER, user));
            }

            // TODO: Add target side support
            final CollideBlockEvent event = SpongeEventFactory.createCollideBlockEvent(frame.getCurrentCause(), (BlockState) state,
                    new Location<>((World) world, VecHelper.toVector3d(pos)), direction);
            final boolean cancelled = SpongeImpl.postEvent(event);
            if (!cancelled) {
                final EntityBridge spongeEntity = (EntityBridge) entity;
                if (!pos.equals(spongeEntity.getLastCollidedBlockPos())) {
                    final PhaseContext<?> context = PhaseTracker.getInstance().getCurrentContext();
                    context.applyNotifierIfAvailable(notifier -> {
                        ChunkBridge spongeChunk = ((ActiveChunkReferantBridge) entity).bridge$getActiveChunk();
                        if (spongeChunk == null) {
                            spongeChunk = (ChunkBridge) world.getChunk(pos);
                        }
                        spongeChunk.addTrackedBlockPosition(block, pos, notifier, PlayerTracker.Type.NOTIFIER);

                    });
                }
            }
            return cancelled;
        }
    }

    public static boolean handleCollideImpactEvent(final net.minecraft.entity.Entity projectile, @Nullable final ProjectileSource projectileSource,
            final RayTraceResult movingObjectPosition) {
        final RayTraceResult.Type movingObjectType = movingObjectPosition.typeOfHit;
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(projectile);
            frame.addContext(EventContextKeys.PROJECTILE_SOURCE, projectileSource == null
                    ? ProjectileSource.UNKNOWN
                    : projectileSource);
            final Optional<User> owner = PhaseTracker.getInstance().getCurrentContext().getOwner();
            owner.ifPresent(user -> frame.addContext(EventContextKeys.OWNER, user));

            final Location<World> impactPoint = new Location<>((World) projectile.world, VecHelper.toVector3d(movingObjectPosition.hitVec));
            boolean cancelled = false;

            if (movingObjectType == RayTraceResult.Type.BLOCK) {
                final BlockPos blockPos = movingObjectPosition.getBlockPos();
                if (blockPos.getY() <= 0) {
                    return false;
                }

                final BlockSnapshot targetBlock = ((World) projectile.world).createSnapshot(VecHelper.toVector3i(movingObjectPosition.getBlockPos()));
                Direction side = Direction.NONE;
                if (movingObjectPosition.sideHit != null) {
                    side = DirectionFacingProvider.getInstance().getKey(movingObjectPosition.sideHit).get();
                }

                final CollideBlockEvent.Impact event = SpongeEventFactory.createCollideBlockEventImpact(frame.getCurrentCause(),
                        impactPoint, targetBlock.getState(),
                        targetBlock.getLocation().get(), side);
                cancelled = SpongeImpl.postEvent(event);
                // Track impact block if event is not cancelled
                if (!cancelled && owner.isPresent()) {
                    final BlockPos targetPos = VecHelper.toBlockPos(impactPoint.getBlockPosition());
                    final ChunkBridge spongeChunk = (ChunkBridge) projectile.world.getChunk(targetPos);
                    spongeChunk.addTrackedBlockPosition((Block) targetBlock.getState().getType(), targetPos, owner.get(), PlayerTracker.Type.NOTIFIER);
                }
            } else if (movingObjectPosition.entityHit != null) { // entity
                final ArrayList<Entity> entityList = new ArrayList<>();
                entityList.add((Entity) movingObjectPosition.entityHit);
                final CollideEntityEvent.Impact event = SpongeEventFactory.createCollideEntityEventImpact(frame.getCurrentCause(), entityList, impactPoint);
                        cancelled = SpongeImpl.postEvent(event);
            }

            if (cancelled) {
                // Entities such as EnderPearls call setDead during onImpact. However, if the event is cancelled
                // setDead will never be called resulting in a bad state such as falling through world.
                projectile.setDead();
            }
            return cancelled;
        }
    }

    public static ClickInventoryEvent.Creative callCreativeClickInventoryEvent(final EntityPlayerMP player, final CPacketCreativeInventoryAction packetIn) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(player);
            // Creative doesn't inform server of cursor status so there is no way of knowing what the final stack is
            // Due to this, we can only send the original item that was clicked in slot
            final Transaction<ItemStackSnapshot> cursorTransaction = new Transaction<>(ItemStackSnapshot.NONE, ItemStackSnapshot.NONE);
            org.spongepowered.api.item.inventory.Slot slot = null;
            if (((ContainerBridge) player.openContainer).bridge$getCapturedSlotTransactions().isEmpty() && packetIn.getSlotId() >= 0
                && packetIn.getSlotId() < player.openContainer.inventorySlots.size()) {
                slot = ((ContainerBridge)player.openContainer).getContainerSlot(packetIn.getSlotId());
                if (slot != null) {
                    final ItemStackSnapshot clickedItem = ItemStackUtil.snapshotOf(slot.peek().orElse(org.spongepowered.api.item.inventory.ItemStack.empty()));
                    final ItemStackSnapshot replacement = ItemStackUtil.snapshotOf(packetIn.getStack());
                    final SlotTransaction slotTransaction = new SlotTransaction(slot, clickedItem, replacement);
                    ((ContainerBridge) player.openContainer).bridge$getCapturedSlotTransactions().add(slotTransaction);
                }
            }
            final ClickInventoryEvent.Creative event =
                SpongeEventFactory.createClickInventoryEventCreative(frame.getCurrentCause(), cursorTransaction,
                    Optional.ofNullable(slot), (org.spongepowered.api.item.inventory.Container) player.openContainer,
                    new ArrayList<>(((ContainerBridge) player.openContainer).bridge$getCapturedSlotTransactions()));
            ((ContainerBridge) player.openContainer).bridge$getCapturedSlotTransactions().clear();
            ((ContainerBridge) player.openContainer).setCaptureInventory(false);
            SpongeImpl.postEvent(event);
            frame.popCause();
            return event;
        }
    }

    public static boolean callInteractInventoryOpenEvent(final EntityPlayerMP player) {
        final ItemStackSnapshot newCursor =
                player.inventory.getItemStack().isEmpty() ? ItemStackSnapshot.NONE
                        : ((org.spongepowered.api.item.inventory.ItemStack) player.inventory.getItemStack()).createSnapshot();
        final Transaction<ItemStackSnapshot> cursorTransaction = new Transaction<>(ItemStackSnapshot.NONE, newCursor);
        final InteractInventoryEvent.Open event =
                SpongeEventFactory.createInteractInventoryEventOpen(Sponge.getCauseStackManager().getCurrentCause(), cursorTransaction,
                        (org.spongepowered.api.item.inventory.Container) player.openContainer);
        SpongeImpl.postEvent(event);
        if (event.isCancelled()) {
            player.closeScreen();
            return false;
        }
        // TODO - determine if/how we want to fire inventory events outside of click packet handlers
        //((ContainerBridge) player.openContainer).setCaptureInventory(true);
        // Custom cursor
        if (event.getCursorTransaction().getCustom().isPresent()) {
            handleCustomCursor(player, event.getCursorTransaction().getFinal());
        }
        return true;
    }

    public static InteractInventoryEvent.Close callInteractInventoryCloseEvent(final Container container, final EntityPlayerMP player,
            final ItemStackSnapshot lastCursor, final ItemStackSnapshot newCursor, final boolean clientSource) {
        final Transaction<ItemStackSnapshot> cursorTransaction = new Transaction<>(lastCursor, newCursor);
        final InteractInventoryEvent.Close event =
                SpongeEventFactory.createInteractInventoryEventClose(Sponge.getCauseStackManager().getCurrentCause(), cursorTransaction, ContainerUtil.fromNative(container));
        SpongeImpl.postEvent(event);
        if (event.isCancelled()) {
            if (clientSource && container.getSlot(0) != null) {
                if (!(container instanceof ContainerPlayer)) {
                    // Inventory closed by client, reopen window and send
                    // container
                    player.openContainer = container;
                    final String guiId;
                    final Slot slot = container.getSlot(0);
                    final IInventory slotInventory = slot.inventory;
                    if (slotInventory instanceof IInteractionObject) {
                        guiId = ((IInteractionObject) slotInventory).getGuiID();
                    } else {
                        // expected fallback for unknown types
                        guiId = "minecraft:container";
                    }
                    slotInventory.openInventory(player);
                    player.connection.sendPacket(new SPacketOpenWindow(container.windowId, guiId, slotInventory
                            .getDisplayName(), slotInventory.getSizeInventory()));
                    // resync data to client
                    player.sendContainerToPlayer(container);
                } else {
                    // TODO: Maybe print a warning or throw an exception here?
                    // The player gui cannot be opened from the
                    // server so allowing this event to be cancellable when the
                    // GUI has been closed already would result
                    // in opening the wrong GUI window.
                }
            }
            // Handle cursor
            if (!event.getCursorTransaction().isValid()) {
                handleCustomCursor(player, event.getCursorTransaction().getOriginal());
            }
        } else {
            final ContainerBridge mixinContainer = (ContainerBridge) player.openContainer;
            mixinContainer.bridge$getCapturedSlotTransactions().clear();
            mixinContainer.setCaptureInventory(false);
            // Handle cursor
            if (!event.getCursorTransaction().isValid()) {
                handleCustomCursor(player, event.getCursorTransaction().getOriginal());
            } else if (event.getCursorTransaction().getCustom().isPresent()) {
                handleCustomCursor(player, event.getCursorTransaction().getFinal());
            }
            if (!clientSource && player.openContainer != null && player.connection != null) {
                player.closeScreen();
            }
        }

        return event;
    }

    @Nullable
    public static Container displayContainer(final EntityPlayerMP player, final Inventory inventory, final Text displayName) {
        final net.minecraft.inventory.Container previousContainer = player.openContainer;
        final net.minecraft.inventory.Container container;

        if (inventory instanceof CustomInventory) {
            if (!checkValidVanillaCustomInventory(((CustomInventory) inventory))) {
                return null; // Invalid size for vanilla inventory ; This is to
                             // prevent crashing the client with invalid data
            }
        }



        try {
            if (displayName != null) {
                ((ServerPlayerEntityBridge) player).setContainerDisplay(displayName);
            }
            if (inventory instanceof IInteractionObject) {
                final String guiId = ((IInteractionObject) inventory).getGuiID();

                switch (guiId) {
                    case "EntityHorse":
                        if (inventory instanceof CarriedInventory) {
                            final CarriedInventory<?> cinventory = (CarriedInventory<?>) inventory;
                            if (cinventory.getCarrier().isPresent() && cinventory.getCarrier().get() instanceof AbstractHorse) {
                                player.openGuiHorseInventory(((AbstractHorse) cinventory.getCarrier().get()), (IInventory) inventory);
                            }
                        }
                        break;
                    case "minecraft:chest":
                        player.displayGUIChest((IInventory) inventory);
                        break;
                    case "minecraft:crafting_table":
                    case "minecraft:anvil":
                    case "minecraft:enchanting_table":
                        player.displayGui((IInteractionObject) inventory);
                        break;
                    default:
                        player.displayGUIChest((IInventory) inventory);
                        break;
                }
            } else if (inventory instanceof IInventory) {
                player.displayGUIChest(((IInventory) inventory));
            } else {
                return null;
            }
        } finally {
            if (displayName != null) {
                ((ServerPlayerEntityBridge) player).setContainerDisplay(null);
            }
        }

        container = player.openContainer;

        if (previousContainer == container) {
            return null;
        }

        // We need to call ensureListenersRegistered both before and after
        // we fire the InteractInventoryEvent.Open event
        // Calling it before the event is fired ensures that we've registered
        // any listeners that we need to before the event is fired
        if (inventory instanceof CustomInventory) {
            ((CustomInventory) inventory).ensureListenersRegistered();
        }

        if (!callInteractInventoryOpenEvent(player)) {
            return null;
        }

        if (container instanceof ContainerBridge) {
            // This overwrites the normal container behaviour and allows viewing
            // inventories that are more than 8 blocks away
            // This currently actually only works for the Containers mixed into
            // by MixinContainerCanInteract ; but throws no errors for other
            // containers

            // Allow viewing inventory; except when dead
            ((ContainerBridge) container).setCanInteractWith(p -> !p.isDead);
        }

        // This call must go at the end of this method,
        // to ensure that it runs after any new viewers are added,
        // since the inventory can be closed/reopned by event listeners
        if (inventory instanceof CustomInventory) {
            ((CustomInventory) inventory).ensureListenersRegistered();
        }

        return container;
    }

    private static boolean checkValidVanillaCustomInventory(final CustomInventory inventory) {
        final InventoryArchetype archetype = inventory.getArchetype();
        if (InventoryArchetypes.CHEST.equals(archetype) || InventoryArchetypes.DOUBLE_CHEST.equals(archetype)) {
            final int size = inventory.getSizeInventory(); // Divisible by
            // 9 AND less than 6 rows of 9 slots
            return size % 9 == 0 && size / 9 <= 6 && size != 0;
        }
        if (InventoryArchetypes.HOPPER.equals(archetype)) {
            return inventory.getSizeInventory() == 5 * 1;
        }
        if (InventoryArchetypes.DISPENSER.equals(archetype)) {
            return inventory.getSizeInventory() == 3 * 3;
        }
        if (InventoryArchetypes.WORKBENCH.equals(archetype)) {
            return inventory.getSizeInventory() == 3 * 3 + 1;
        }
        if (InventoryArchetypes.FURNACE.equals(archetype)) {
            return inventory.getSizeInventory() == 3;
        }
        if (InventoryArchetypes.ENCHANTING_TABLE.equals(archetype)) {
            return inventory.getSizeInventory() == 2;
        }
        if (InventoryArchetypes.ANVIL.equals(archetype)) {
            return inventory.getSizeInventory() == 3;
        }
        if (InventoryArchetypes.BREWING_STAND.equals(archetype)) {
            return inventory.getSizeInventory() == 5;
        }
        if (InventoryArchetypes.BEACON.equals(archetype)) {
            return inventory.getSizeInventory() == 1;
        }
        // TODO horse container are actually dependent on an horse entity
        if (InventoryArchetypes.HORSE.equals(archetype)) {
            return inventory.getSizeInventory() == 2;
        }
        if (InventoryArchetypes.HORSE_WITH_CHEST.equals(archetype)) {
            return inventory.getSizeInventory() == 2 + 5 * 3;
        }
        if (InventoryArchetypes.VILLAGER.equals(archetype)) {
            return inventory.getSizeInventory() == 3;
        }
        // else any other Archetype we cannot be sure which size is correct
        return true;
    }

    public static ChangeInventoryEvent.Transfer.Pre callTransferPre(final Inventory source, final Inventory destination) {
        Sponge.getCauseStackManager().pushCause(source);
        final ChangeInventoryEvent.Transfer.Pre event = SpongeEventFactory.createChangeInventoryEventTransferPre(
                Sponge.getCauseStackManager().getCurrentCause(), source, destination);
        SpongeImpl.postEvent(event);
        Sponge.getCauseStackManager().popCause();
        return event;
    }

    public static boolean callTransferPost(@Nullable final TrackedInventoryBridge captureSource, @Nullable final Inventory source, @Nullable final Inventory destination) {
        // TODO make sure we never got null
        if (captureSource == null || source == null || destination == null) {
            return true;
        }
        Sponge.getCauseStackManager().pushCause(source);
        final ChangeInventoryEvent.Transfer.Post event =
                SpongeEventFactory.createChangeInventoryEventTransferPost(Sponge.getCauseStackManager().getCurrentCause(),
                        source, destination, captureSource.bridge$getCapturedSlotTransactions());
        SpongeImpl.postEvent(event);
        if (event.isCancelled()) {
            // restore inventories
            setSlots(event.getTransactions(), SlotTransaction::getOriginal);
        } else {
            // handle custom inventory transaction result
            event.getTransactions().stream().filter(t -> !t.isValid() || t.getCustom().isPresent()).forEach(t -> t.getSlot().set(t.getFinal().createStack()));
        }

        captureSource.bridge$getCapturedSlotTransactions().clear();
        Sponge.getCauseStackManager().popCause();

        return event.isCancelled();
    }

    public static void setSlots(final List<SlotTransaction> transactions, final Function<SlotTransaction, ItemStackSnapshot> func) {
        transactions.forEach(t -> t.getSlot().set(func.apply(t).createStack()));
    }

    /**
     * Captures a transaction
     *
     * @param captureIn the {@link TrackedInventoryBridge} to capture the transaction in
     * @param inv the Inventory
     * @param index the affected SlotIndex
     * @param originalStack the original Stack
     */
    public static void captureTransaction(@Nullable final TrackedInventoryBridge captureIn, @Nullable final Inventory inv, final int index, final ItemStack originalStack) {
        // TODO make sure we never got null
        if (captureIn == null || inv == null) {
            return;
        }
        final Optional<org.spongepowered.api.item.inventory.Slot> slot = ((InventoryAdapter) inv).getSlot(index);
        if (slot.isPresent()) {
            final SlotTransaction trans = new SlotTransaction(slot.get(),
                    ItemStackUtil.snapshotOf(originalStack),
                    ItemStackUtil.snapshotOf(slot.get().peek().orElse(org.spongepowered.api.item.inventory.ItemStack.empty())));
            captureIn.bridge$getCapturedSlotTransactions().add(trans);
        } else {
            SpongeImpl.getLogger().warn("Unable to capture transaction from " + inv.getClass() + " at index " + index);
        }
    }

    /**
     * Captures a transaction
     *
     * @param captureIn the {@link TrackedInventoryBridge} to capture the transaction in
     * @param inv the Inventory
     * @param index the affected SlotIndex
     * @param transaction the transaction to execute
     * @return the result if the transaction
     */
    public static ItemStack captureTransaction(@Nullable final TrackedInventoryBridge captureIn, @Nullable final Inventory inv, final int index, final Supplier<ItemStack> transaction) {
        // TODO make sure we never got null
        if (captureIn == null || inv == null) {
            return transaction.get();
        }

        final Optional<org.spongepowered.api.item.inventory.Slot> slot = ((InventoryAdapter) inv).getSlot(index);
        if (!slot.isPresent()) {
            SpongeImpl.getLogger().warn("Unable to capture transaction from " + inv.getClass() + " at index " + index);
            return transaction.get();
        }
        final ItemStackSnapshot original = slot.get().peek().map(ItemStackUtil::snapshotOf).orElse(ItemStackSnapshot.NONE);
        final ItemStack remaining = transaction.get();
        if (remaining.isEmpty()) {
            final ItemStackSnapshot replacement = slot.get().peek().map(ItemStackUtil::snapshotOf).orElse(ItemStackSnapshot.NONE);
            captureIn.bridge$getCapturedSlotTransactions().add(new SlotTransaction(slot.get(), original, replacement));
        }
        return remaining;
    }

    public static SetAITargetEvent callSetAttackTargetEvent(@Nullable final Entity target, final Agent agent) {
        final SetAITargetEvent event = SpongeEventFactory.createSetAITargetEvent(Sponge.getCauseStackManager().getCurrentCause(), Optional.ofNullable(target), agent);
        SpongeImpl.postEvent(event);
        return event;
    }

    public static CraftItemEvent.Preview callCraftEventPre(final EntityPlayer player, final CraftingInventory inventory,
            final SlotTransaction previewTransaction, @Nullable final CraftingRecipe recipe, final Container container, final List<SlotTransaction> transactions) {
        final CraftItemEvent.Preview event = SpongeEventFactory
                .createCraftItemEventPreview(Sponge.getCauseStackManager().getCurrentCause(), inventory, previewTransaction, Optional.ofNullable(recipe), ((Inventory) container), transactions);
        SpongeImpl.postEvent(event);
        PacketPhaseUtil.handleSlotRestore(player, container, new ArrayList<>(transactions), event.isCancelled());
        if (player instanceof EntityPlayerMP) {
            if (event.getPreview().getCustom().isPresent() || event.isCancelled() || !event.getPreview().isValid()) {
                ItemStackSnapshot stack = event.getPreview().getFinal();
                if (event.isCancelled() || !event.getPreview().isValid()) {
                    stack = event.getPreview().getOriginal();
                }
                // Resend modified output
                ((EntityPlayerMP) player).connection.sendPacket(new SPacketSetSlot(0, 0, ItemStackUtil.fromSnapshotToNative(stack)));
            }

        }
        return event;
    }

    public static CraftItemEvent.Craft callCraftEventPost(final EntityPlayer player, final CraftingInventory inventory, final ItemStackSnapshot result,
           @Nullable final CraftingRecipe recipe, final Container container, final List<SlotTransaction> transactions) {
        // Get previous cursor if captured
        ItemStack previousCursor = ((ContainerBridge) container).getPreviousCursor();
        if (previousCursor == null) {
            previousCursor = player.inventory.getItemStack(); // or get the current one
        }
        final Transaction<ItemStackSnapshot> cursorTransaction = new Transaction<>(ItemStackUtil.snapshotOf(previousCursor), ItemStackUtil.snapshotOf(player.inventory.getItemStack()));
        final org.spongepowered.api.item.inventory.Slot slot = inventory.getResult();
        final CraftItemEvent.Craft event = SpongeEventFactory.createCraftItemEventCraft(Sponge.getCauseStackManager().getCurrentCause(), result, inventory,
                        cursorTransaction, Optional.ofNullable(recipe), Optional.of(slot), ((org.spongepowered.api.item.inventory.Container) container), transactions);
        SpongeImpl.postEvent(event);

        final boolean capture = ((ContainerBridge) container).capturingInventory();
        ((ContainerBridge) container).setCaptureInventory(false);
        // handle slot-transactions
        PacketPhaseUtil.handleSlotRestore(player, container, new ArrayList<>(transactions), event.isCancelled());
        if (event.isCancelled() || !event.getCursorTransaction().isValid() || event.getCursorTransaction().getCustom().isPresent()) {
            // handle cursor-transaction
            final ItemStackSnapshot newCursor = event.isCancelled() || event.getCursorTransaction().isValid() ? event.getCursorTransaction().getOriginal() : event.getCursorTransaction().getFinal();
            player.inventory.setItemStack(ItemStackUtil.fromSnapshotToNative(newCursor));
            if (player instanceof EntityPlayerMP) {
                ((EntityPlayerMP) player).connection.sendPacket(new SPacketSetSlot(-1, -1, player.inventory.getItemStack()));
            }
        }

        transactions.clear();
        ((ContainerBridge) container).setCaptureInventory(capture);
        return event;
    }

    public static void callPostPlayerRespawnEvent(final EntityPlayerMP playerMP, final boolean conqueredEnd) {
        // We overwrite this method in SpongeForge, in order to fire
        // Forge's PlayerRespawnEvent
    }

    public static UpdateAnvilEvent callUpdateAnvilEvent(final ContainerRepair anvil, final ItemStack slot1, final ItemStack slot2, final ItemStack result, final String name, final int levelCost, final int materialCost) {
        final Transaction<ItemStackSnapshot> transaction = new Transaction<>(ItemStackSnapshot.NONE, ItemStackUtil.snapshotOf(result));
        final UpdateAnvilEventCost costs = new UpdateAnvilEventCost(levelCost, materialCost);
        final UpdateAnvilEvent event = SpongeEventFactory.createUpdateAnvilEvent(Sponge.getCauseStackManager().getCurrentCause(),
                new Transaction<>(costs, costs), name, ItemStackUtil.snapshotOf(slot1), transaction, ItemStackUtil.snapshotOf(slot2), (Inventory)anvil);
        SpongeImpl.postEvent(event);
        return event;
    }

    public static ChangeEntityEquipmentEvent callChangeEntityEquipmentEvent(
        final EntityLivingBase entity, final ItemStackSnapshot before, final ItemStackSnapshot after, final SlotAdapter slot) {
        final ChangeEntityEquipmentEvent event;
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(entity);
            final Cause cause = frame.getCurrentCause();
            final Transaction<ItemStackSnapshot> transaction = new Transaction<>(before, after);
            if (entity instanceof EntityPlayerMP) {
                final Player player = (Player) (EntityPlayerMP) entity;
                event = SpongeEventFactory.createChangeEntityEquipmentEventTargetPlayer(cause, player, slot, transaction);
            } else if (entity instanceof Human) {
                final Human humanoid = (Human) entity;
                event = SpongeEventFactory.createChangeEntityEquipmentEventTargetHumanoid(cause, humanoid, slot, transaction);
            } else {
                final Living living = EntityUtil.fromNativeToLiving(entity);
                event = SpongeEventFactory.createChangeEntityEquipmentEventTargetLiving(cause, living, slot, transaction);
            }
            SpongeImpl.postEvent(event);
            return event;
        }
    }

    public static Optional<net.minecraft.world.Explosion> detonateExplosive(ExplosiveBridge explosiveBridge, Explosion.Builder builder) {
        DetonateExplosiveEvent event = SpongeEventFactory.createDetonateExplosiveEvent(
                Sponge.getCauseStackManager().getCurrentCause(), builder, builder.build(), (Explosive) explosiveBridge
        );
        if (!Sponge.getEventManager().post(event)) {
            Explosion explosion = event.getExplosionBuilder().build();
            if (explosion.getRadius() > 0) {
                ((ServerWorldBridge) ((Explosive) explosiveBridge).getWorld()).triggerInternalExplosion(explosion,
                        e -> GeneralPhase.State.EXPLOSION.createPhaseContext().explosion(e));
            }
            return Optional.of((net.minecraft.world.Explosion) explosion);
        }
        return Optional.empty();
    }
}
