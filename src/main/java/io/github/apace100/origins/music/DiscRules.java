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
        private final Target target;
        private final List<StatusEffectInstance> effects;

        private DiscRule(double range, Target target, List<StatusEffectInstance> effects) {
            this.range = range;
            this.target = target;
            this.effects = effects;
        }

        public double range() {
            return range;
        }

        public Target target() {
            return target;
        }

        public List<StatusEffectInstance> effects() {
            return effects;
        }
    }

    public enum Target {
        SELF,
        OTHERS,
        ALL
    }

    private static final Map<Identifier, DiscRule> RULES = new HashMap<>();

    private DiscRules() { }

    public static void bootstrap() {
        RULES.clear();
        register(new Identifier("minecraft", "music_disc.11"), 24.0D, Target.OTHERS,
                new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, true, true));

        register(new Identifier("minecraft", "music_disc.13"), 24.0D, Target.OTHERS,
                new StatusEffectInstance(StatusEffects.WEAKNESS, 20, 0, false, true, true));

        register(new Identifier("minecraft", "music_disc.5"), 24.0D, Target.OTHERS,
                new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 20, 0, false, true, true));

        register(new Identifier("minecraft", "music_disc.blocks"), 48.0D, Target.ALL,
                new StatusEffectInstance(StatusEffects.GLOWING, 20, 0, false, true, true));

        register(new Identifier("minecraft", "music_disc.cat"), 64.0D, Target.SELF,
                new StatusEffectInstance(StatusEffects.SPEED, 20, 0, false, false, true));

        register(new Identifier("minecraft", "music_disc.chirp"), 64.0D, Target.SELF,
                new StatusEffectInstance(StatusEffects.JUMP_BOOST, 20, 1, false, false, true));

        register(new Identifier("minecraft", "music_disc.far"), 64.0D, Target.SELF,
                new StatusEffectInstance(StatusEffects.HERO_OF_THE_VILLAGE, 20, 0, false, false, true));

        register(new Identifier("minecraft", "music_disc.mall"), 64.0D, Target.SELF,
                new StatusEffectInstance(StatusEffects.WATER_BREATHING, 20, 0, false, false, true));

        register(new Identifier("minecraft", "music_disc.mellohi"), 64.0D, Target.SELF,
                new StatusEffectInstance(StatusEffects.NIGHT_VISION, 220, 0, false, false, true));

        register(new Identifier("minecraft", "music_disc.stal"), 64.0D, Target.SELF,
                new StatusEffectInstance(StatusEffects.INVISIBILITY, 20, 0, false, false, true));

        register(new Identifier("minecraft", "music_disc.strad"), 64.0D, Target.SELF,
                new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 20, 0, false, false, true));

        register(new Identifier("minecraft", "music_disc.ward"), 64.0D, Target.SELF,
                new StatusEffectInstance(StatusEffects.BAD_OMEN, 20, 0, false, false, true));

        register(new Identifier("minecraft", "music_disc.wait"), 64.0D, Target.SELF,
                new StatusEffectInstance(StatusEffects.HASTE, 20, 0, false, false, true));

        register(new Identifier("minecraft", "music_disc.otherside"), 64.0D, Target.SELF,
                new StatusEffectInstance(StatusEffects.RESISTANCE, 20, 0, false, false, true));

        register(new Identifier("minecraft", "music_disc.pigstep"), 64.0D, Target.SELF,
                new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20, 0, false, false, true));

        register(new Identifier("minecraft", "music_disc.relic"), 64.0D, Target.SELF,
                new StatusEffectInstance(StatusEffects.SLOW_FALLING, 20, 0, false, false, true));


    }

    public static void register(Identifier soundId, double range, Target target, StatusEffectInstance... effects) {
        if(soundId == null || range <= 0.0D || target == null || effects.length == 0) {
            return;
        }
        RULES.put(soundId, new DiscRule(range, target, List.of(effects)));
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
