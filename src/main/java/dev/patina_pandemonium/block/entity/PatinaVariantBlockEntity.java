package dev.patina_pandemonium.block.entity;

import dev.patina_pandemonium.block.PatinaOxidizable;
import dev.patina_pandemonium.registry.DynamicVariantRegistry;
import dev.patina_pandemonium.registry.OxidationStage;
import dev.patina_pandemonium.registry.VariantData;
import dev.patina_pandemonium.registry.VariantForm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;

public class PatinaVariantBlockEntity extends BlockEntity {

    public static final ModelProperty<VariantData> MODEL_DATA = new ModelProperty<>();
    private static final String SOURCE_KEY = "source";
    private static final String STAGE_KEY = "stage";
    private static final String WAXED_KEY = "waxed";
    private static final String DYE_KEY = "dye";
    private static final String COLOR_KEY = "custom_color";
    private VariantData data;
    private ModelData modelData;

    public PatinaVariantBlockEntity(BlockPos pos, BlockState state) {
        super(DynamicVariantRegistry.VARIANT_BLOCK_ENTITY.get(), pos, state);
        this.data = defaultData(state);
        this.refreshModelData();
    }

    public VariantData data() {
        return this.data;
    }

    public void setData(VariantData data) {
        VariantData normalized = data.normalized(form(this.getBlockState()));
        if (this.data.equals(normalized)) return;
        this.data = normalized;
        this.refreshModelData();
        this.setChanged();
        Level level = this.getLevel();
        if (level == null) return;
        if (level.isClientSide()) {
            this.refreshClientModel();
        } else {
            BlockState state = this.getBlockState();
            level.sendBlockUpdated(this.getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Identifier fallback = defaultData(this.getBlockState()).sourceId();
        Identifier source = Identifier.tryParse(input.getStringOr(SOURCE_KEY, fallback.toString()));
        int customColor = input.getIntOr(COLOR_KEY, -1);
        this.data = new VariantData(
            source == null ? fallback : source,
            OxidationStage.byOrdinal(input.getIntOr(STAGE_KEY, 0)),
            input.getBooleanOr(WAXED_KEY, false),
            form(this.getBlockState()),
            VariantData.dyeById(input.getIntOr(DYE_KEY, -1)),
            customColor < 0 ? null : customColor);
        this.refreshModelData();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString(SOURCE_KEY, this.data.sourceId().toString());
        output.putInt(STAGE_KEY, this.data.stage().ordinal());
        output.putBoolean(WAXED_KEY, this.data.waxed());
        output.putInt(DYE_KEY, this.data.dyeId());
        if (this.data.customColor() == null) output.discard(COLOR_KEY);
        else output.putInt(COLOR_KEY, this.data.customColor());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
        this.refreshClientModel();
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        super.onDataPacket(connection, input);
        this.refreshClientModel();
    }

    @Override
    public ModelData getModelData() {
        return this.modelData;
    }

    private void refreshModelData() {
        this.modelData = ModelData.of(MODEL_DATA, this.data);
    }

    private void refreshClientModel() {
        this.requestModelDataUpdate();
        Level level = this.getLevel();
        if (level == null || !level.isClientSide()) return;
        BlockState state = this.getBlockState();
        level.sendBlockUpdated(this.getBlockPos(), state, state, Block.UPDATE_IMMEDIATE);
    }

    private static VariantData defaultData(BlockState state) {
        VariantForm form = form(state);
        Identifier source = DynamicVariantRegistry.sourceId(state.getBlock());
        return source == null ? VariantData.defaultFor(form) : new VariantData(source, OxidationStage.FRESH, false, form, null);
    }

    private static VariantForm form(BlockState state) {
        return state.getBlock() instanceof PatinaOxidizable oxidizable ? oxidizable.patinaForm() : VariantForm.FULL;
    }

}