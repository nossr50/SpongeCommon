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
package org.spongepowered.common.data.processor.data.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.immutable.item.ImmutableSpawnableData;
import org.spongepowered.api.data.manipulator.mutable.item.SpawnableData;
import org.spongepowered.api.data.value.ValueContainer;
import org.spongepowered.api.data.value.immutable.ImmutableValue;
import org.spongepowered.api.data.value.mutable.Value;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.data.manipulator.mutable.item.SpongeSpawnableData;
import org.spongepowered.common.data.processor.common.AbstractItemSingleDataProcessor;
import org.spongepowered.common.data.util.NbtDataUtil;
import org.spongepowered.common.data.value.immutable.ImmutableSpongeValue;
import org.spongepowered.common.data.value.mutable.SpongeValue;
import org.spongepowered.common.registry.type.entity.EntityTypeRegistryModule;
import org.spongepowered.common.util.Constants;

import java.util.Optional;

public class SpawnableDataProcessor extends AbstractItemSingleDataProcessor<EntityType, Value<EntityType>, SpawnableData, ImmutableSpawnableData> {

    public SpawnableDataProcessor() {
        super(input -> input.getItem().equals(Items.SPAWN_EGG), Keys.SPAWNABLE_ENTITY_TYPE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean set(ItemStack itemStack, EntityType value) {
        final ResourceLocation name = EntityList.getKey((Class<? extends Entity>) value.getEntityClass());
        if (EntityList.ENTITY_EGGS.containsKey(name)) {
            final NBTTagCompound mainCompound = NbtDataUtil.getOrCreateCompound(itemStack);
            final NBTTagCompound subCompound = NbtDataUtil.getOrCreateSubCompound(mainCompound, Constants.TileEntity.Spawner.SPAWNABLE_ENTITY_TAG);
            subCompound.setString(Constants.Entity.ENTITY_TYPE_ID, name.toString());
            return true;
        }
        return false;
    }

    @Override
    public Optional<EntityType> getVal(ItemStack itemStack) {
        final ResourceLocation name = ItemMonsterPlacer.getNamedIdFrom(itemStack);
        if (name != null) {
            final Class<? extends Entity> entity = SpongeImplHooks.getEntityClass(name);
            return Optional.ofNullable(EntityTypeRegistryModule.getInstance().getForClass(entity));
        }
        return Optional.empty();
    }

    @Override
    protected Value<EntityType> constructValue(EntityType actualValue) {
        return new SpongeValue<>(Keys.SPAWNABLE_ENTITY_TYPE, EntityTypes.CREEPER, actualValue);
    }

    @Override
    public ImmutableValue<EntityType> constructImmutableValue(EntityType value) {
        return ImmutableSpongeValue.cachedOf(Keys.SPAWNABLE_ENTITY_TYPE, EntityTypes.CREEPER, value);
    }

    @Override
    public SpawnableData createManipulator() {
        return new SpongeSpawnableData();
    }

    @Override
    public DataTransactionResult removeFrom(ValueContainer<?> container) {
        if (!supports(container)) {
            return DataTransactionResult.failNoData();
        }
        ItemStack itemStack = (ItemStack) container;
        Optional<EntityType> old = getVal(itemStack);
        if (!old.isPresent()) {
            return DataTransactionResult.successNoData();
        }
        try {
            NbtDataUtil.getItemCompound(itemStack).get().removeTag(Constants.TileEntity.Spawner.SPAWNABLE_ENTITY_TAG);
            return DataTransactionResult.successRemove(constructImmutableValue(old.get()));
        } catch (Exception e) {
            return DataTransactionResult.builder().result(DataTransactionResult.Type.ERROR).build();
        }
    }
}
