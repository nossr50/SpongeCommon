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
package org.spongepowered.common.mixin.core.block;

import com.google.common.collect.ImmutableList;
import net.minecraft.block.BlockSkull;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.ImmutableDataManipulator;
import org.spongepowered.api.data.manipulator.immutable.block.ImmutableDirectionalData;
import org.spongepowered.api.data.manipulator.immutable.block.ImmutableDropData;
import org.spongepowered.api.data.value.BaseValue;
import org.spongepowered.api.util.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.common.data.ImmutableDataCachingUtil;
import org.spongepowered.common.data.manipulator.immutable.block.ImmutableSpongeDirectionalData;
import org.spongepowered.common.data.manipulator.immutable.block.ImmutableSpongeDropData;
import org.spongepowered.common.data.util.DirectionResolver;

import java.util.Optional;

@Mixin(BlockSkull.class)
public abstract class MixinBlockSkull extends MixinBlock {

    @SuppressWarnings("RedundantTypeArguments") // some JDK's can fail to compile without the explicit type generics
    @Override
    public ImmutableList<ImmutableDataManipulator<?, ?>> getManipulators(IBlockState blockState) {
        return ImmutableList.<ImmutableDataManipulator<?, ?>>of(getShouldDropFor(blockState), getDirectionalData(blockState));
    }

    @Override
    public boolean supports(Class<? extends ImmutableDataManipulator<?, ?>> immutable) {
        return ImmutableDropData.class.isAssignableFrom(immutable) || ImmutableDirectionalData.class.isAssignableFrom(immutable);
    }

    @Override
    public Optional<BlockState> getStateWithData(IBlockState blockState, ImmutableDataManipulator<?, ?> manipulator) {
        if (manipulator instanceof ImmutableDropData) {
            final boolean shouldDrop = ((ImmutableDropData) manipulator).willDrop().get();
            return Optional.of((BlockState) blockState.withProperty(BlockSkull.NODROP, !shouldDrop));
        }
        if (manipulator instanceof ImmutableDirectionalData) {
            final Direction dir = ((ImmutableDirectionalData) manipulator).direction().get();
            return Optional.of((BlockState) blockState.withProperty(BlockSkull.FACING, DirectionResolver.getFor(dir)));
        }
        return super.getStateWithData(blockState, manipulator);
    }

    @Override
    public <E> Optional<BlockState> getStateWithValue(IBlockState blockState, Key<? extends BaseValue<E>> key, E value) {
        if (key.equals(Keys.SHOULD_DROP)) {
            final boolean shouldDrop = (Boolean) value;
            return Optional.of((BlockState) blockState.withProperty(BlockSkull.NODROP, !shouldDrop));
        }
        if (key.equals(Keys.DIRECTION)) {
            final Direction dir = (Direction) value;
            return Optional.of((BlockState) blockState.withProperty(BlockSkull.FACING, DirectionResolver.getFor(dir)));
        }
        return super.getStateWithValue(blockState, key, value);
    }

    private ImmutableDropData getShouldDropFor(IBlockState blockState) {
        return ImmutableDataCachingUtil.getManipulator(ImmutableSpongeDropData.class, !blockState.getValue(BlockSkull.NODROP));
    }

    private ImmutableDirectionalData getDirectionalData(IBlockState blockState) {
        return ImmutableDataCachingUtil.getManipulator(ImmutableSpongeDirectionalData.class,
                DirectionResolver.getFor(blockState.getValue(BlockSkull.FACING)));
    }

}
