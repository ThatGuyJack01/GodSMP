package io.github.apace100.origins.registry;

import io.github.apace100.origins.Origins;
import io.github.apace100.origins.content.PylonBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {
    public static final BlockEntityType<PylonBlockEntity> PYLON = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(Origins.MODID, "pylon"),
            FabricBlockEntityTypeBuilder.create(PylonBlockEntity::new, ModBlocks.PYLON).build()
    );

    public static void register() { }
}
