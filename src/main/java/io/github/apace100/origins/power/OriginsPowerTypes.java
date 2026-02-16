package io.github.apace100.origins.power;

import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeReference;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.apoli.registry.ApoliRegistries;
import io.github.apace100.origins.Origins;
import net.minecraft.registry.Registry;

public class OriginsPowerTypes {

    public static final PowerType<?> LIKE_WATER = new PowerTypeReference<>(Origins.identifier("like_water"));
    public static final PowerType<?> WATER_BREATHING = new PowerTypeReference<>(Origins.identifier("water_breathing"));
    public static final PowerType<?> SCARE_CREEPERS = new PowerTypeReference<>(Origins.identifier("scare_creepers"));
    public static final PowerType<?> WATER_VISION = new PowerTypeReference<>(Origins.identifier("water_vision"));
    public static final PowerType<?> NO_COBWEB_SLOWDOWN = new PowerTypeReference<>(Origins.identifier("no_cobweb_slowdown"));
    public static final PowerType<?> MASTER_OF_WEBS_NO_SLOWDOWN = new PowerTypeReference<>(Origins.identifier("master_of_webs_no_slowdown"));
    public static final PowerType<?> CONDUIT_POWER_ON_LAND = new PowerTypeReference<>(Origins.identifier("conduit_power_on_land"));
    public static final PowerType<PortableJukeboxPower> PORTABLE_JUKEBOX = new PowerTypeReference<>(Origins.identifier("portable_jukebox"));
    public static final PowerType<PortableJukeboxPower> MUSIC_AURA = new PowerTypeReference<>(MusicAuraPower.ID);
    public static final PowerType<?> CAN_OWN_PYLONS = new PowerTypeReference<>(Origins.identifier("can_own_pylons"));

    public static void register() {
        register(OriginsCallbackPower.createFactory());
        register(PortableJukeboxPower.createFactory());
        register(MusicAuraPower.createFactory());
    }

    private static void register(PowerFactory<?> serializer) {
        Registry.register(ApoliRegistries.POWER_FACTORY, serializer.getSerializerId(), serializer);
    }

}
