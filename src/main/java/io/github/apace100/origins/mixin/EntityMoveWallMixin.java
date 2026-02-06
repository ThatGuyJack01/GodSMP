package io.github.apace100.origins.mixin;

import io.github.apace100.origins.content.pylon.PylonWallPhysics;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public class EntityMoveWallMixin {
    @ModifyVariable(
            method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private Vec3d godsmp$adjustMovementForPylonCollisions(Vec3d movement) {
        Vec3d v = PylonWallPhysics.clipMovementIfLeavingHull((Entity)(Object)this, movement);
/**        if(movement.equals(v))
            if ((Object)this instanceof PlayerEntity player)
                player.sendMessage(Text.of("Returning: " + v.toString())); **/
        return v;
    }
}
