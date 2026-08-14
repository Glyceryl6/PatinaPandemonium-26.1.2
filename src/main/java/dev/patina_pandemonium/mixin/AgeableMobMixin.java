package dev.patina_pandemonium.mixin;

import net.minecraft.world.entity.AgeableMob;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AgeableMob.class)
public class AgeableMobMixin {

    @Redirect(method = "setAge", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/AgeableMob;age:I", opcode = Opcodes.PUTFIELD))
    public void patina$setAge(AgeableMob mob, int value) {
        mob.age = 0;
    }

}