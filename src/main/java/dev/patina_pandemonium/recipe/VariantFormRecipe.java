package dev.patina_pandemonium.recipe;

import com.mojang.serialization.MapCodec;
import dev.patina_pandemonium.registry.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Runtime crafting rules for forms that do not exist on the original source block. */
public class VariantFormRecipe extends CustomRecipe {
    public static final VariantFormRecipe INSTANCE = new VariantFormRecipe();
    public static final MapCodec<VariantFormRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, VariantFormRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    private static final List<FormPattern> PATTERNS = List.of(
            new FormPattern(VariantForm.SLAB, 6, "MMM"),
            new FormPattern(VariantForm.STAIRS, 4, "M  ", "MM ", "MMM"),
            new FormPattern(VariantForm.STAIRS, 4, "  M", " MM", "MMM"),
            new FormPattern(VariantForm.WALL, 6, "MMM", "MMM"),
            new FormPattern(VariantForm.FENCE, 3, "MSM", "MSM"),
            new FormPattern(VariantForm.FENCE_GATE, 1, "SMS", "SMS"),
            new FormPattern(VariantForm.CARPET, 3, "MM"),
            new FormPattern(VariantForm.BUTTON, 1, "M"),
            new FormPattern(VariantForm.PRESSURE_PLATE, 1, "MM"));

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.findMatch(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Match match = this.findMatch(input);
        if (match == null) return ItemStack.EMPTY;
        ItemStack output = DynamicVariantRegistry.fabricate(match.material(), match.pattern().form(), match.stage(), match.waxed(), match.dyeColor(), match.pattern().count());
        if (!output.isEmpty()) output.remove(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get());
        return output;
    }

    @Override
    public RecipeSerializer<VariantFormRecipe> getSerializer() {
        return DynamicVariantRegistry.VARIANT_FORM_RECIPE.get();
    }

    @Nullable
    private Match findMatch(CraftingInput input) {
        for (FormPattern pattern : PATTERNS) {
            if (pattern.width() > input.width() || pattern.height() > input.height()) continue;
            for (int offsetY = 0; offsetY <= input.height() - pattern.height(); offsetY++) {
                for (int offsetX = 0; offsetX <= input.width() - pattern.width(); offsetX++) {
                    Match match = this.matchAt(input, pattern, offsetX, offsetY);
                    if (match != null) return match;
                }
            }
        }
        return null;
    }

    @Nullable
    private Match matchAt(CraftingInput input, FormPattern pattern, int offsetX, int offsetY) {
        Identifier sourceId = null;
        ItemStack material = ItemStack.EMPTY;
        OxidationStage stage = OxidationStage.FRESH;
        boolean waxed = true;
        boolean foundMaterial = false;
        DyeColor dyeColor = null;
        boolean dyeInitialized = false;

        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                char expected = pattern.at(x - offsetX, y - offsetY);
                ItemStack stack = input.getItem(x + y * input.width());
                if (expected == ' ') {
                    if (!stack.isEmpty()) return null;
                    continue;
                }
                if (expected == 'S') {
                    if (!this.isMarker(stack, Items.STICK)) return null;
                    continue;
                }

                MaterialState current = this.materialState(stack);
                if (current == null || sourceId != null && !sourceId.equals(current.sourceId())) return null;
                sourceId = current.sourceId();
                if (material.isEmpty()) material = stack;
                foundMaterial = true;
                if (current.stage().ordinal() > stage.ordinal()) stage = current.stage();
                waxed &= current.waxed();
                if (!dyeInitialized) {
                    dyeColor = current.dyeColor();
                    dyeInitialized = true;
                } else if (dyeColor != current.dyeColor()) {
                    dyeColor = null;
                }
            }
        }

        if (!foundMaterial || sourceId == null || DynamicVariantRegistry.hasExistingForm(sourceId, pattern.form())) return null;
        return new Match(pattern, material, stage, waxed, dyeColor);
    }

    @Nullable
    private MaterialState materialState(ItemStack stack) {
        if (stack.isEmpty()) return null;
        VariantData blockData = stack.get(DynamicVariantRegistry.VARIANT_DATA.get());
        if (blockData != null && blockData.form() != VariantForm.FULL) return null;

        Identifier sourceId = DynamicVariantRegistry.fullSourceId(stack);
        if (sourceId == null) return null;
        ItemVariantData itemData = DynamicVariantRegistry.variantUseData(stack);
        if (itemData == null) return new MaterialState(sourceId, OxidationStage.FRESH, false, null);
        return new MaterialState(sourceId, itemData.stage(), itemData.waxed(), itemData.dyeColor());
    }

    private boolean isMarker(ItemStack stack, Item marker) {
        if (stack.isEmpty()) return false;
        if (stack.is(marker)) return true;
        ItemVariantData itemData = DynamicVariantRegistry.peekItemData(stack);
        return itemData != null && itemData.sourceId().equals(BuiltInRegistries.ITEM.getKey(marker));
    }

    private record FormPattern(VariantForm form, int count, String... rows) {
        int width() {
            return this.rows[0].length();
        }

        int height() {
            return this.rows.length;
        }

        char at(int x, int y) {
            return x < 0 || y < 0 || y >= this.rows.length || x >= this.rows[y].length() ? ' ' : this.rows[y].charAt(x);
        }
    }

    private record MaterialState(Identifier sourceId, OxidationStage stage, boolean waxed, @Nullable DyeColor dyeColor) {
    }

    private record Match(FormPattern pattern, ItemStack material, OxidationStage stage, boolean waxed, @Nullable DyeColor dyeColor) {
    }
}
