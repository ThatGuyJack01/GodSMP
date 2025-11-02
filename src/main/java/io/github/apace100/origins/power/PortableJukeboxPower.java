package io.github.apace100.origins.power;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataType;
import io.github.apace100.calio.data.SerializableDataTypes;
import io.github.apace100.origins.Origins;
import net.minecraft.entity.LivingEntity;

public class PortableJukeboxPower extends Power {
    private final int exhaustionInterval;
    private final float exhaustionAmount;

    public PortableJukeboxPower(PowerType<?> type, LivingEntity entity, int exhaustionInterval, float exhaustionAmount) {
        super(type, entity);
        this.exhaustionInterval = exhaustionInterval;
        this.exhaustionAmount = exhaustionAmount;
    }

    public int getExhaustionInterval() {
        return exhaustionInterval;
    }

    public float getExhaustionAmount() {
        return exhaustionAmount;
    }

    public static PowerFactory<PortableJukeboxPower> createFactory() {
        return new PowerFactory<PortableJukeboxPower>(
            Origins.identifier("portable_jukebox"),
            new SerializableData()
                .add("exhaustion_interval", SerializableDataTypes.INT, 20)
                .add("exhaustion_amount", SerializableDataTypes.FLOAT, 0.05F),
    data -> (type, entity) -> new PortableJukeboxPower(
                type,
                entity,
                data.getInt("exhaustion_interval"),
                data.getFloat("exhaustion_amount")
            )
        ).allowCondition();
    }
}
