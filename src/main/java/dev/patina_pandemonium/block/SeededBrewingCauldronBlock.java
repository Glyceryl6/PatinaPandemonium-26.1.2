package dev.patina_pandemonium.block;

import com.mojang.serialization.MapCodec;
import dev.patina_pandemonium.block.entity.SeededBrewingCauldronBlockEntity;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class SeededBrewingCauldronBlock extends BaseEntityBlock {

    public static final MapCodec<SeededBrewingCauldronBlock> CODEC = simpleCodec(SeededBrewingCauldronBlock::new);
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    private static final VoxelShape INSIDE = box(2.0D, 4.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    private static final VoxelShape SHAPE = Shapes.join(Shapes.block(), Shapes.or(
        box(0.0D, 0.0D, 4.0D, 16.0D, 3.0D, 12.0D),
        box(4.0D, 0.0D, 0.0D, 12.0D, 3.0D, 16.0D),
        box(2.0D, 0.0D, 2.0D, 14.0D, 3.0D, 14.0D), INSIDE), BooleanOp.ONLY_FIRST);

    public SeededBrewingCauldronBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 0));
    }

    @Override
    protected MapCodec<SeededBrewingCauldronBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof SeededBrewingCauldronBlockEntity cauldron)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        if (stack.is(Items.WATER_BUCKET)) {
            if (cauldron.liquidLevel() != 0) return this.message(player, "message.patina_pandemonium.seeded_brewing.not_empty");
            cauldron.fill(stack);
            this.updateLevel(level, pos, state, cauldron.liquidLevel());
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, Items.BUCKET.getDefaultInstance());
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS_SERVER;
        }

        if (stack.is(Items.NETHER_WART)) {
            if (cauldron.liquidLevel() <= 0) return this.message(player, "message.patina_pandemonium.seeded_brewing.needs_water");
            if (!cauldron.prime(serverPlayer, stack)) return this.message(player, "message.patina_pandemonium.seeded_brewing.already_primed");
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 0.9F);
            return InteractionResult.SUCCESS_SERVER;
        }

        if (stack.is(Items.GLASS_BOTTLE)) {
            ItemStack potion = cauldron.bottle(serverPlayer, stack);
            if (potion.isEmpty()) {
                String key = cauldron.primed() ? cauldron.brewing()
                    ? "message.patina_pandemonium.seeded_brewing.processing"
                    : "message.patina_pandemonium.seeded_brewing.needs_ingredient"
                    : "message.patina_pandemonium.seeded_brewing.needs_base";
                return this.message(player, key);
            }

            if (!player.getAbilities().instabuild) player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, potion));
            else if (!player.getInventory().add(potion)) player.drop(potion, false);
            this.updateLevel(level, pos, state, cauldron.liquidLevel());
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS_SERVER;
        }

        if (cauldron.liquidLevel() <= 0) return this.message(player, "message.patina_pandemonium.seeded_brewing.needs_water");
        if (!cauldron.primed()) return this.message(player, "message.patina_pandemonium.seeded_brewing.needs_base");
        if (cauldron.brewing()) return this.message(player, "message.patina_pandemonium.seeded_brewing.processing");
        if (!cauldron.addIngredient(stack)) return InteractionResult.PASS;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8F, 1.15F);
        return InteractionResult.SUCCESS_SERVER;
    }

    private InteractionResult message(Player player, String key) {
        player.sendOverlayMessage(Component.translatable(key));
        return InteractionResult.SUCCESS_SERVER;
    }

    private void updateLevel(Level level, BlockPos pos, BlockState state, int liquidLevel) {
        if (state.getValue(LEVEL) == liquidLevel) return;
        level.setBlock(pos, state.setValue(LEVEL, liquidLevel), Block.UPDATE_ALL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return INSIDE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SeededBrewingCauldronBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, DynamicVariantRegistry.SEEDED_BREWING_CAULDRON_BLOCK_ENTITY.get(), SeededBrewingCauldronBlockEntity::serverTick);
    }

}