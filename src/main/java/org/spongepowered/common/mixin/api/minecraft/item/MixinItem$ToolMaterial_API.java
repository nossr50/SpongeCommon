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
package org.spongepowered.common.mixin.api.minecraft.item;

import net.minecraft.item.Item;
import org.spongepowered.api.data.type.ToolType;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Locale;

import javax.annotation.Nullable;

@Mixin(Item.ToolMaterial.class)
public abstract class MixinItem$ToolMaterial_API implements ToolType {

    @Nullable private String spongeImpl$name;
    @Nullable private String spongeImpl$capitalizedName;

    @Override
    public String getId() {
        if (this.spongeImpl$name == null) {
            String toString = this.toString();
            if (toString.equalsIgnoreCase("emerald")) {
                toString = "diamond";
            }
            this.spongeImpl$name = toString.toLowerCase(Locale.ENGLISH);
        }
        return this.spongeImpl$name;
    }

    @Override
    public String getName() {
        if (this.spongeImpl$capitalizedName == null) {
            this.spongeImpl$capitalizedName = getId().toUpperCase(Locale.ENGLISH);
        }
        return this.spongeImpl$capitalizedName;
    }
}
