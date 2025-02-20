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
package org.spongepowered.common.mixin.entityactivation;

import net.minecraft.world.World;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.bridge.world.WorldInfoBridge;
import org.spongepowered.common.mixin.plugin.entityactivation.EntityActivationRange;
import org.spongepowered.common.mixin.plugin.entityactivation.interfaces.ActivationCapability;

@NonnullByDefault
@Mixin(value = net.minecraft.entity.Entity.class, priority = 1002)
public abstract class MixinEntity_Activation implements ActivationCapability {

    private final byte activation$activationType = EntityActivationRange.initializeEntityActivationType((net.minecraft.entity.Entity) (Object) this);
    private boolean activation$defaultActivationState = true;
    private long activation$activatedTick = Integer.MIN_VALUE;
    private int activation$activationRange;
    private boolean activation$refreshCache = false;

    @Shadow public World world;
    @Shadow public boolean onGround;

    @Shadow public abstract void setDead();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void activation$InitActivationRanges(World world, CallbackInfo ci) {
        if (world != null && !((WorldBridge) world).isFake() && ((WorldInfoBridge) world.getWorldInfo()).isValid()) {
            EntityActivationRange.initializeEntityActivationState((net.minecraft.entity.Entity) (Object) this);
        }
    }

    @Override
    public void activation$inactiveTick() {
    }

    @Override
    public byte activation$getActivationType() {
        return this.activation$activationType;
    }

    @Override
    public long activation$getActivatedTick() {
        return this.activation$activatedTick;
    }

    @Override
    public boolean activation$getDefaultActivationState() {
        return this.activation$defaultActivationState;
    }

    @Override
    public void activation$setDefaultActivationState(boolean defaultState) {
        this.activation$defaultActivationState = defaultState;
    }

    @Override
    public void activation$setActivatedTick(long tick) {
        this.activation$activatedTick = tick;
    }

    @Override
    public int activation$getActivationRange() {
        return this.activation$activationRange;
    }

    @Override
    public void activation$setActivationRange(int range) {
        this.activation$activationRange = range;
    }

    @Override
    public void activation$requiresActivationCacheRefresh(boolean flag) {
        this.activation$refreshCache = flag;
    }

    @Override
    public boolean activation$requiresActivationCacheRefresh() {
        return this.activation$refreshCache;
    }
}
