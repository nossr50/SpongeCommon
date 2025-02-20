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
package org.spongepowered.common.mixin.api.minecraft.entity.passive;

import net.minecraft.entity.passive.EntityHorse;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.data.manipulator.mutable.entity.HorseData;
import org.spongepowered.api.data.type.HorseColor;
import org.spongepowered.api.data.type.HorseStyle;
import org.spongepowered.api.data.value.mutable.Value;
import org.spongepowered.api.entity.living.animal.RideableHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.common.data.manipulator.mutable.entity.SpongeHorseData;
import org.spongepowered.common.data.value.mutable.SpongeValue;
import org.spongepowered.common.registry.type.entity.HorseColorRegistryModule;
import org.spongepowered.common.registry.type.entity.HorseStyleRegistryModule;
import org.spongepowered.common.util.Constants;

import java.util.List;

@Mixin(EntityHorse.class)
public abstract class MixinEntityHorse_API extends MixinAbstractHorse_API implements RideableHorse {

    @Override
    public Value<HorseStyle> style() {
        return new SpongeValue<>(Keys.HORSE_STYLE, Constants.Entity.Horse.DEFAULT_STYLE, HorseStyleRegistryModule.getHorseStyle((EntityHorse) (Object) this));
    }

    @Override
    public Value<HorseColor> color() {
        return new SpongeValue<>(Keys.HORSE_COLOR, Constants.Entity.Horse.DEFAULT_COLOR, HorseColorRegistryModule.getHorseColor((EntityHorse) (Object) this));
    }

    @Override
    public HorseData getHorseData() {
        return new SpongeHorseData(HorseColorRegistryModule.getHorseColor((EntityHorse) (Object) this), HorseStyleRegistryModule.getHorseStyle((EntityHorse) (Object) this));
    }

    @Override
    public void spongeApi$supplyVanillaManipulators(List<? super DataManipulator<?, ?>> manipulators) {
        super.spongeApi$supplyVanillaManipulators(manipulators);
        manipulators.add(getHorseData());
    }
}
