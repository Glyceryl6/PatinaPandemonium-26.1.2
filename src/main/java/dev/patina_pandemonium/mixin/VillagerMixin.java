package dev.patina_pandemonium.mixin;

import dev.patina_pandemonium.advancement.VariantAdvancements;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.OxidationStage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public class VillagerMixin {

    @Unique
    private @Nullable MerchantOffers patina$baseOffers;

    @Inject(method = "setTradingPlayer", at = @At("HEAD"))
    private void patina$preparePlayerOffers(@Nullable Player player, CallbackInfo callback) {
        Villager villager = (Villager) (Object) this;
        if (villager.level().isClientSide()) return;
        if (player == null) {
            this.patina$restoreOffers(villager);
            return;
        }
        if (this.patina$baseOffers != null) this.patina$restoreOffers(villager);
        MerchantOffers baseOffers = villager.getOffers().copy();
        this.patina$baseOffers = baseOffers;
        MerchantOffers playerOffers = baseOffers.copy();
        PatinaRules rules = PatinaRules.INSTANCE;
        int reputation = Math.max(0, villager.getPlayerReputation(player));
        double reputationMultiplier = Math.max(
            rules.villagerMinimumChanceMultiplier, 1.0D - reputation * rules.villagerReputationChanceReduction);
        double replacementChance = rules.villagerVariantTradeChance * reputationMultiplier;
        long day = villager.level().getGameTime() / 24_000L;
        long seed = villager.getUUID().getMostSignificantBits() ^ villager.getUUID().getLeastSignificantBits()
            ^ Long.rotateLeft(player.getUUID().getMostSignificantBits(), 17)
            ^ Long.rotateLeft(player.getUUID().getLeastSignificantBits(), 41) ^ day;
        RandomSource random = RandomSource.create(seed);
        boolean variantOffer = false;
        for (MerchantOffer offer : playerOffers) {
            if (random.nextDouble() >= replacementChance) continue;
            OxidationStage stage = this.patina$randomStage(random, rules.villagerVariantStageWeights);
            boolean waxed = stage == OxidationStage.FRESH || random.nextDouble() < rules.villagerWaxChance;
            ItemStack transformed = DynamicVariantRegistry.transform(offer.getResult(), stage, waxed, null);
            if (transformed.isEmpty()) continue;
            offer.result = transformed;
            variantOffer = true;
            int discount = Math.max(1, (int) Math.floor(offer.getCostA().getCount() * rules.villagerVariantDiscount));
            offer.addToSpecialPriceDiff(-discount);
        }
        if (variantOffer && player instanceof ServerPlayer serverPlayer) {
            VariantAdvancements.interaction(serverPlayer, VariantAdvancements.Metric.VARIANT_TRADE);
        }
        villager.setOffers(playerOffers);
    }

    @Unique
    private void patina$restoreOffers(Villager villager) {
        if (this.patina$baseOffers == null) return;
        MerchantOffers currentOffers = villager.getOffers();
        MerchantOffers restoredOffers = this.patina$baseOffers.copy();
        for (int index = 0; index < Math.min(restoredOffers.size(), currentOffers.size()); index++) {
            MerchantOffer restored = restoredOffers.get(index);
            MerchantOffer current = currentOffers.get(index);
            restored.uses = current.getUses();
            restored.demand = current.getDemand();
        }
        villager.setOffers(restoredOffers);
        this.patina$baseOffers = null;
    }

    @Unique
    private OxidationStage patina$randomStage(RandomSource random, double[] weights) {
        double total = 0.0D;
        for (double weight : weights) total += weight;
        if (total <= 0.0D) return OxidationStage.EXPOSED;
        double selected = random.nextDouble() * total;
        for (int index = 0; index < weights.length; index++) {
            selected -= weights[index];
            if (selected <= 0.0D) return OxidationStage.byOrdinal(index);
        }
        return OxidationStage.OXIDIZED;
    }

}