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
package org.spongepowered.common.mixin.core.tileentity;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Lists;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.world.WorldServer;
import org.spongepowered.api.data.meta.PatternLayer;
import org.spongepowered.api.data.type.BannerPatternShape;
import org.spongepowered.api.data.type.DyeColor;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.data.meta.SpongePatternLayer;
import org.spongepowered.common.interfaces.block.tile.IMixinBanner;
import org.spongepowered.common.registry.SpongeGameRegistry;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.util.NonNullArrayList;

import java.util.ArrayList;
import java.util.List;

@NonnullByDefault
@Mixin(TileEntityBanner.class)
public abstract class MixinTileEntityBanner extends MixinTileEntity implements IMixinBanner {

    @Shadow private EnumDyeColor baseColor;
    @Shadow private NBTTagList patterns;

    private List<PatternLayer> patternLayers = Lists.newArrayList();

    @Inject(method = "setItemValues", at = @At("RETURN"))
    private void onSetItemValues(final CallbackInfo ci) {
        impl$updatePatterns();
    }

    @Override
    public void bridge$readFromSpongeCompound(final NBTTagCompound compound) {
        super.bridge$readFromSpongeCompound(compound);
        impl$updatePatterns();
    }

    @Override
    protected void bridge$writeToSpongeCompound(final NBTTagCompound compound) {
        super.bridge$writeToSpongeCompound(compound);
    }

    private void impl$markDirtyAndUpdate() {
        this.bridge$markDirty();
        if (this.world != null && !this.world.isRemote) {
            ((WorldServer) this.world).getPlayerChunkMap().markBlockForUpdate(this.getPos());
        }
    }

    private void impl$updatePatterns() {
        this.patternLayers.clear();
        if (this.patterns != null) {
            final SpongeGameRegistry registry = SpongeImpl.getRegistry();
            for (int i = 0; i < this.patterns.tagCount(); i++) {
                final NBTTagCompound tagCompound = this.patterns.getCompoundTagAt(i);
                final String patternId = tagCompound.getString(Constants.TileEntity.Banner.BANNER_PATTERN_ID);
                this.patternLayers.add(new SpongePatternLayer(
                    SpongeImpl.getRegistry().getType(BannerPatternShape.class, patternId).get(),
                    registry.getType(DyeColor.class, EnumDyeColor.byDyeDamage(tagCompound.getInteger(Constants.TileEntity.Banner.BANNER_PATTERN_COLOR)).getName()).get()));
            }
        }
        this.impl$markDirtyAndUpdate();
    }

    @Override
    public List<PatternLayer> getLayers() {
        return new ArrayList<>(this.patternLayers);
    }

    @Override
    public void setLayers(final List<PatternLayer> layers) {
        this.patternLayers = new NonNullArrayList<>();
        this.patternLayers.addAll(layers);
        this.patterns = new NBTTagList();
        for (final PatternLayer layer : this.patternLayers) {
            final NBTTagCompound compound = new NBTTagCompound();
            compound.setString(Constants.TileEntity.Banner.BANNER_PATTERN_ID, layer.getShape().getName());
            compound.setInteger(Constants.TileEntity.Banner.BANNER_PATTERN_COLOR, ((EnumDyeColor) (Object) layer.getColor()).getDyeDamage());
            this.patterns.appendTag(compound);
        }
        impl$markDirtyAndUpdate();
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public DyeColor getBaseColor() {
        return (DyeColor) (Object) this.baseColor;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void setBaseColor(final DyeColor baseColor) {
        checkNotNull(baseColor, "Null DyeColor!");
        try {
            final EnumDyeColor color = (EnumDyeColor) (Object) baseColor;
            this.baseColor = color;
        } catch (final Exception e) {
            this.baseColor = EnumDyeColor.BLACK;
        }
        impl$markDirtyAndUpdate();
    }
}
