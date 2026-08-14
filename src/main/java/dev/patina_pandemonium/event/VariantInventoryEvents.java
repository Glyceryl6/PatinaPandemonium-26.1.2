package dev.patina_pandemonium.event;

import dev.patina_pandemonium.PatinaPandemonium;
import dev.patina_pandemonium.advancement.VariantAdvancements;
import dev.patina_pandemonium.config.PatinaRules;
import dev.patina_pandemonium.registry.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

import static dev.patina_pandemonium.event.PatinaGameplayEvents.*;

/** Event handlers grouped by gameplay responsibility. */
@EventBusSubscriber(modid = PatinaPandemonium.MOD_ID)
public class VariantInventoryEvents {
    private static final Map<ServerPlayer, Integer> ADVANCEMENT_SCAN_CURSORS = new WeakHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        PatinaRules rules = PatinaRules.INSTANCE;
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) return;
        if (rules.enableAdvancements && Math.floorMod(player.tickCount + player.getId(), rules.advancementScanInterval) == 0) {
            int inventorySize = player.getInventory().getContainerSize();
            if (inventorySize > 0) {
                int advancementCursor = Math.floorMod(ADVANCEMENT_SCAN_CURSORS.getOrDefault(serverPlayer, 0), inventorySize);
                ADVANCEMENT_SCAN_CURSORS.put(serverPlayer, (advancementCursor + 1) % inventorySize);
                VariantAdvancements.evaluateItem(serverPlayer, player.getInventory().getItem(advancementCursor));
            }
            VariantAdvancements.evaluateItem(serverPlayer, player.getMainHandItem());
            VariantAdvancements.evaluateItem(serverPlayer, player.getOffhandItem());
        }
        if (player.tickCount % rules.inventoryOxidationInterval != 0
            || (!rules.inventoryOxidationAffectsCreative && player.getAbilities().instabuild)) return;
        BlockPos exposurePos = player.blockPosition().above();
        if ((rules.inventoryOxidationRequiresSky && !level.canSeeSky(exposurePos))
            || (rules.inventoryOxidationRequiresRain && !level.isRainingAt(exposurePos))) return;
        int size = player.getInventory().getContainerSize();
        if (size <= 0) return;
        int cursor = Math.floorMod(INVENTORY_OXIDATION_CURSORS.getOrDefault(player, 0), size);
        INVENTORY_OXIDATION_CURSORS.put(player, (cursor + 1) % size);
        if (level.getRandom().nextDouble() >= rules.inventoryOxidationAttemptChance) return;
        ItemStack oxidized = DynamicVariantRegistry.oxidizedCopy(player.getInventory().getItem(cursor));
        if (oxidized.isEmpty()) return;
        player.getInventory().setItem(cursor, oxidized);
        player.getInventory().setChanged();
        serverPlayer.containerMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) VariantAdvancements.evaluateItem(player, event.getCrafting());
    }

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) VariantAdvancements.evaluateItem(player, event.getSmelting());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack output = event.getOutput();
        if (output.isEmpty()) return;
        VariantProvenance.anvil(event.getLeft(), event.getRight(), output, event.getXpCost(), event.getMaterialCost(), event.getName());
        event.setOutput(output);
    }

    @SubscribeEvent
    public static void onPlayerEnchantItem(PlayerEnchantItemEvent event) {
        VariantProvenance.enchantingTable(event.getEnchantedItem(), event.getEnchantments());
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        VariantGenetics.Data genetics = VariantGenetics.get(event.getItemStack());
        if (genetics != null && PatinaRules.INSTANCE.showGeneticNames) {
            event.getToolTip().add(Component.translatable("tooltip.patina_pandemonium.genetics.systematic",
                VariantGenetics.systematicName(event.getItemStack(), genetics)).withStyle(ChatFormatting.DARK_AQUA));
            event.getToolTip().add(Component.translatable("tooltip.patina_pandemonium.genetics.pedigree",
                VariantGenetics.compactPedigree(genetics)).withStyle(ChatFormatting.DARK_GRAY));
            event.getToolTip().add(VariantGenetics.colorSummary(genetics).copy().withStyle(ChatFormatting.DARK_AQUA));
            VariantGenetics.TraitSummary traits = VariantGenetics.traitSummary(genetics);
            event.getToolTip().add(Component.translatable("tooltip.patina_pandemonium.genetics.fitness", traits.recessiveHomozygotes(),
                traits.recessiveCarriers(), traits.heterosisPermille(), traits.inbreedingDepressionPermille()).withStyle(
                    traits.recessiveHomozygotes() > 0 ? ChatFormatting.RED : traits.heterosisPermille() > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            VariantGenetics.FitnessEffects effects = VariantGenetics.fitnessEffects(genetics);
            event.getToolTip().add(Component.translatable("tooltip.patina_pandemonium.genetics.attributes",
                String.format(Locale.ROOT, "%+.1f%%", effects.healthMultiplier() * 100.0D),
                String.format(Locale.ROOT, "%+.1f%%", effects.movementMultiplier() * 100.0D),
                String.format(Locale.ROOT, "%+.1f%%", effects.attackMultiplier() * 100.0D),
                String.format(Locale.ROOT, "%+.2f", effects.armorDelta())).withStyle(ChatFormatting.DARK_GRAY));
            if (event.getFlags().isAdvanced()) {
                event.getToolTip().add(Component.translatable("tooltip.patina_pandemonium.genetics.meiosis", genetics.recombinations(),
                    genetics.mutations(), genetics.heterozygosityPermille(), genetics.inbreedingPermille()).withStyle(ChatFormatting.DARK_GRAY));
                event.getToolTip().add(Component.translatable("tooltip.patina_pandemonium.genetics.overdominance",
                    traits.overdominantHeterozygotes()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        if (!PatinaRules.INSTANCE.showProvenanceTooltip) return;
        VariantProvenance.Data data = VariantProvenance.get(event.getItemStack());
        if (data == null) return;
        event.getToolTip().add(Component.translatable("tooltip.patina_pandemonium.provenance.summary",
            data.generation(), data.nodes().size(), data.maximumDepth()).withStyle(ChatFormatting.DARK_GRAY));
        event.getToolTip().add(Component.translatable("tooltip.patina_pandemonium.provenance.fingerprint",
            VariantProvenance.shortFingerprint(data)).withStyle(ChatFormatting.DARK_GRAY));
        if (data.truncated()) event.getToolTip().add(Component.translatable("tooltip.patina_pandemonium.provenance.truncated")
            .withStyle(ChatFormatting.GOLD));
        if (!event.getFlags().isAdvanced()) return;
        int count = Math.min(PatinaRules.INSTANCE.maximumProvenanceTooltipNodes, data.nodes().size());
        for (int index = data.nodes().size() - count; index < data.nodes().size(); index++) {
            VariantProvenance.Node node = data.nodes().get(index);
            event.getToolTip().add(Component.translatable("tooltip.patina_pandemonium.provenance.operation", index, node.type().name().toLowerCase(Locale.ROOT),
                node.operation()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

}
