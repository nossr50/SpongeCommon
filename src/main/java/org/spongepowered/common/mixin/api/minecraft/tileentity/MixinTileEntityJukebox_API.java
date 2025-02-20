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
package org.spongepowered.common.mixin.api.minecraft.tileentity;

import net.minecraft.block.BlockJukebox;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemRecord;
import org.spongepowered.api.block.tileentity.Jukebox;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.data.manipulator.mutable.RepresentedItemData;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;
import org.spongepowered.common.mixin.core.block.AccessorBlockJukebox;
import org.spongepowered.common.util.Constants;

import java.util.List;

@Mixin(BlockJukebox.TileEntityJukebox.class)
public abstract class MixinTileEntityJukebox_API extends MixinTileEntity_API implements Jukebox {

    @Shadow public abstract net.minecraft.item.ItemStack getRecord();
    @Shadow public abstract void setRecord(net.minecraft.item.ItemStack recordStack);

    @Override
    public void playRecord() {
        if (!getRecord().isEmpty()) {
            this.world.playEvent(null, Constants.WorldEvents.PLAY_RECORD_EVENT, this.pos, Item.getIdFromItem(getRecord().getItem()));
        }
    }

    @Override
    public void stopRecord() {
        this.world.playEvent(Constants.WorldEvents.PLAY_RECORD_EVENT, this.pos, 0);
        this.world.playRecord(this.pos, null);
    }

    @Override
    public void ejectRecord() {
        IBlockState block = this.world.getBlockState(this.pos);
        if (block.getBlock() == Blocks.JUKEBOX) {
            ((AccessorBlockJukebox) block.getBlock()).accessor$dropRecordItem(this.world, this.pos, block);
            this.world.setBlockState(this.pos, block.withProperty(BlockJukebox.HAS_RECORD, false), Constants.BlockChangeFlags.NOTIFY_CLIENTS);
        }
    }

    @Override
    public void insertRecord(ItemStack record) {
        final net.minecraft.item.ItemStack itemStack = ItemStackUtil.toNative(record);
        if (!(itemStack.getItem() instanceof ItemRecord)) {
            return;
        }
        final IBlockState block = this.world.getBlockState(this.pos);
        if (block.getBlock() == Blocks.JUKEBOX) {
            // Don't use BlockJukebox#insertRecord - it looses item data
            this.setRecord(itemStack);
            this.world.setBlockState(this.pos, block.withProperty(BlockJukebox.HAS_RECORD, true), Constants.BlockChangeFlags.NOTIFY_CLIENTS);
        }
    }

    @Override
    public void supplyVanillaManipulators(List<DataManipulator<?, ?>> manipulators) {
        super.supplyVanillaManipulators(manipulators);
        get(RepresentedItemData.class).ifPresent(manipulators::add);
    }

}
