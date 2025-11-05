package io.github.apace100.origins.music;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MusicDiscItem;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DiscRules {
    public static final class DiscRule {
        private final double range;
        private final List<StatusEffectInstance> effects;

        private DiscRule(double range, List<StatusEffectInstance> effects) {
            this.range = range;
            this.effects = effects;
        }

        public double range() {
            return range;
        }

        public List<StatusEffectInstance> effects() {
            return effects;
        }
    }

    private static final Map<Identifier, DiscRule> RULES = new HashMap<>();

    private DiscRules() { }

    public static void bootstrap() {
        RULES.clear();
        register(new Identifier("minecraft", "music_disc.cat"), 64.0D,
                new StatusEffectInstance(StatusEffects.SPEED, 60, 0, false, true, true));
        register(new Identifier("minecraft", "music_disc.ward"), 48.0D,
                new StatusEffectInstance(StatusEffects.STRENGTH, 60, 0, false, true, true));
    }

    public static void register(Identifier soundId, double range, StatusEffectInstance... effects) {
        if(soundId == null || range <= 0.0D || effects.length == 0) {
            return;
        }
        RULES.put(soundId, new DiscRule(range, List.of(effects)));
    }

    public static Optional<DiscRule> get(Identifier soundId) {
        return Optional.ofNullable(RULES.get(soundId));
    }

    public static boolean has(Identifier soundId) {
        return RULES.containsKey(soundId);
    }

    public static Identifier resolve(ItemStack stack) {
        if(stack == null || stack.isEmpty() || !(stack.getItem() instanceof MusicDiscItem disc))
            return null;
        if(disc.getSound() == null)
            return null;
        return disc.getSound().getId();
    }
}
