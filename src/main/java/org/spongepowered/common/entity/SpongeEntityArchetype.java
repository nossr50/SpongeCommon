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
package org.spongepowered.common.entity;

import com.flowpowered.math.vector.Vector3d;
import com.google.common.base.MoreObjects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.entity.EntityArchetype;
import org.spongepowered.api.entity.EntitySnapshot;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.spawn.SpawnType;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.common.data.AbstractArchetype;
import org.spongepowered.common.data.nbt.NbtDataType;
import org.spongepowered.common.data.nbt.NbtDataTypes;
import org.spongepowered.common.data.nbt.validation.ValidationType;
import org.spongepowered.common.data.nbt.validation.Validations;
import org.spongepowered.common.data.persistence.NbtTranslator;
import org.spongepowered.common.data.util.DataVersions;
import org.spongepowered.common.data.util.NbtDataUtil;
import org.spongepowered.common.bridge.world.WorldInfoBridge;
import org.spongepowered.common.bridge.world.ServerWorldBridge;
import org.spongepowered.common.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

public class SpongeEntityArchetype extends AbstractArchetype<EntityType, EntitySnapshot, org.spongepowered.api.entity.Entity> implements EntityArchetype {

    @Nullable
    private Vector3d position;

    SpongeEntityArchetype(SpongeEntityArchetypeBuilder builder) {
        super(builder.entityType, builder.compound != null ? builder.compound : builder.entityData == null ? new NBTTagCompound() : NbtTranslator.getInstance().translateData(builder.entityData));
    }

    @Override
    public EntityType getType() {
        return this.type;
    }

    @Nullable
    public NBTTagCompound getData() {
        return this.data;
    }

    public Optional<Vector3d> getPosition() {
        if (this.position != null) {
            return Optional.of(this.position);
        }
        if (!this.data.hasKey(Constants.Entity.ENTITY_POSITION, Constants.NBT.TAG_LIST)) {
            return Optional.empty();
        }
        try {
            NBTTagList pos = this.data.getTagList(Constants.Entity.ENTITY_POSITION, Constants.NBT.TAG_DOUBLE);
            double x = pos.getDoubleAt(0);
            double y = pos.getDoubleAt(1);
            double z = pos.getDoubleAt(2);
            this.position = new Vector3d(x, y, z);
            return Optional.of(this.position);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public DataContainer getEntityData() {
        return NbtTranslator.getInstance().translateFrom(this.data);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<org.spongepowered.api.entity.Entity> apply(Location<World> location) {
        final Vector3d position = location.getPosition();
        final double x = position.getX();
        final double y = position.getY();
        final double z = position.getZ();
        final BlockPos blockPos = new BlockPos(x, y, z);
        final World world = location.getExtent();
        final WorldServer worldServer = (WorldServer) world;

        Entity entity = null;

        try {
            Class<? extends Entity> oclass = (Class<? extends Entity>) this.type.getEntityClass();
            if (oclass != null) {
                entity = oclass.getConstructor(net.minecraft.world.World.class).newInstance(world);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        if (entity == null) {
            return Optional.empty();
        }

        this.data.setTag("Pos", NbtDataUtil.newDoubleNBTList(x, y, z));
        this.data.setInteger("Dimension", ((WorldInfoBridge) location.getExtent().getProperties()).getDimensionId());
        final boolean requiresInitialSpawn;
        if (this.data.hasKey(Constants.Sponge.EntityArchetype.REQUIRES_EXTRA_INITIAL_SPAWN)) {
            requiresInitialSpawn = !this.data.getBoolean(Constants.Sponge.EntityArchetype.REQUIRES_EXTRA_INITIAL_SPAWN);
            this.data.removeTag(Constants.Sponge.EntityArchetype.REQUIRES_EXTRA_INITIAL_SPAWN);
        } else {
            requiresInitialSpawn = true;
        }
        entity.readFromNBT(this.data);
        this.data.removeTag("Pos");
        this.data.removeTag("Dimension");

        final org.spongepowered.api.entity.Entity spongeEntity = (org.spongepowered.api.entity.Entity) entity;
        final List<org.spongepowered.api.entity.Entity> entities = new ArrayList<>();
        entities.add(spongeEntity);
        // We require spawn types. This is more of a sanity check to throw an IllegalStateException otherwise for the plugin developer to properly associate the type.
        final SpawnType require = Sponge.getCauseStackManager().getCurrentContext().require(EventContextKeys.SPAWN_TYPE);
        final SpawnEntityEvent.Custom event = SpongeEventFactory.createSpawnEntityEventCustom(Sponge.getCauseStackManager().getCurrentCause(), entities);
        if (!event.isCancelled()) {
            final ServerWorldBridge mixinWorldServer = (ServerWorldBridge) worldServer;
            entity.setPositionAndRotation(x, y, z, entity.rotationYaw, entity.rotationPitch);
            mixinWorldServer.bridge$forceSpawnEntity(entity);
            if (entity instanceof EntityLiving) {
                // This is ok to force spawn since we aren't considering custom items.
                if (requiresInitialSpawn) {
                    ((EntityLiving) entity).onInitialSpawn(worldServer.getDifficultyForLocation(blockPos), null);
                }
                ((EntityLiving) entity).spawnExplosionParticle();
            }
            return Optional.of(spongeEntity);
        }
        return Optional.empty();
    }

    @Override
    public EntitySnapshot toSnapshot(Location<World> location) {
        final SpongeEntitySnapshotBuilder builder = new SpongeEntitySnapshotBuilder();
        builder.entityType = this.type;
        NBTTagCompound newCompound = this.data.copy();
        newCompound.setTag("Pos", NbtDataUtil
                .newDoubleNBTList(new double[] { location.getPosition().getX(), location.getPosition().getY(), location.getPosition().getZ() }));
        newCompound.setInteger("Dimension", ((WorldInfoBridge) location.getExtent().getProperties()).getDimensionId());
        builder.compound = newCompound;
        builder.worldId = location.getExtent().getUniqueId();
        builder.position = location.getPosition();
        return builder.build();
    }

    @Override
    public int getContentVersion() {
        return DataVersions.EntityArchetype.BASE_VERSION;
    }

    @Override
    public DataContainer toContainer() {
        return DataContainer.createNew()
                .set(Constants.Sponge.EntityArchetype.ENTITY_TYPE, this.type)
                .set(Constants.Sponge.EntityArchetype.ENTITY_DATA, getEntityData())
                ;
    }

    @Override
    protected NbtDataType getDataType() {
        return NbtDataTypes.ENTITY;
    }

    @Override
    protected ValidationType getValidationType() {
        return Validations.ENTITY;
    }

    @Override
    public EntityArchetype copy() {
        final SpongeEntityArchetypeBuilder builder = new SpongeEntityArchetypeBuilder();
        builder.entityType = this.type;
        builder.entityData = NbtTranslator.getInstance().translate(this.data);
        return builder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        SpongeEntityArchetype that = (SpongeEntityArchetype) o;
        return Objects.equals(this.position, that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.position);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("position", this.position)
            .add("type", this.type)
            .toString();
    }
}
