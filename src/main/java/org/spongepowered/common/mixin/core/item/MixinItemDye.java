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
package org.spongepowered.common.mixin.core.item;

import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.event.ShouldFire;
import org.spongepowered.common.event.tracking.IPhaseState;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.phase.block.BlockPhase;
import org.spongepowered.common.event.tracking.phase.block.GrowablePhaseContext;

import java.util.Random;

@Mixin(ItemDye.class)
public abstract class MixinItemDye extends MixinItem {

    /**
     * @author gabizou - March 20th, 2019 - 1.12.2
     * @reason To allow growing to be captured via bonemeal without
     * explicitly attempting to capture the growth, we can enter an
     * alternate phase blockState that will specifically track the block
     * changes as growth, and therefor will be able to throw
     * {@link ChangeBlockEvent.Grow} without specifying the cases
     * where the phase blockState needs to throw the event during another
     * phases's capturing.
     *
     * @param iGrowable The growable (usually the block blockState's block)
     * @param worldIn The world to grow
     * @param rand The random provided by the world
     * @param pos The position to grow at
     * @param blockState The block blockState where the growable was from
     * @param stack The item stack in use, useful for the context
     * @param sameWorld The same world as worldIn
     * @param target The target position, same as pos
     */

    @SuppressWarnings({"unchecked", "Duplicates", "rawtypes"})
    // Pending https://github.com/SpongePowered/Mixin/issues/312
    // @Group(name = "org.spongepowered.tracker:bonemeal", min = 1, max = 1)
    @Redirect(
        method = "applyBonemeal",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/IGrowable;grow(Lnet/minecraft/world/World;Ljava/util/Random;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)V"
        ),
        require = 0, // Will be removed once the above github issue is resolved with a proper solution
        // Even though we're in a group, expecting this to succeed in forge environments will not work since there is a different mixin
        expect = 0
    )
    private static void onGrowableVanilla(IGrowable iGrowable, World worldIn, Random rand, BlockPos pos, IBlockState blockState, ItemStack stack, World sameWorld, BlockPos target) {
        if (((WorldBridge) worldIn).isFake() || !ShouldFire.CHANGE_BLOCK_EVENT_GROW) {
            iGrowable.grow(worldIn, rand, pos, blockState);
            return;
        }

        final PhaseContext<?> current = PhaseTracker.getInstance().getCurrentContext();
        final IPhaseState state = current.state;
        final boolean doesBulk = state.doesBulkBlockCapture(current);
        final boolean doesEvent = state.doesBlockEventTracking(current);
        if (doesBulk || doesEvent) {
            // We can enter the new phase state.
            try (GrowablePhaseContext context = BlockPhase.State.GROWING.createPhaseContext()
                .provideItem(stack)
                .world(worldIn)
                .block(blockState)
                .pos(pos)) {
                context.buildAndSwitch();
                iGrowable.grow(worldIn, rand, pos, blockState);
            }
        }

    }

}
