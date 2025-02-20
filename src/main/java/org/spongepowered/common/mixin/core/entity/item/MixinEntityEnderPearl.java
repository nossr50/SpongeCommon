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
package org.spongepowered.common.mixin.core.entity.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.teleport.TeleportTypes;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.mixin.core.entity.projectile.MixinEntityThrowable;
import org.spongepowered.common.util.Constants;

import javax.annotation.Nullable;

@Mixin(EntityEnderPearl.class)
public abstract class MixinEntityEnderPearl extends MixinEntityThrowable {

    @Shadow private EntityLivingBase perlThrower;

    private double damageAmount;

    @ModifyArg(method = "onImpact",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z"))
    private float onAttackEntityFrom(float damage) {
        return (float) this.damageAmount;
    }

    @SuppressWarnings("deprecation")
    @Redirect(method = "onImpact", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayerMP;isPlayerSleeping()Z"))
    private boolean onEnderPearlImpact(EntityPlayerMP player) {
        if (player.isPlayerSleeping()) {
            return true;
        }

        try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.TELEPORT_TYPE, TeleportTypes.ENTITY_TELEPORT);
            frame.addContext(EventContextKeys.PROJECTILE_SOURCE, (Player) player);
            frame.addContext(EventContextKeys.THROWER, (Player) player); // TODO - remove in API 8/1.13

            MoveEntityEvent.Teleport event = EntityUtil.handleDisplaceEntityTeleportEvent(player, ((org.spongepowered.api.entity.Entity) this).getLocation());
            if (event.isCancelled()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void spongeImpl$readFromSpongeCompound(NBTTagCompound compound) {
        super.spongeImpl$readFromSpongeCompound(compound);
        if (compound.hasKey(Constants.Sponge.Entity.Projectile.PROJECTILE_DAMAGE_AMOUNT)) {
            this.damageAmount = compound.getDouble(Constants.Sponge.Entity.Projectile.PROJECTILE_DAMAGE_AMOUNT);
        }
    }

    @Override
    public void spongeImpl$writeToSpongeCompound(NBTTagCompound compound) {
        super.spongeImpl$writeToSpongeCompound(compound);
        compound.setDouble(Constants.Sponge.Entity.Projectile.PROJECTILE_DAMAGE_AMOUNT, this.damageAmount);
    }

    /**
     * @author Zidane - June 2019 - 1.12.2
     * @reason Only have this ender pearl remove the thrower references if we actually changed dimension
     */
    @Override
    @Nullable
    public Entity changeDimension(int dimensionIn) {
        final Entity entity = super.changeDimension(dimensionIn);

        if (entity instanceof EntityEnderPearl) {
            // We actually teleported so...
            this.perlThrower = null;
            this.thrower = null;
        }

        return entity;
    }
}
