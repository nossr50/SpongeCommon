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
package org.spongepowered.common.world.extent;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.collect.Maps;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.block.tileentity.TileEntityArchetype;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityArchetype;
import org.spongepowered.api.util.AABB;
import org.spongepowered.api.util.DiscreteTransform3;
import org.spongepowered.api.util.PositionOutOfBoundsException;
import org.spongepowered.api.world.extent.ArchetypeVolume;
import org.spongepowered.api.world.extent.BiomeVolume;
import org.spongepowered.api.world.extent.BlockVolume;
import org.spongepowered.api.world.extent.Extent;
import org.spongepowered.api.world.extent.ImmutableBiomeVolume;
import org.spongepowered.api.world.extent.ImmutableBlockVolume;
import org.spongepowered.api.world.extent.MutableBiomeVolume;
import org.spongepowered.api.world.extent.MutableBlockVolume;
import org.spongepowered.api.world.extent.StorageType;
import org.spongepowered.api.world.extent.UnmodifiableBiomeVolume;
import org.spongepowered.api.world.extent.UnmodifiableBlockVolume;
import org.spongepowered.api.world.extent.worker.MutableBiomeVolumeWorker;
import org.spongepowered.api.world.extent.worker.MutableBlockVolumeWorker;
import org.spongepowered.common.block.SpongeTileEntityArchetype;
import org.spongepowered.common.entity.SpongeEntityArchetype;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.util.gen.ArrayImmutableBlockBuffer;
import org.spongepowered.common.util.gen.ArrayMutableBlockBuffer;
import org.spongepowered.common.util.gen.ByteArrayImmutableBiomeBuffer;
import org.spongepowered.common.util.gen.ByteArrayMutableBiomeBuffer;
import org.spongepowered.common.world.extent.worker.SpongeMutableBiomeVolumeWorker;
import org.spongepowered.common.world.extent.worker.SpongeMutableBlockVolumeWorker;
import org.spongepowered.common.world.schematic.GlobalPalette;
import org.spongepowered.common.world.schematic.SpongeArchetypeVolume;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Extent interface with extra defaults that are only available in the
 * implementation.
 */
public interface DefaultedExtent extends Extent {

    @Override
    default MutableBiomeVolume getBiomeView(Vector3i newMin, Vector3i newMax) {
        if (!containsBiome(newMin.getX(), newMin.getY(), newMin.getZ())) {
            throw new PositionOutOfBoundsException(newMin, getBiomeMin(), getBiomeMax());
        }
        if (!containsBiome(newMax.getX(), newMin.getY(), newMax.getZ())) {
            throw new PositionOutOfBoundsException(newMax, getBiomeMin(), getBiomeMax());
        }
        return new MutableBiomeViewDownsize(this, newMin, newMax);
    }

    @Override
    default MutableBiomeVolume getBiomeView(DiscreteTransform3 transform) {
        return new MutableBiomeViewTransform(this, transform);
    }

    @Override
    default UnmodifiableBiomeVolume getUnmodifiableBiomeView() {
        return new UnmodifiableBiomeVolumeWrapper(this);
    }

    @Override
    default MutableBiomeVolume getBiomeCopy(StorageType type) {
        switch (type) {
            case STANDARD:
                return new ByteArrayMutableBiomeBuffer(GlobalPalette.getBiomePalette(), ExtentBufferUtil.copyToArray((BiomeVolume) this, getBiomeMin(), getBiomeMax(), getBiomeSize()),
                        getBiomeMin(), getBiomeSize());
            case THREAD_SAFE:
            default:
                throw new UnsupportedOperationException(type.name());
        }
    }

    @Override
    default ImmutableBiomeVolume getImmutableBiomeCopy() {
        return ByteArrayImmutableBiomeBuffer.newWithoutArrayClone(ExtentBufferUtil.copyToArray((BiomeVolume) this, getBiomeMin(), getBiomeMax(),
                getBiomeSize()), getBiomeMin(), getBiomeSize());
    }

    @Override
    default MutableBlockVolume getBlockView(Vector3i newMin, Vector3i newMax) {
        if (!containsBlock(newMin.getX(), newMin.getY(), newMin.getZ())) {
            throw new PositionOutOfBoundsException(newMin, getBlockMin(), getBlockMax());
        }
        if (!containsBlock(newMax.getX(), newMax.getY(), newMax.getZ())) {
            throw new PositionOutOfBoundsException(newMax, getBlockMin(), getBlockMax());
        }
        return new MutableBlockViewDownsize(this, newMin, newMax);
    }

    @Override
    default MutableBlockVolume getBlockView(DiscreteTransform3 transform) {
        return new MutableBlockViewTransform(this, transform);
    }

    @Override
    default UnmodifiableBlockVolume getUnmodifiableBlockView() {
        return new UnmodifiableBlockVolumeWrapper(this);
    }

    @Override
    default MutableBlockVolume getBlockCopy(StorageType type) {
        switch (type) {
            case STANDARD:
                // TODO: Optimize and use a local palette
                return new ArrayMutableBlockBuffer(GlobalPalette.getBlockPalette(), getBlockMin(), getBlockSize(),
                        ExtentBufferUtil.copyToArray((BlockVolume) this, getBlockMin(), getBlockMax(), getBlockSize()));
            case THREAD_SAFE:
            default:
                throw new UnsupportedOperationException(type.name());
        }
    }

    @Override
    default ImmutableBlockVolume getImmutableBlockCopy() {
        char[] data = ExtentBufferUtil.copyToArray((BlockVolume) this, getBlockMin(), getBlockMax(), getBlockSize());
        return ArrayImmutableBlockBuffer.newWithoutArrayClone(GlobalPalette.getBlockPalette(), getBlockMin(), getBlockSize(), data);
    }

    @Override
    default MutableBiomeVolumeWorker<? extends Extent> getBiomeWorker() {
        return new SpongeMutableBiomeVolumeWorker<>(this);
    }

    @Override
    default MutableBlockVolumeWorker<? extends Extent> getBlockWorker() {
        return new SpongeMutableBlockVolumeWorker<>(this);
    }

    @Override
    default ArchetypeVolume createArchetypeVolume(Vector3i min, Vector3i max, Vector3i origin) {
        Vector3i tmin = min.min(max);
        Vector3i tmax = max.max(min);
        min = tmin;
        max = tmax;
        Extent volume = getExtentView(min, max);
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();
        final MutableBlockVolume backing = new ArrayMutableBlockBuffer(min.sub(origin), max.sub(min).add(1, 1, 1));
        Map<Vector3i, TileEntityArchetype> tiles = Maps.newHashMap();
        volume.getBlockWorker().iterate((extent, x, y, z) -> {
            BlockState state = extent.getBlock(x, y, z);
            backing.setBlock(x - ox, y - oy, z - oz, state);
            Optional<TileEntity> tile = extent.getTileEntity(x, y, z);
            if (tile.isPresent()) {
                final TileEntityArchetype archetype = tile.get().createArchetype();
                if (archetype instanceof SpongeTileEntityArchetype) {
                    final int[] apos = new int[] {(x - ox) - tmin.getX(), y - tmin.getY(), (z - oz) - tmin.getZ()};
                    final SpongeTileEntityArchetype sponge = (SpongeTileEntityArchetype) archetype;
                    sponge.getCompound().setIntArray(Constants.Sponge.TileEntityArchetype.TILE_ENTITY_POS, apos);
                }
                tiles.put(new Vector3i(x - ox, y - oy, z - oz), archetype);
            }
        });
        if (backing.getBlockSize().equals(Vector3i.ONE)) {
            // We can't get entities within a 1x1x1 block area because of AABB...
            return new SpongeArchetypeVolume(backing, tiles, Collections.emptyList());
        }
        Set<Entity> intersectingEntities = volume.getIntersectingEntities(new AABB(min, max));
        if (intersectingEntities.isEmpty()) {
            return new SpongeArchetypeVolume(backing, tiles, Collections.emptyList());
        }
        ArrayList<EntityArchetype> entities = new ArrayList<>();
        for (Entity hit : intersectingEntities) {
            net.minecraft.entity.Entity nms = (net.minecraft.entity.Entity) hit;
            SpongeEntityArchetype archetype = (SpongeEntityArchetype) hit.createArchetype();
            NBTTagList tagList = archetype.getData().getTagList(Constants.Entity.ENTITY_POSITION, Constants.NBT.TAG_DOUBLE);
            if (tagList.isEmpty()) {
                tagList.appendTag(new NBTTagDouble(nms.posX - ox));
                tagList.appendTag(new NBTTagDouble(nms.posY - oy));
                tagList.appendTag(new NBTTagDouble(nms.posZ - oz));
            } else {
                tagList.set(0, new NBTTagDouble(nms.posX - ox));
                tagList.set(1, new NBTTagDouble(nms.posY - oy));
                tagList.set(2, new NBTTagDouble(nms.posZ - oz));
            }
            entities.add(archetype);
        }
        return new SpongeArchetypeVolume(backing, tiles, entities);
    }

}
