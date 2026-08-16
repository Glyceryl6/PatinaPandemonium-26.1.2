package dev.patina_pandemonium.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SingleQuadParticle.class)
public interface ParticleAccessor {

    @Accessor("rCol")
    float patina$getRed();

    @Accessor("rCol")
    void patina$setRed(float value);

    @Accessor("gCol")
    float patina$getGreen();

    @Accessor("gCol")
    void patina$setGreen(float value);

    @Accessor("bCol")
    float patina$getBlue();

    @Accessor("bCol")
    void patina$setBlue(float value);
}
