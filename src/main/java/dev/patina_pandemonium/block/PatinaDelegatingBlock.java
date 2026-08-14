package dev.patina_pandemonium.block;

import dev.patina_pandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patina_pandemonium.event.PatinaGameplayEvents;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.VariantData;
import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;

/** State-preserving carrier for delegated and stateful full source blocks. */
public class PatinaDelegatingBlock extends Block implements PatinaOxidizable {

    private static final ThreadLocal<Block> CONSTRUCTION_SOURCE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> SOURCE_VIEW_DEPTH = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<SourceView>> EXTERNAL_SOURCE_VIEWS = new ThreadLocal<>();
    private final Block source;

    public static PatinaDelegatingBlock create(Block source, BlockBehaviour.Properties properties) {
        CONSTRUCTION_SOURCE.set(source);
        try {
            return new PatinaDelegatingBlock(source, properties);
        } finally {
            CONSTRUCTION_SOURCE.remove();
        }
    }

    private PatinaDelegatingBlock(Block source, BlockBehaviour.Properties properties) {
        super(properties);
        this.source = source;
        this.registerDefaultState(this.withPropertiesOf(source.defaultBlockState()));
    }

    public Block source() {
        return this.source;
    }

    public BlockState sourceState(BlockState state) {
        return this.source.withPropertiesOf(state);
    }

    public BlockState carrierState(BlockState state) {
        return this.withPropertiesOf(state);
    }

    public static BlockState sourceView(BlockState state) {
        Integer depth = SOURCE_VIEW_DEPTH.get();
        return depth != null && depth > 0 && state.getBlock() instanceof PatinaDelegatingBlock delegated
            ? delegated.sourceState(state) : state;
    }

    public static BlockState sourceView(BlockPos pos, BlockState state) {
        ArrayDeque<SourceView> views = EXTERNAL_SOURCE_VIEWS.get();
        if (views != null) {
            for (SourceView view : views) {
                if (view.pos().equals(pos) && state.is(view.carrierState().getBlock())) return view.sourceState();
            }
        }
        return sourceView(state);
    }

    public static void beginExternalSourceView(BlockPos pos, BlockState sourceState, BlockState carrierState) {
        ArrayDeque<SourceView> views = EXTERNAL_SOURCE_VIEWS.get();
        if (views == null) {
            views = new ArrayDeque<>();
            EXTERNAL_SOURCE_VIEWS.set(views);
        }
        views.push(new SourceView(pos.immutable(), sourceState, carrierState));
    }

    public static void endExternalSourceView() {
        ArrayDeque<SourceView> views = EXTERNAL_SOURCE_VIEWS.get();
        if (views == null) return;
        if (!views.isEmpty()) views.pop();
        if (views.isEmpty()) EXTERNAL_SOURCE_VIEWS.remove();
    }

    public static BlockState preserveSourceWrite(Level level, BlockPos pos, BlockState state) {
        ArrayDeque<SourceView> views = EXTERNAL_SOURCE_VIEWS.get();
        if (views != null) {
            for (SourceView view : views) {
                if (view.pos().equals(pos) && state.is(view.sourceState().getBlock())) return view.carrierState();
            }
        }

        Integer depth = SOURCE_VIEW_DEPTH.get();
        if (depth == null || depth <= 0) return state;
        BlockState current = level.getChunkAt(pos).getBlockState(pos);
        if (!(current.getBlock() instanceof PatinaDelegatingBlock delegated) || !state.is(delegated.source)) return state;
        return delegated.carrierState(state);
    }

    private static void beginSourceView() {
        Integer depth = SOURCE_VIEW_DEPTH.get();
        SOURCE_VIEW_DEPTH.set(depth == null ? 1 : depth + 1);
    }

    private static void endSourceView() {
        Integer depth = SOURCE_VIEW_DEPTH.get();
        if (depth == null || depth <= 1) SOURCE_VIEW_DEPTH.remove();
        else SOURCE_VIEW_DEPTH.set(depth - 1);
    }

    @Override
    public VariantForm patinaForm() {
        return VariantForm.FULL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        Block source = CONSTRUCTION_SOURCE.get();
        if (source == null) return;
        Property<?>[] properties = source.getStateDefinition().getProperties().toArray(Property[]::new);
        builder.add(properties);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.source.getStateForPlacement(context);
        return state == null ? null : this.carrierState(state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        VariantData data = DynamicVariantRegistry.data(stack, VariantForm.FULL);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upperPos = pos.above();
            level.setBlock(upperPos, state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            if (level.getBlockEntity(upperPos) instanceof PatinaVariantBlockEntity blockEntity) blockEntity.setData(data);
            return;
        }

        this.source.setPlacedBy(level, pos, this.sourceState(state), placer, stack);
        this.restoreAround(level, pos, data);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return this.sourceState(state).isPathfindable(type);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        BlockState sourceNeighborState = neighborState.is(this) ? this.sourceState(neighborState) : neighborState;
        BlockState updated = this.sourceState(state).updateShape(level, scheduledTickAccess, pos, direction, neighborPos, sourceNeighborState, random);
        return updated.is(this.source) ? this.carrierState(updated) : updated;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
            && level.getBlockState(pos.below()).is(this)) return true;
        return this.sourceState(state).canSurvive(level, pos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return this.sourceState(state).getFluidState();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return this.sourceState(state).getRenderShape();
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        BlockState sourceNeighbor = neighborState.is(this) ? this.sourceState(neighborState) : neighborState;
        return this.sourceState(state).skipRendering(sourceNeighbor, direction);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.sourceState(state).getShape(level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.sourceState(state).getCollisionShape(level, pos, context);
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return this.sourceState(state).getEntityInsideCollisionShape(level, pos, entity);
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return this.sourceState(state).getBlockSupportShape(level, pos);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.sourceState(state).getVisualShape(level, pos, context);
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return this.sourceState(state).getInteractionShape(level, pos);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return this.sourceState(state).isSignalSource();
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        beginSourceView();
        try {
            return this.sourceState(state).getSignal(level, pos, direction);
        } finally {
            endSourceView();
        }
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        beginSourceView();
        try {
            return this.sourceState(state).getDirectSignal(level, pos, direction);
        } finally {
            endSourceView();
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return this.sourceState(state).hasAnalogOutputSignal();
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        beginSourceView();
        try {
            return this.sourceState(state).getAnalogOutputSignal(level, pos, direction);
        } finally {
            endSourceView();
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide() || !(this.source instanceof BaseFireBlock)) return;
        VariantData data = this.data(level, pos);
        PatinaGameplayEvents.beginVariantUse(data);
        beginSourceView();
        try {
            this.sourceState(state).onPlace(level, pos, sourceView(oldState), movedByPiston);
        } finally {
            endSourceView();
            PatinaGameplayEvents.endVariantUse();
        }

        this.restoreAround(level, pos, data);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        VariantData data = this.data(level, pos);
        PatinaGameplayEvents.beginVariantUse(data);
        beginSourceView();
        try {
            this.sourceState(state).randomTick(level, pos, random);
        } finally {
            endSourceView();
            PatinaGameplayEvents.endVariantUse();
        }

        this.restore(level, pos, data);
        this.patinaRandomTick(level, pos, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        VariantData data = this.data(level, pos);
        PatinaGameplayEvents.beginVariantUse(data);
        beginSourceView();
        try {
            this.sourceState(state).tick(level, pos, random);
        } finally {
            endSourceView();
            PatinaGameplayEvents.endVariantUse();
        }

        this.restore(level, pos, data);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (this.source instanceof DoorBlock door) return this.useDoor(state, level, pos, player, door);
        VariantData data = this.data(level, pos);
        PatinaGameplayEvents.beginVariantUse(data);
        beginSourceView();
        InteractionResult result;
        try {
            result = this.sourceState(state).useWithoutItem(level, player, hit);
        } finally {
            endSourceView();
            PatinaGameplayEvents.endVariantUse();
        }

        this.restoreAround(level, pos, data);
        return result;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        VariantData data = this.data(level, pos);
        PatinaGameplayEvents.beginVariantUse(data);
        beginSourceView();
        InteractionResult result;
        try {
            result = this.sourceState(state).useItemOn(stack, level, player, hand, hit);
        } finally {
            endSourceView();
            PatinaGameplayEvents.endVariantUse();
        }

        this.restoreAround(level, pos, data);
        return result;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean moving) {
        VariantData data = this.data(level, pos);
        PatinaGameplayEvents.beginVariantUse(data);
        beginSourceView();
        try {
            this.sourceState(state).entityInside(level, pos, entity, effects, moving);
        } finally {
            endSourceView();
            PatinaGameplayEvents.endVariantUse();
        }

        if (data != null && entity.isOnFire()) PatinaGameplayEvents.applyVariantFire(entity, data);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        this.source.animateTick(this.sourceState(state), level, pos, random);
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        this.sourceState(state).attack(level, pos, player);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (this.source instanceof DoorBlock door && state.hasProperty(BlockStateProperties.POWERED)
            && state.hasProperty(BlockStateProperties.OPEN) && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            boolean signal = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.relative(half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN));
            if (block == this || signal == state.getValue(BlockStateProperties.POWERED)) return;
            if (signal != state.getValue(BlockStateProperties.OPEN)) this.playDoorSound(null, level, pos, door, signal);
            this.setDoorState(level, pos, state, signal, signal);
            return;
        }

        VariantData data = this.data(level, pos);
        PatinaGameplayEvents.beginVariantUse(data);
        beginSourceView();
        try {
            this.sourceState(state).handleNeighborChanged(level, pos, block == this ? this.source : block, orientation, movedByPiston);
        } finally {
            endSourceView();
            PatinaGameplayEvents.endVariantUse();
        }

        this.restoreAround(level, pos, data);
    }

    private InteractionResult useDoor(BlockState state, Level level, BlockPos pos, Player player, DoorBlock door) {
        if (!door.type().canOpenByHand()) return InteractionResult.PASS;
        boolean open = !state.getValue(BlockStateProperties.OPEN);
        this.setDoorState(level, pos, state, open, state.getValue(BlockStateProperties.POWERED));
        this.playDoorSound(player, level, pos, door, open);
        return InteractionResult.SUCCESS;
    }

    private void setDoorState(Level level, BlockPos pos, BlockState state, boolean open, boolean powered) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState changed = state.setValue(BlockStateProperties.OPEN, open).setValue(BlockStateProperties.POWERED, powered);
        level.setBlock(pos, changed, flags);
        DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
        BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState otherState = level.getBlockState(otherPos);
        if (!otherState.is(this) || !otherState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            || otherState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == half) return;
        level.setBlock(otherPos, otherState.setValue(BlockStateProperties.OPEN, open).setValue(BlockStateProperties.POWERED, powered), flags);
    }

    private void playDoorSound(@Nullable Entity sourceEntity, Level level, BlockPos pos, DoorBlock door, boolean open) {
        level.playSound(sourceEntity, pos, open ? door.type().doorOpen() : door.type().doorClose(), SoundSource.BLOCKS,
            1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        level.gameEvent(sourceEntity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !(this.source instanceof FireBlock) || !type.equals(DynamicVariantRegistry.VARIANT_BLOCK_ENTITY.get())) return null;
        return (tickerLevel, pos, tickerState, blockEntity) -> {
            if (!(tickerLevel instanceof ServerLevel serverLevel) || !tickerState.is(this)) return;
            int interval = 30 + Math.floorMod(Long.hashCode(pos.asLong()), 10);
            if (Math.floorMod(serverLevel.getGameTime() + pos.asLong(), interval) != 0) return;
            this.tickVariantFire(tickerState, serverLevel, pos, serverLevel.getRandom());
        };
    }

    private void tickVariantFire(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        VariantData data = this.data(level, pos);
        if (data == null || !(this.source instanceof FireBlock)) return;
        PatinaGameplayEvents.beginVariantUse(data);
        beginSourceView();
        try {
            this.sourceState(state).tick(level, pos, random);
        } finally {
            endSourceView();
            PatinaGameplayEvents.endVariantUse();
        }

        this.restoreAround(level, pos, data);
    }

    @Nullable
    private VariantData data(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity ? blockEntity.data() : null;
    }

    private void restoreAround(Level level, BlockPos pos, @Nullable VariantData data) {
        this.restore(level, pos, data);
        for (Direction direction : Direction.values()) {
            this.restore(level, pos.relative(direction), data);
        }
    }

    private void restore(Level level, BlockPos pos, @Nullable VariantData data) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this.source)) return;
        level.setBlock(pos, this.carrierState(state), Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        if (data != null && level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) blockEntity.setData(data);
    }

    private record SourceView(BlockPos pos, BlockState sourceState, BlockState carrierState) {}

}