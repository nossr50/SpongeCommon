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
package org.spongepowered.common.item.recipe.crafting;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.recipe.crafting.ShapelessCraftingRecipe;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.translation.Translation;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;
import org.spongepowered.common.util.SpongeCatalogBuilder;

import javax.annotation.Nullable;

@NonnullByDefault
public class SpongeShapelessCraftingRecipeBuilder extends SpongeCatalogBuilder<ShapelessCraftingRecipe, ShapelessCraftingRecipe.Builder>
        implements ShapelessCraftingRecipe.Builder.EndStep, ShapelessCraftingRecipe.Builder.ResultStep {

    private ItemStackSnapshot exemplaryResult = ItemStackSnapshot.NONE;
    private NonNullList<Ingredient> ingredients = NonNullList.create();
    private String groupName = "";

    @Override
    public EndStep group(@Nullable String name) {
        this.groupName = name == null ? "" : name;
        return this;
    }

    @Override
    public EndStep name(Translation name) {
        return (EndStep) super.name(name);
    }

    @Override
    public EndStep name(String name) {
        return (EndStep) super.name(name);
    }

    @Override
    public EndStep id(String id) {
        return (EndStep) super.id(id);
    }

    @Deprecated
    @Override
    public ShapelessCraftingRecipe.Builder from(ShapelessCraftingRecipe value) {
        this.exemplaryResult = value.getExemplaryResult();

        if (this.exemplaryResult == null) {
            this.exemplaryResult = ItemStackSnapshot.NONE;
        }

        this.ingredients.clear();
        value.getIngredientPredicates().forEach(i -> this.ingredients.add(IngredientUtil.toNative(i)));

        this.groupName = "";
        if (value instanceof ShapelessRecipes) {
            this.groupName = ((ShapelessRecipes) value).group;
        }

        super.reset();
        return this;
    }

    @Override
    protected ShapelessCraftingRecipe build(PluginContainer plugin, String id, Translation name) {
        checkState(this.exemplaryResult != null && this.exemplaryResult != ItemStackSnapshot.NONE, "The result is not set.");
        checkState(!this.ingredients.isEmpty(), "The ingredients are not set.");
        // Copy the ingredient list
        final NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.addAll(this.ingredients);
        return ((ShapelessCraftingRecipe) new SpongeShapelessRecipe(plugin.getId() + ':' + id, this.groupName,
                ItemStackUtil.toNative(this.exemplaryResult.createStack()), ingredients));
    }

    @Override
    public ShapelessCraftingRecipe.Builder reset() {
        super.reset();
        this.exemplaryResult = ItemStackSnapshot.NONE;
        this.ingredients.clear();
        this.groupName = "";
        return this;
    }

    @Override
    public ResultStep addIngredient(org.spongepowered.api.item.recipe.crafting.Ingredient ingredient) {
        checkNotNull(ingredient, "ingredient");
        this.ingredients.add(IngredientUtil.toNative(ingredient));
        return this;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public EndStep result(ItemStackSnapshot result) {
        checkNotNull(result, "result");
        checkArgument(result != ItemStackSnapshot.NONE, "The result must not be `ItemStackSnapshot.NONE`.");
        this.exemplaryResult = result;
        return this;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ShapelessCraftingRecipe build(String id, Object plugin) {
        checkState(this.exemplaryResult != null && this.exemplaryResult != ItemStackSnapshot.NONE,
                "The result is not set.");
        checkState(!this.ingredients.isEmpty(), "The ingredients are not set.");
        checkNotNull(id, "id");
        checkNotNull(id, "plugin");

        PluginContainer container = SpongeImpl.getPluginContainer(plugin);

        if (!id.startsWith(container.getId() + ":")) {
            id = container.getId() + ":" + id;
        }

        return ((ShapelessCraftingRecipe) new SpongeShapelessRecipe(id, this.groupName, ItemStackUtil.toNative(this.exemplaryResult.createStack()),
            this.ingredients));
    }

}
