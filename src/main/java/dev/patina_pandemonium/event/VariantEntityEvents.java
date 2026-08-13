package dev.patina_pandemonium.event;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.*;

import static dev.patina_pandemonium.event.PatinaGameplayEvents.*;

/** Event handlers grouped by gameplay responsibility. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID)
public class VariantEntityEvents {

    private static final long LIGHTNING_CLEAN_PROTECTION_TICKS = 40L;

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living
            && entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_GENETICS.get()) != null) VariantGenetics.applyGeneticEffects(living);
        if (event.loadedFromDisk()) return;
        if (entity instanceof LightningBolt lightning) {
            BlockPos strikePos = BlockPos.containing(lightning.getX(), lightning.getY() - 1.0E-6D, lightning.getZ());
            PENDING_LIGHTNING_STRIKES.computeIfAbsent(level, _ -> new ArrayList<>())
                .add(new PendingLightningStrike(level.getGameTime() + 1L, strikePos));
            return;
        }

        if (entity instanceof Player || entity instanceof ExperienceOrb) return;
        ItemStack sourceStack = entity instanceof AbstractArrow arrow ? arrow.getPickupItemStackOrigin()
            : entity instanceof ItemSupplier supplier ? supplier.getItem() : ItemStack.EMPTY;
        ItemVariantData data = DynamicVariantRegistry.variantUseData(sourceStack);
        CraftingChemistry.Data chemistry = sourceStack.get(DynamicVariantRegistry.CRAFTING_CHEMISTRY.get());
        VariantProvenance.Data provenance = VariantProvenance.get(sourceStack);
        VariantGenetics.Data genetics = VariantGenetics.get(sourceStack);
        if (data == null) data = currentVariantUse();
        if (chemistry == null) chemistry = currentChemistryUse();
        if (provenance == null) provenance = currentProvenance();
        if (genetics == null) genetics = currentGeneticsUse();
        if (data == null && chemistry == null && provenance == null && genetics == null) return;
        if (entity instanceof ItemEntity itemEntity) {
            if (data == null) return;
            ItemStack transformed = DynamicVariantRegistry.transform(itemEntity.getItem(), data.stage(), data.waxed(), data.dyeColor(), data.customColor());
            if (!transformed.isEmpty()) itemEntity.setItem(transformed);
            return;
        }
        if (data != null && entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get()) == null) setEntityVariant(entity, data);
        if (chemistry != null && entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_CHEMISTRY.get()) == null) {
            CraftingChemistry.Data attached = data == null ? chemistry
                : CraftingChemistry.retarget(chemistry, data.stage(), data.waxed(), data.dyeColor(), data.customColor());
            entity.setData(DynamicVariantRegistry.ENTITY_CHEMISTRY.get(), attached);
        }
        if (provenance != null && entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_PROVENANCE.get()) == null) {
            entity.setData(DynamicVariantRegistry.ENTITY_PROVENANCE.get(), provenance);
        }
        if (genetics != null && entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_GENETICS.get()) == null) {
            entity.setData(DynamicVariantRegistry.ENTITY_GENETICS.get(), genetics);
            if (entity instanceof LivingEntity living) VariantGenetics.applyGeneticEffects(living);
        }
    }

    @SubscribeEvent
    public static void onMobPositionCheck(MobSpawnEvent.PositionCheck event) {
        EntitySpawnReason reason = event.getSpawnType();
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) return;
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get()) != null
            || !level.isRainingAt(entity.blockPosition()) || level.getRandom().nextDouble() >= PatinaRules.INSTANCE.naturalVariantSpawnChance) return;
        ItemVariantData base = ItemVariantData.defaultData();
        entity.setData(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get(), new ItemVariantData(
            base.sourceId(), randomNaturalStage(level), false, null, base.modelId()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityStruckByLightning(EntityStruckByLightningEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel)) return;
        if (entity instanceof LivingEntity living) {
            LightningCleanProtection protection = LIGHTNING_CLEAN_PROTECTION.get(living);
            if (protection != null) {
                if (living.level().getGameTime() <= protection.expiresAt() && protection.lightningId() == event.getLightning().getId()) {
                    living.clearFire();
                    event.setCanceled(true);
                    return;
                }
                LIGHTNING_CLEAN_PROTECTION.remove(living);
            }
        }

        if (entity instanceof Player player) {
            boolean inventoryChanged = false;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack cleaned = DynamicVariantRegistry.cleanOxidationCopy(player.getInventory().getItem(slot));
                if (cleaned.isEmpty()) continue;
                player.getInventory().setItem(slot, cleaned);
                inventoryChanged = true;
            }

            if (inventoryChanged) player.getInventory().setChanged();
        }

        ItemVariantData entityData = entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        if (entityData == null || entityData.waxed() || entityData.stage() == OxidationStage.FRESH) return;
        setEntityVariant(entity, entityData.withStage(OxidationStage.FRESH));
        if (entity instanceof LivingEntity living) {
            living.clearFire();
            LIGHTNING_CLEAN_PROTECTION.put(living, new LightningCleanProtection(event.getLightning().getId(), living.level().getGameTime() + LIGHTNING_CLEAN_PROTECTION_TICKS));
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        LightningCleanProtection protection = LIGHTNING_CLEAN_PROTECTION.get(entity);
        if (protection == null) return;
        if (entity.level().getGameTime() > protection.expiresAt()) {
            LIGHTNING_CLEAN_PROTECTION.remove(entity);
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        if (event.getSource().is(DamageTypes.LIGHTNING_BOLT)
            && (!(direct instanceof LightningBolt lightning) || lightning.getId() == protection.lightningId())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel) || event.getInflictedDamage() <= 0.0F) return;
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        Entity directSource = event.getSource().getDirectEntity();
        ItemStack weapon = attacker == null ? ItemStack.EMPTY : attacker.getMainHandItem();
        boolean directAttack = attacker != null && (directSource == null || directSource == attacker);
        ItemVariantData weaponData = directAttack && (weapon.has(DataComponents.WEAPON) || weapon.has(DataComponents.TOOL))
            ? DynamicVariantRegistry.variantUseData(weapon) : null;
        ItemVariantData data = strongestVariant(
            attacker == null ? null : attacker.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get()),
            strongestVariant(
                directSource == null ? null : directSource.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get()),
                weaponData));
        if (data == null || data.stage() == OxidationStage.FRESH) return;
        int stage = data.stage().ordinal();
        double chance = PatinaRules.INSTANCE.tetanusChances[stage]
            * (data.waxed() ? PatinaRules.INSTANCE.waxTetanusMultiplier : 1.0D);
        if (event.getEntity().getRandom().nextDouble() >= chance) return;
        event.getEntity().addEffect(new MobEffectInstance(
            DynamicVariantRegistry.TETANUS, PatinaRules.INSTANCE.tetanusDurations[stage], Math.max(0, stage - 2)));
    }

    @SubscribeEvent
    public static void onLivingUseItemFinished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        ItemStack consumed = event.getItem();
        ItemVariantData data = DynamicVariantRegistry.variantUseData(consumed);
        if (data == null || data.stage() == OxidationStage.FRESH || !consumed.has(DataComponents.FOOD)) return;
        int stage = data.stage().ordinal();
        double chance = PatinaRules.INSTANCE.foodPoisonChances[stage]
            * (data.waxed() ? PatinaRules.INSTANCE.waxFoodRiskMultiplier : 1.0D);
        if (event.getEntity().getRandom().nextDouble() >= chance) return;
        event.getEntity().addEffect(new MobEffectInstance(
            MobEffects.POISON, PatinaRules.INSTANCE.foodPoisonDurations[stage], Math.max(0, stage - 2)));
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        ItemVariantData data = event.getEntity().getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        if (data == null) return;
        for (ItemEntity drop : event.getDrops()) {
            ItemStack transformed = DynamicVariantRegistry.transform(
                drop.getItem(), data.stage(), data.waxed(), data.dyeColor(), data.customColor());
            if (!transformed.isEmpty()) drop.setItem(transformed);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!entity.isOnFire() && entity.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_FIRE_VARIANT_DATA.get()) != null) {
            entity.removeData(DynamicVariantRegistry.ENTITY_FIRE_VARIANT_DATA.get());
        }
        if (!(entity instanceof LivingEntity living)) return;
        PatinaRules rules = PatinaRules.INSTANCE;
        if (Math.floorMod(living.tickCount + living.getId(), rules.entityOxidationInterval) != 0) return;
        ItemVariantData data = living.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        if (data != null && (data.waxed() || data.stage() == OxidationStage.OXIDIZED)) return;
        ItemVariantData base = data == null ? ItemVariantData.defaultData() : data;
        double chance = rules.entityOxidationAttemptChance;
        VariantGenetics.Data genetics = living.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_GENETICS.get());
        if (genetics != null) chance *= VariantGenetics.oxidationRateMultiplier(genetics);
        if (base.stage() == OxidationStage.FRESH) chance *= 0.75D;
        if (level.isRainingAt(living.blockPosition().above())) chance *= rules.entityOxidationRainMultiplier;
        if (level.getRandom().nextDouble() >= Math.min(1.0D, chance)) return;
        OxidationStage next = base.stage().next();
        if (next != null) setEntityVariant(living, base.withStage(next));
    }

}
