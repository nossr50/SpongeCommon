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
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.state.properties.Half;
import net.minecraft.state.properties.StairsShape;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.ImmutableDataManipulator;
import org.spongepowered.api.data.manipulator.immutable.ImmutableDirectionalData;
import org.spongepowered.api.data.manipulator.immutable.ImmutablePortionData;
import org.spongepowered.api.data.manipulator.immutable.ImmutableStairShapeData;
import org.spongepowered.api.data.type.PortionType;
import org.spongepowered.api.data.type.StairShape;
import org.spongepowered.api.data.value.Value;
import org.spongepowered.api.util.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.common.data.ImmutableDataCachingUtil;
import org.spongepowered.common.data.manipulator.immutable.block.ImmutableSpongeDirectionalData;
import org.spongepowered.common.data.manipulator.immutable.block.ImmutableSpongePortionData;
import org.spongepowered.common.data.manipulator.immutable.block.ImmutableSpongeStairShapeData;
import org.spongepowered.common.data.util.DirectionChecker;
import org.spongepowered.common.data.util.DirectionResolver;

import java.util.Optional;

@Mixin(BlockStairs.class)
public abstract class MixinBlockStairs extends MixinBlock {

    @Override
    public ImmutableList<ImmutableDataManipulator<?, ?>> getManipulators(IBlockState blockState) {
        return ImmutableList.<ImmutableDataManipulator<?, ?>>of(getStairShapeFor(blockState), getPortionTypeFor(blockState),
                getDirectionalData(blockState));
    }

    @Override
    public boolean supports(Class<? extends ImmutableDataManipulator<?, ?>> immutable) {
        return ImmutableStairShapeData.class.isAssignableFrom(immutable) || ImmutablePortionData.class.isAssignableFrom(immutable)
                || ImmutableDirectionalData.class.isAssignableFrom(immutable);
    }

    @Override
    public Optional<BlockState> getStateWithData(IBlockState blockState, ImmutableDataManipulator<?, ?> manipulator) {
        if (manipulator instanceof ImmutableStairShapeData) {
            final StairsShape stairShapeType = (StairsShape) (Object) ((ImmutableStairShapeData) manipulator).type().get();
            return Optional.of((BlockState) blockState.with(BlockStairs.SHAPE, stairShapeType));
        } else if (manipulator instanceof ImmutablePortionData) {
            final PortionType portionType = ((ImmutablePortionData) manipulator).type().get();
            return Optional.of((BlockState) blockState.with(BlockStairs.HALF, (Half) (Object) portionType));
        }
        if (manipulator instanceof ImmutableDirectionalData) {
            final Direction dir = DirectionChecker.checkDirectionToHorizontal(((ImmutableDirectionalData) manipulator).direction().get());
            return Optional.of((BlockState) blockState.with(BlockStairs.FACING, DirectionResolver.getFor(dir)));
        }
        return super.getStateWithData(blockState, manipulator);
    }

    @Override
    public <E> Optional<BlockState> getStateWithValue(IBlockState blockState, Key<? extends Value<E>> key, E value) {
        if (key.equals(Keys.STAIR_SHAPE)) {
            final StairsShape stairShapeType = (StairsShape) value;
            return Optional.of((BlockState) blockState.with(BlockStairs.SHAPE, stairShapeType));
        } else if (key.equals(Keys.PORTION_TYPE)) {
            return Optional.of((BlockState) blockState.with(BlockStairs.HALF, (Half) value));
        }
        if (key.equals(Keys.DIRECTION)) {
            final Direction dir = DirectionChecker.checkDirectionToHorizontal((Direction) value);
            return Optional.of((BlockState) blockState.with(BlockStairs.FACING, DirectionResolver.getFor(dir)));
        }
        return super.getStateWithValue(blockState, key, value);
    }

    private ImmutableStairShapeData getStairShapeFor(IBlockState blockState) {
        return ImmutableDataCachingUtil.getManipulator(ImmutableSpongeStairShapeData.class,
                (StairShape) (Object) blockState.get(BlockStairs.SHAPE));
    }

    private ImmutablePortionData getPortionTypeFor(IBlockState blockState) {
        return ImmutableDataCachingUtil.getManipulator(ImmutableSpongePortionData.class,
                convertType(blockState.get(BlockStairs.HALF)));
    }

    private ImmutableDirectionalData getDirectionalData(IBlockState blockState) {
        return ImmutableDataCachingUtil.getManipulator(ImmutableSpongeDirectionalData.class,
                DirectionResolver.getFor(blockState.get(BlockStairs.FACING)));
    }

    private PortionType convertType(Half type) {
        return (PortionType) (Object)Half.valueOf(type.getName().toUpperCase());
    }
}
