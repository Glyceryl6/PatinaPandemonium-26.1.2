package dev.patina_pandemonium.event;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.advancement.VariantAdvancements;
import dev.patina_pandemonium.registry.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;

import java.util.*;

import static dev.patina_pandemonium.event.PatinaGameplayEvents.*;

/** Event handlers grouped by gameplay responsibility. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID)
public class VariantBreedingEvents {

    @SubscribeEvent
    public static void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        AgeableMob child = event.getChild();
        if (child == null || !(child.level() instanceof ServerLevel)) return;
        Mob parentAlpha = event.getParentA();
        Mob parentBeta = event.getParentB();
        if (!hasHeritableVariant(parentAlpha) && !hasHeritableVariant(parentBeta)) return;

        VariantGenetics.initialize(parentAlpha);
        VariantGenetics.initialize(parentBeta);
        VariantGenetics.applyGeneticEffects(parentAlpha);
        VariantGenetics.applyGeneticEffects(parentBeta);
        VariantProvenance.Data alphaProvenance = parentAlpha.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_PROVENANCE.get());
        VariantProvenance.Data betaProvenance = parentBeta.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_PROVENANCE.get());
        if (alphaProvenance == null) {
            alphaProvenance = VariantProvenance.entitySource(parentAlpha);
            parentAlpha.setData(DynamicVariantRegistry.ENTITY_PROVENANCE.get(), alphaProvenance);
        }
        if (betaProvenance == null) {
            betaProvenance = VariantProvenance.entitySource(parentBeta);
            parentBeta.setData(DynamicVariantRegistry.ENTITY_PROVENANCE.get(), betaProvenance);
        }

        VariantGenetics.Data genetics = VariantGenetics.breed(parentAlpha, parentBeta, child, child.getRandom());
        ItemVariantData alphaVariant = parentAlpha.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        ItemVariantData betaVariant = parentBeta.getExistingDataOrNull(DynamicVariantRegistry.ENTITY_VARIANT_DATA.get());
        ItemStack childPick = child.getPickResult();
        Identifier fallbackSource = childPick == null || childPick.isEmpty()
            ? ItemVariantData.defaultData().sourceId() : BuiltInRegistries.ITEM.getKey(childPick.getItem());
        ItemVariantData phenotype = VariantGenetics.phenotype(genetics, alphaVariant, betaVariant, fallbackSource, fallbackSource);
        child.setData(DynamicVariantRegistry.ENTITY_GENETICS.get(), genetics);
        setEntityVariant(child, phenotype);
        VariantGenetics.applyGeneticEffects(child);
        if (event.getCausedByPlayer() instanceof ServerPlayer player) {
            VariantAdvancements.evaluateGenetics(player, genetics);
        }

        CraftingChemistry.Data chemistry = breedChemistry(parentAlpha, parentBeta, childPick, phenotype);
        if (chemistry != null) child.setData(DynamicVariantRegistry.ENTITY_CHEMISTRY.get(), chemistry);
        child.setData(DynamicVariantRegistry.ENTITY_PROVENANCE.get(), VariantProvenance.breed(alphaProvenance, betaProvenance, child, genetics));
    }

}