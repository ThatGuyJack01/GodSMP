package io.github.apace100.origins.content.pylon;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.origins.power.OriginsPowerTypes;
import net.minecraft.server.network.ServerPlayerEntity;

public class PylonPermissions {
    private PylonPermissions() { }

    public static boolean canOwnPylons(ServerPlayerEntity player) {
        return PowerHolderComponent.KEY.get(player).hasPower(OriginsPowerTypes.CAN_OWN_PYLONS);
    }
}
