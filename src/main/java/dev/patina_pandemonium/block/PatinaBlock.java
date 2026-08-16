package dev.patina_pandemonium.block;

import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.event.PatinaGameplayEvents;
import dev.patina_pandemonium.registry.CraftingWorkstationContext;
import dev.patina_pandemonium.registry.VariantData;
import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SpreadingSnowyBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Full-block carrier that preserves source interactions without multiplying source block states. */
public class PatinaBlock extends Block implements PatinaOxidizable {

    private final Block source;

    public static PatinaBlock create(Block source, BlockBehaviour.Properties properties) {
        return new PatinaBlock(source, properties);
    }

    private PatinaBlock(Block source, BlockBehaviour.Properties properties) {
        super(properties);
        this.source = source;
    }

    public Block source() {
        return this.source;
    }

    @Override
    public VariantForm patinaForm() {
        return VariantForm.FULL;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return this.source.defaultBlockState().isSignalSource();
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.source.defaultBlockState().getSignal(level, pos, direction);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.source.defaultBlockState().getDirectSignal(level, pos, direction);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return !(this.source instanceof EntityBlock) && this.source.defaultBlockState().hasAnalogOutputSignal();
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return this.source instanceof EntityBlock ? 0 : this.source.defaultBlockState().getAnalogOutputSignal(level, pos, direction);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (this.source instanceof EntityBlock) return;
        VariantData data = this.data(level, pos);
        PatinaGameplayEvents.beginVariantUse(data);
        try {
            this.source.defaultBlockState().handleNeighborChanged(level, pos, block == this ? this.source : block, orientation, movedByPiston);
        } finally {
            PatinaGameplayEvents.endVariantUse();
        }

        this.restore(level, pos, data);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.source instanceof SpreadingSnowyBlock) {
            VariantData data = this.data(level, pos);
            PatinaGameplayEvents.beginVariantUse(data);
            try {
                this.source.defaultBlockState().randomTick(level, pos, random);
            } finally {
                PatinaGameplayEvents.endVariantUse();
            }
        }

        if (level.getBlockState(pos).is(this)) {
            this.patinaRandomTick(level, pos, random);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        VariantData data = this.data(level, pos);
        PatinaGameplayEvents.beginVariantUse(data);
        InteractionResult result;
        try {
            result = this.source.defaultBlockState().useWithoutItem(level, player, hit);
        } finally {
            PatinaGameplayEvents.endVariantUse();
        }

        CraftingWorkstationContext.capture(player, level, pos, this.source);
        this.restore(level, pos, data);
        return result;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        VariantData data = this.data(level, pos);
        PatinaGameplayEvents.beginVariantUse(data);
        InteractionResult result;
        try {
            result = this.source.defaultBlockState().useItemOn(stack, level, player, hand, hit);
        } finally {
            PatinaGameplayEvents.endVariantUse();
        }

        this.restore(level, pos, data);
        return result;
    }

    @Nullable
    private VariantData data(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity ? blockEntity.data() : null;
    }

    private void restore(Level level, BlockPos pos, @Nullable VariantData data) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this.source)) return;
        level.setBlock(pos, this.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        if (data != null && level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) blockEntity.setData(data);
    }

}