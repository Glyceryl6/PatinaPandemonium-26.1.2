package dev.patinapandemonium.block;

import dev.patinapandemonium.block.entity.PatinaVariantBlockEntity;
import dev.patinapandemonium.registry.VariantData;
import dev.patinapandemonium.registry.VariantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** One registry carrier per non-full source block. Its state and common behavior are delegated to the source. */
public class PatinaDelegatingBlock extends Block implements PatinaOxidizable {

    private static final ThreadLocal<Block> CONSTRUCTION_SOURCE = new ThreadLocal<>();
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return this.sourceState(state).isPathfindable(type);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        BlockState updated = this.sourceState(state).updateShape(level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
        return updated.is(this.source) ? this.carrierState(updated) : updated;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return this.sourceState(state).canSurvive(level, pos);
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
        return this.sourceState(state).getSignal(level, pos, direction);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.sourceState(state).getDirectSignal(level, pos, direction);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return this.sourceState(state).hasAnalogOutputSignal();
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return this.sourceState(state).getAnalogOutputSignal(level, pos, direction);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        VariantData data = this.data(level, pos);
        this.sourceState(state).randomTick(level, pos, random);
        this.restore(level, pos, data);
        this.patinaRandomTick(level, pos, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        VariantData data = this.data(level, pos);
        this.sourceState(state).tick(level, pos, random);
        this.restore(level, pos, data);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        VariantData data = this.data(level, pos);
        InteractionResult result = this.sourceState(state).useWithoutItem(level, player, hit);
        this.restoreAround(level, pos, data);
        return result;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        VariantData data = this.data(level, pos);
        InteractionResult result = this.sourceState(state).useItemOn(stack, level, player, hand, hit);
        this.restoreAround(level, pos, data);
        return result;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean moving) {
        this.sourceState(state).entityInside(level, pos, entity, effects, moving);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        this.source.animateTick(this.sourceState(state), level, pos, random);
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        this.sourceState(state).attack(level, pos, player);
    }

    @Nullable
    private VariantData data(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity ? blockEntity.data() : null;
    }

    private void restoreAround(Level level, BlockPos pos, @Nullable VariantData data) {
        this.restore(level, pos, data);
        for (Direction direction : Direction.values()) this.restore(level, pos.relative(direction), data);
    }

    private void restore(Level level, BlockPos pos, @Nullable VariantData data) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this.source)) return;
        level.setBlock(pos, this.carrierState(state), Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        if (data != null && level.getBlockEntity(pos) instanceof PatinaVariantBlockEntity blockEntity) blockEntity.setData(data);
    }

}