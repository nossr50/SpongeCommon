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
package org.spongepowered.common.mixin.core.entity.ai;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import org.spongepowered.api.entity.ai.task.builtin.creature.target.FindNearestAttackableTargetAITask;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.util.Functional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@SuppressWarnings("rawtypes")
@Mixin(EntityAINearestAttackableTarget.class)
public abstract class MixinEntityAINearestAttackableTarget extends MixinEntityAITarget<FindNearestAttackableTargetAITask>
        implements FindNearestAttackableTargetAITask {

    @Shadow protected Class targetClass;
    @Shadow protected int targetChance;
    @Shadow public Predicate targetEntitySelector;

    @SuppressWarnings("unchecked")
    @Override
    public Class<? extends Living> getTargetClass() {
        return this.targetClass;
    }

    @Override
    public FindNearestAttackableTargetAITask setTargetClass(Class<? extends Living> targetClass) {
        this.targetClass = targetClass;
        return this;
    }

    @Override
    public int getChance() {
        return this.targetChance;
    }

    @Override
    public FindNearestAttackableTargetAITask setChance(int chance) {
        this.targetChance = chance;
        return this;
    }

    @Override
    public FindNearestAttackableTargetAITask filter(java.util.function.Predicate<Living> predicate) {
        this.targetEntitySelector = predicate == null ? null : Functional.java8ToGuava(predicate);
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public java.util.function.Predicate<Living> getFilter() {
        return this.targetEntitySelector == null ? Predicates.alwaysTrue() : this.targetEntitySelector;
    }

}
