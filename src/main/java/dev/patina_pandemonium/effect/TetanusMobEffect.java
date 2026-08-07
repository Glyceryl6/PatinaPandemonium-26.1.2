package dev.patina_pandemonium.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** A non-lethal periodic damage effect caused by rusty attackers and weapons. */
public class TetanusMobEffect extends MobEffect {

    public TetanusMobEffect(int color) {
        super(MobEffectCategory.HARMFUL, color);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        float damage = Math.min(1.0F + amplifier * 0.5F, entity.getHealth() - 1.0F);
        if (damage > 0.0F) entity.hurtServer(level, entity.damageSources().magic(), damage);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % Math.max(20, 60 - amplifier * 10) == 0;
    }

}