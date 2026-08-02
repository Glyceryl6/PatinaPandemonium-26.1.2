# Implementation notes

## State graph

The supplied copper-block reference is used as the behavioral baseline: four unwaxed oxidation stages, a waxed mirror for every stage, honeycomb waxing, axe scraping/de-waxing, and the usual copper-family crafting relationship.

Every generated block implementing `PatinaOxidizable` delegates its next-state lookup to `VariantRuntime`. Waxed blocks never random-tick into another stage. NeoForge's built-in `oxidizables` and `waxables` data maps cover item interactions, including source blocks and reused existing forms.

## Dynamic registration

`DynamicVariantRegistry` snapshots the block registry during this mod's lowest-priority block `RegisterEvent`. It filters full collision cubes, reuses conventionally named forms, registers missing blocks, then registers their items during the item event. Generated IDs are deterministic:

```text
patina_pandemonium:generated/<source namespace>/<source path>/<waxed_?><stage>/<form>
```

A hard generation ceiling prevents accidental unbounded registry growth.

## Asset generation

`AssetResolver` reads the first model referenced by a source blockstate and recursively collects concrete texture variables. `GeneratedPackWriter` preserves the source model for full blocks and emits standard template models for derived forms.

Exposed/weathered/oxidized textures are produced by deterministic per-pixel palette transforms. Alpha and source detail are preserved; waxed and unwaxed blocks of the same stage share a texture. If a custom model loader hides its texture, `textureOverrides` supplies an explicit texture ID. When no image can be opened, the generated model safely keeps the source texture instead of relying on a runtime block tint.

## Signs

Signs require more than a normal JSON block model. The implementation creates real standing/wall sign blocks, injects them into `BlockEntityType.SIGN`, registers dynamic `WoodType` materials on the client, emits a 64x32 algorithmically tiled/tinted entity texture, and uses a simple cuboid inventory model. Sign text remains handled by the vanilla sign block entity and editor.

## Generated packs

Client assets and server data are written to separate always-active ZIP packs because modern Minecraft gives resource packs and data packs independent major/minor format versions. The same writer backs the three datagen providers, so `runData` and runtime generation cannot silently diverge.

## Deliberate limitations

- Doors and trapdoors are excluded.
- Generated variants do not clone source block entities or special gameplay behavior.
- The scanner cannot see registrations that occur after this mod's own lowest-priority registry listener.
- Complex multipart/custom-loader models may need `textureOverrides`.
- Nonstandard existing-form names may need `existingFormOverrides`.
