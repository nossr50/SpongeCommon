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
package org.spongepowered.common.mixin.optimization.tileentity;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityHopper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.common.bridge.optimization.HopperOptimizationBridge;

@Mixin(value = TileEntityHopper.class, priority = 1300)
public class MixinTileEntityHopper_HopperOptimization extends MixinTileEntity_HopperOptimization {

    @Redirect(method = "insertStack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/IInventory;setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V"))
    private static void hopper$FlipMarkUpdateWhenInserting(final IInventory iInventory, final int index, final ItemStack stack) {
        if (iInventory instanceof HopperOptimizationBridge) {
            ((HopperOptimizationBridge) iInventory).hopper$setCancelDirtyUpdate(true);
        }
        iInventory.setInventorySlotContents(index, stack);
        if (iInventory instanceof HopperOptimizationBridge) {
            ((HopperOptimizationBridge) iInventory).hopper$setCancelDirtyUpdate(false);
        }
    }

}
