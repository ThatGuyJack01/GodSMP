package io.github.apace100.origins.mixin;

import io.github.apace100.origins.content.pylon.PylonWallPhysics;
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
    private Vec3d origins$clipMovementAgainstPylonWall(Vec3d movement) {
        return PylonWallPhysics.clipMovementIfLeavingHull((Entity)(Object)this, movement);
    }
}
