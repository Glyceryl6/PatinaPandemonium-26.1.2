package dev.patinapandemonium.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.patinapandemonium.config.PatinaRules;
import dev.patinapandemonium.registry.DynamicVariantRegistry;
import dev.patinapandemonium.registry.OxidationStage;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantForm;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Stream;

/** Gives any logical variant without pre-creating hundreds of thousands of registry entries. */
public class PatinaCommands {

    private static final String SOURCE_ARGUMENT = "source";
    private static final String FORM_ARGUMENT = "form";
    private static final String STAGE_ARGUMENT = "stage";
    private static final String WAXED_ARGUMENT = "waxed";
    private static final String DYE_ARGUMENT = "dye";
    private static final String COUNT_ARGUMENT = "count";
    private static final String NO_DYE = "none";
    private static final DynamicCommandExceptionType INVALID_SOURCE = new DynamicCommandExceptionType(
        source -> Component.translatable("commands.patina.invalid_source", source));
    private static final SimpleCommandExceptionType INVALID_FORM = new SimpleCommandExceptionType(
        Component.translatable("commands.patina.invalid_form"));
    private static final SimpleCommandExceptionType INVALID_STAGE = new SimpleCommandExceptionType(
        Component.translatable("commands.patina.invalid_stage"));
    private static final SimpleCommandExceptionType INVALID_DYE = new SimpleCommandExceptionType(
        Component.translatable("commands.patina.invalid_dye"));

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("patina")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("give")
                .then(Commands.argument(SOURCE_ARGUMENT, ResourceArgument.resource(event.getBuildContext(), Registries.BLOCK))
                    .suggests((_, builder) -> SharedSuggestionProvider.suggestResource(
                        DynamicVariantRegistry.sourceIds().stream(), builder))
                    .then(Commands.argument(FORM_ARGUMENT, StringArgumentType.word())
                        .suggests((_, builder) -> SharedSuggestionProvider.suggest(
                            Arrays.stream(VariantForm.values()).map(VariantForm::id), builder))
                        .then(Commands.argument(STAGE_ARGUMENT, StringArgumentType.word())
                            .suggests((_, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(OxidationStage.values()).map(OxidationStage::id), builder))
                            .then(Commands.argument(WAXED_ARGUMENT, BoolArgumentType.bool())
                                .then(Commands.argument(DYE_ARGUMENT, StringArgumentType.word())
                                    .suggests((_, builder) -> SharedSuggestionProvider.suggest(Stream.concat(
                                        Stream.of(NO_DYE), DyeColor.VALUES.stream().map(DyeColor::getSerializedName)), builder))
                                    .executes(context -> give(context, 1))
                                    .then(Commands.argument(COUNT_ARGUMENT, IntegerArgumentType.integer(1, 6_400))
                                        .executes(context -> give(context, IntegerArgumentType.getInteger(context, COUNT_ARGUMENT)))))))))));
    }

    private static int give(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        Holder.Reference<Block> source = ResourceArgument.getResource(context, SOURCE_ARGUMENT, Registries.BLOCK);
        Identifier sourceId = source.key().identifier();
        if (!DynamicVariantRegistry.isSource(sourceId, source.value(), PatinaRules.INSTANCE)) {
            throw INVALID_SOURCE.create(sourceId);
        }

        VariantForm form = form(StringArgumentType.getString(context, FORM_ARGUMENT));
        if (form == null) throw INVALID_FORM.create();
        OxidationStage stage = stage(StringArgumentType.getString(context, STAGE_ARGUMENT));
        if (stage == null) throw INVALID_STAGE.create();
        DyeColor dye = dye(StringArgumentType.getString(context, DYE_ARGUMENT));
        if (dye == null && !NO_DYE.equals(StringArgumentType.getString(context, DYE_ARGUMENT))) throw INVALID_DYE.create();
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack prototype = DynamicVariantRegistry.displayStack(new VariantData(
            sourceId, stage, BoolArgumentType.getBool(context, WAXED_ARGUMENT), form, dye));
        int remaining = count;
        int maximumStackSize = prototype.getMaxStackSize();
        while (remaining > 0) {
            int size = Math.min(maximumStackSize, remaining);
            remaining -= size;
            ItemStack stack = prototype.copyWithCount(size);
            boolean added = player.getInventory().add(stack);
            if (!added || !stack.isEmpty()) {
                ItemEntity dropped = player.drop(stack, false);
                if (dropped != null) {
                    dropped.setNoPickUpDelay();
                    dropped.setTarget(player.getUUID());
                }
            }
        }

        player.containerMenu.broadcastChanges();
        context.getSource().sendSuccess(() -> Component.translatable(
            "commands.patina.give.success",
                count, prototype.getDisplayName(),
                player.getDisplayName()), true);
        return count;
    }

    @Nullable
    private static VariantForm form(String value) {
        for (VariantForm form : VariantForm.values()) {
            if (form.id().equals(value)) return form;
        }
        return null;
    }

    @Nullable
    private static OxidationStage stage(String value) {
        for (OxidationStage stage : OxidationStage.values()) {
            if (stage.id().equals(value)) return stage;
        }
        return null;
    }

    @Nullable
    private static DyeColor dye(String value) {
        if (NO_DYE.equals(value)) return null;
        for (DyeColor dye : DyeColor.VALUES) {
            if (dye.getSerializedName().equals(value)) return dye;
        }
        return null;
    }

}