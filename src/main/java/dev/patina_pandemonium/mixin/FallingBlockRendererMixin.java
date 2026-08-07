package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.block.PatinaOxidizable;
import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.ItemVariantData;
import dev.patina_pandemonium.registry.VariantData;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockRenderer.class)
public class FallingBlockRendererMixin {

    @Inject(method = "extractRenderState*", at = @At("TAIL"))
    private void patina$applyVariantModelData(FallingBlockEntity entity, FallingBlockRenderState state, float partialTicks, CallbackInfo callback) {
        AttachmentType<ItemVariantData> type = DynamicVariantRegistry.ENTITY_VARIANT_DATA.get();
        if (!entity.hasData(type)) return;
        BlockState movingState = state.movingBlockRenderState.blockState;
        Identifier sourceId = DynamicVariantRegistry.sourceId(movingState.getBlock());
        if (sourceId == null) sourceId = BuiltInRegistries.BLOCK.getKey(movingState.getBlock());
        VariantData data = entity.getData(type).forBlock(sourceId);
        ItemStack carrierStack = DynamicVariantRegistry.stack(data);
        Block carrier = Block.byItem(carrierStack.getItem());
        if (!(carrier instanceof PatinaOxidizable)) return;
        state.movingBlockRenderState.blockState = carrier.withPropertiesOf(movingState);
        state.movingBlockRenderState.modelData = ModelData.of(PatinaVariantBlockEntity.MODEL_DATA, data);
    }

}