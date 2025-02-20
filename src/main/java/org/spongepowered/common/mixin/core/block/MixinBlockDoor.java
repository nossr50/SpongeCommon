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
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockDoor.EnumDoorHalf;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.data.key.Key;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.ImmutableDataManipulator;
import org.spongepowered.api.data.manipulator.immutable.block.ImmutableDirectionalData;
import org.spongepowered.api.data.manipulator.immutable.block.ImmutableHingeData;
import org.spongepowered.api.data.manipulator.immutable.block.ImmutableOpenData;
import org.spongepowered.api.data.manipulator.immutable.block.ImmutablePortionData;
import org.spongepowered.api.data.manipulator.immutable.block.ImmutablePoweredData;
import org.spongepowered.api.data.type.Hinge;
import org.spongepowered.api.data.type.PortionType;
import org.spongepowered.api.data.type.PortionTypes;
import org.spongepowered.api.data.value.BaseValue;
import org.spongepowered.api.util.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.common.data.ImmutableDataCachingUtil;
import org.spongepowered.common.data.manipulator.immutable.block.ImmutableSpongeDirectionalData;
import org.spongepowered.common.data.manipulator.immutable.block.ImmutableSpongeHingeData;
import org.spongepowered.common.data.manipulator.immutable.block.ImmutableSpongeOpenData;
import org.spongepowered.common.data.manipulator.immutable.block.ImmutableSpongePortionData;
import org.spongepowered.common.data.manipulator.immutable.block.ImmutableSpongePoweredData;
import org.spongepowered.common.data.util.DirectionChecker;
import org.spongepowered.common.data.util.DirectionResolver;

import java.util.Optional;

@Mixin(BlockDoor.class)
public abstract class MixinBlockDoor extends MixinBlock {

    @Override
    public ImmutableList<ImmutableDataManipulator<?, ?>> getManipulators(IBlockState blockState) {
        return ImmutableList.<ImmutableDataManipulator<?, ?>>of(getHingeFor(blockState), getIsOpenFor(blockState),
                getIsPoweredFor(blockState), getDirectionalData(blockState), getPortionData(blockState));
    }

    @Override
    public boolean supports(Class<? extends ImmutableDataManipulator<?, ?>> immutable) {
        return ImmutableHingeData.class.isAssignableFrom(immutable) || ImmutableOpenData.class.isAssignableFrom(immutable)
                || ImmutablePoweredData.class.isAssignableFrom(immutable) || ImmutablePortionData.class.isAssignableFrom(immutable);
    }

    @Override
    public Optional<BlockState> getStateWithData(IBlockState blockState, ImmutableDataManipulator<?, ?> manipulator) {
        if (manipulator instanceof ImmutableHingeData) {
            final BlockDoor.EnumHingePosition hinge = (BlockDoor.EnumHingePosition) (Object) ((ImmutableHingeData) manipulator).type().get();
            return Optional.of((BlockState) blockState.withProperty(BlockDoor.HINGE, hinge));
        }
        if (manipulator instanceof ImmutableOpenData) {
            final boolean isOpen = ((ImmutableOpenData) manipulator).open().get();
            return Optional.of((BlockState) blockState.withProperty(BlockDoor.OPEN, isOpen));
        }
        if (manipulator instanceof ImmutablePoweredData) {
            return Optional.of((BlockState) blockState.withProperty(BlockDoor.POWERED, ((ImmutablePoweredData) manipulator).powered().get()));
        }
        if (manipulator instanceof ImmutableDirectionalData) {
            final Direction dir = DirectionChecker.checkDirectionToHorizontal(((ImmutableDirectionalData) manipulator).direction().get());
            return Optional.of((BlockState) blockState.withProperty(BlockDoor.FACING, DirectionResolver.getFor(dir)));
        }
        if (manipulator instanceof ImmutablePortionData) {
            return Optional.of((BlockState) blockState.withProperty(BlockDoor.HALF,
                    convertPortionType(((ImmutablePortionData) manipulator).type().get())));
        }
        return super.getStateWithData(blockState, manipulator);
    }

    @Override
    public <E> Optional<BlockState> getStateWithValue(IBlockState blockState, Key<? extends BaseValue<E>> key, E value) {
        if (key.equals(Keys.HINGE_POSITION)) {
            final BlockDoor.EnumHingePosition hinge = (BlockDoor.EnumHingePosition) value;
            return Optional.of((BlockState) blockState.withProperty(BlockDoor.HINGE, hinge));
        }
        if (key.equals(Keys.OPEN)) {
            final boolean isOpen = (Boolean) value;
            return Optional.of((BlockState) blockState.withProperty(BlockDoor.OPEN, isOpen));
        }
        if (key.equals(Keys.POWERED)) {
            return Optional.of((BlockState) blockState.withProperty(BlockDoor.POWERED, (Boolean) value));
        }
        if (key.equals(Keys.DIRECTION)) {
            final Direction dir = DirectionChecker.checkDirectionToHorizontal((Direction) value);
            return Optional.of((BlockState) blockState.withProperty(BlockDoor.FACING, DirectionResolver.getFor(dir)));
        }
        if (key.equals(Keys.PORTION_TYPE)) {
            return Optional.of((BlockState) blockState.withProperty(BlockDoor.HALF, convertPortionType((PortionType) value)));
        }
        return super.getStateWithValue(blockState, key, value);
    }

    private BlockDoor.EnumDoorHalf convertPortionType(PortionType portionType) {
        return portionType == PortionTypes.BOTTOM ? BlockDoor.EnumDoorHalf.LOWER : BlockDoor.EnumDoorHalf.UPPER;
    }

    private ImmutableHingeData getHingeFor(IBlockState blockState) {
        return ImmutableDataCachingUtil.getManipulator(ImmutableSpongeHingeData.class, (Hinge) (Object) blockState.getValue(BlockDoor.HINGE));
    }

    private ImmutableOpenData getIsOpenFor(IBlockState blockState) {
        return ImmutableDataCachingUtil.getManipulator(ImmutableSpongeOpenData.class, blockState.getValue(BlockDoor.OPEN));
    }

    private ImmutablePoweredData getIsPoweredFor(IBlockState blockState) {
        return ImmutableDataCachingUtil.getManipulator(ImmutableSpongePoweredData.class, blockState.getValue(BlockDoor.POWERED));
    }

    private ImmutableDirectionalData getDirectionalData(IBlockState blockState) {
        return ImmutableDataCachingUtil.getManipulator(ImmutableSpongeDirectionalData.class,
                DirectionResolver.getFor(blockState.getValue(BlockDoor.FACING)));
    }

    private ImmutablePortionData getPortionData(IBlockState blockState) {
        EnumDoorHalf half = blockState.getValue(BlockDoor.HALF);
        return ImmutableDataCachingUtil.getManipulator(ImmutableSpongePortionData.class,
                half == EnumDoorHalf.LOWER ? PortionTypes.BOTTOM : PortionTypes.TOP);
    }

}
