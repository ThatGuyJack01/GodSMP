package io.github.apace100.origins.power;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

public class MusicAuraPower extends Power {
    public static final Identifier ID = new Identifier("origins", "music_aura");
    public MusicAuraPower(PowerType<?> type, LivingEntity entity) {
        super(type, entity);
    }

    public static PowerFactory<MusicAuraPower> createFactory() {
        return new PowerFactory<MusicAuraPower>(
                ID,
                new SerializableData(),
                data -> MusicAuraPower::new
        ).allowCondition();
    }
}
