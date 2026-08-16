package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.client.PatinaClient;
import dev.patina_pandemonium.registry.VariantData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TerrainParticle.class)
public class TerrainParticleMixin {

    @Unique
    private boolean patina$variantTintApplied;

    @Inject(method = "updateSprite", at = @At("RETURN"))
    private void patina$tintVariantParticle(BlockState state, BlockPos pos, CallbackInfoReturnable<TerrainParticle> callback) {
        if (this.patina$variantTintApplied) return;
        VariantData data = PatinaClient.blockVariantForParticle(pos, state);
        if (data == null) return;
        this.patina$variantTintApplied = true;
        int tint = this.patina$particleTint(state, pos, data);
        ParticleAccessor particle = (ParticleAccessor) this;
        particle.patina$setRed(particle.patina$getRed() * ((tint >>> 16) & 0xFF) / 255.0F);
        particle.patina$setGreen(particle.patina$getGreen() * ((tint >>> 8) & 0xFF) / 255.0F);
        particle.patina$setBlue(particle.patina$getBlue() * (tint & 0xFF) / 255.0F);
    }

    @Unique
    private int patina$particleTint(BlockState state, BlockPos pos, VariantData data) {
        int tint = data.tint();
        Block source = BuiltInRegistries.BLOCK.getValue(data.sourceId());
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || source == Blocks.AIR || state.is(source)) return tint;
        BlockState sourceState = source.withPropertiesOf(state);
        BlockTintSource sourceTint = Minecraft.getInstance().getBlockColors().getTintSource(sourceState, 0);
        return sourceTint == null ? tint : patina$multiply(tint, sourceTint.colorAsTerrainParticle(sourceState, level, pos));
    }

    @Unique
    private static int patina$multiply(int color, int tint) {
        int red = ((color >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 0xFF;
        int green = ((color >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 0xFF;
        int blue = (color & 0xFF) * (tint & 0xFF) / 0xFF;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
