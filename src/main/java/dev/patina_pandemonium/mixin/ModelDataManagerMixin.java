package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.VariantData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelDataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelDataManager.class)
public class ModelDataManagerMixin {

    @Redirect(method = "refreshAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;getModelData()Lnet/neoforged/neoforge/model/data/ModelData;"))
    private ModelData patina$appendVariantData(BlockEntity blockEntity) {
        ModelData modelData = blockEntity.getModelData();
        VariantData data = DynamicVariantRegistry.blockEntityVariantData(blockEntity);
        return data == null ? modelData : modelData.derive().with(PatinaVariantBlockEntity.MODEL_DATA, data).build();
    }

}