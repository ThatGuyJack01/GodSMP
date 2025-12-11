package io.github.apace100.origins.content.pylon;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface OwnablePylon {
    @Nullable UUID getOwner();
    void setOwner(@Nullable UUID owner);
}
