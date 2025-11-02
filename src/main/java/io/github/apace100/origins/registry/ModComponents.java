package io.github.apace100.origins.registry;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.origins.Origins;
import io.github.apace100.origins.component.OriginComponent;
import io.github.apace100.origins.component.PlayerOriginComponent;
import io.github.apace100.origins.component.PlayerPortableJukeboxComponent;
import io.github.apace100.origins.component.PortableJukeboxComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

public class ModComponents implements EntityComponentInitializer {

    public static final ComponentKey<OriginComponent> ORIGIN;
    public static final ComponentKey<PortableJukeboxComponent> PORTABLE_JUKEBOX;

    static {
        ORIGIN = ComponentRegistry.getOrCreate(new Identifier(Origins.MODID, "origin"), OriginComponent.class);
        PORTABLE_JUKEBOX = ComponentRegistry.getOrCreate(new Identifier(Origins.MODID, "portable_jukebox"), PortableJukeboxComponent.class);
    }

    public static void register() {}

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.beginRegistration(PlayerEntity.class, ORIGIN)
            .after(PowerHolderComponent.KEY)
            .respawnStrategy(RespawnCopyStrategy.CHARACTER)
            .end(PlayerOriginComponent::new);

        registry.beginRegistration(PlayerEntity.class, PORTABLE_JUKEBOX)
            .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY)
            .end(PlayerPortableJukeboxComponent::new);
    }

}
