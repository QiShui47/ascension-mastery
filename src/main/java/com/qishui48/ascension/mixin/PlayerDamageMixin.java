package com.qishui48.ascension.mixin;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.skill.SkillEffectHandler;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            // 技能：火焰免疫 (Fire Res)
            if (PacketUtils.isSkillActive(serverPlayer, "fire_res")) {
                if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FIRE)) {
                    player.extinguish();
                    cir.setReturnValue(false);
                    return;
                }
            }

            // 技能：肾上腺素爆发 (Adrenaline Rush)
            // 判定条件：满血 且 受到伤害 >= 8 (4颗心)
            if (player.getHealth() >= player.getMaxHealth() && amount >= 8.0f) {
                int level = PacketUtils.getSkillLevel(serverPlayer, "adrenaline_burst");
                if (level > 0) {
                    // === 关键：推迟到下一 Tick 执行 ===
                    // 这样就能保证 "先扣血结算，然后再加盾"
                    // 否则如果在 HEAD 加盾，盾会被本次伤害直接打掉
                    serverPlayer.getServer().execute(() -> {
                        if (serverPlayer.isAlive()) {
                            int absorptionAmplifier = level - 1; // 0级=I, 1级=II...
                            int speedAmplifier = (level >= 3) ? 1 : 0; // 3级才有速度II
                            // 给予 Buff
                            serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, absorptionAmplifier));
                            serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, speedAmplifier));
                            // 反馈音效
                            serverPlayer.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0f, 1.5f);
                        }
                    });
                }
            }

            // 技能：亢奋 (Excitement)
            // 逻辑：伤害 >= 6.0 (3颗心) 且 技能激活
            if (amount >= 6.0f && PacketUtils.isSkillActive(serverPlayer, "excitement")) {
                int level = PacketUtils.getSkillLevel(serverPlayer, "excitement");
                int foodCost = 4; // 消耗4点饱食度 (2个鸡腿)

                // 检查饱食度是否足够
                if (player.getHungerManager().getFoodLevel() >= foodCost) {

                    // 扣除饱食度 (立即执行)
                    player.getHungerManager().setFoodLevel(player.getHungerManager().getFoodLevel() - foodCost);
                    // 增加一点饱和度消耗，防止玩家利用高饱和度无限回血，模拟"消化"
                    player.getHungerManager().addExhaustion(2.0f);

                    // 播放音效 (类似剧烈喘息或快速进食)
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 1.0f, 1.5f);

                    // 推迟治疗逻辑，确保在伤害扣除后执行
                    serverPlayer.getServer().execute(() -> {
                        if (serverPlayer.isAlive()) {
                            float healAmount = (level >= 2) ? 4.0f : 3.0f; // Lv1=3, Lv2=4
                            serverPlayer.heal(healAmount);
                        }
                    });
                }
            }

            // === 技能：火焰感染 (Fire Infection) ===
            // 逻辑：玩家着火 + 受到攻击 -> 攻击者着火
            if (PacketUtils.isSkillActive(serverPlayer, "fire_infection")) {
                if (player.isOnFire() && source.getAttacker() != null) {
                    Entity attacker = source.getAttacker();
                    // 只有攻击者没着火时才点燃，防止无限刷新
                    if (!attacker.isOnFire()) {
                        attacker.setOnFireFor(5); // 点燃 5 秒
                        // 播放点燃音效
                        player.getWorld().playSound(null, attacker.getBlockPos(), SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    }
                }
            }

            // === 缸中之脑：玻璃破碎 ===
            // 只要受到伤害（amount > 0），就有概率碎
            if (amount > 0 && PacketUtils.isSkillActive(serverPlayer, "brain_in_a_jar")) {
                ItemStack headStack = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
                if (!headStack.isEmpty() && headStack.getItem() instanceof net.minecraft.item.BlockItem bi &&
                        bi.getBlock() instanceof net.minecraft.block.AbstractGlassBlock) {

                    int level = PacketUtils.getSkillLevel(serverPlayer, "brain_in_a_jar");
                    float breakChance = (level >= 5) ? 0.05f : 0.5f; // Lv5: 5%, 其他: 50%

                    if (serverPlayer.getRandom().nextFloat() < breakChance) {
                        // 碎了！
                        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        player.getWorld().syncWorldEvent(2001, player.getBlockPos().up(), net.minecraft.block.Block.getRawIdFromState(bi.getBlock().getDefaultState()));

                        headStack.decrement(1); // 扣除物品

                        // 刷新属性（移除护甲加成）
                        SkillEffectHandler.refreshAttributes(serverPlayer);
                    }
                }
            }
        }
    }

    // === 优雅的数值修改：火焰伤害减半 ===
    // at = "HEAD" 表示在方法开始时就修改
    // argsOnly = true 表示我们只关心方法参数
    // ordinal = 0 表示修改第一个 float 类型的参数（即 amount）
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float modifyDamageAmount(float amount, DamageSource source) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            // 检查是否是火焰伤害
            if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FIRE)) {

                // 检查 Lv2 技能：火焰抵抗 (Fire Resistance)
                // 注意：如果玩家以后解锁了"火焰免疫"，那个在下面的 Inject 里会直接 return false，这里减半也没关系
                if (PacketUtils.isSkillActive(serverPlayer, "fire_resistance")) {
                    return amount * 0.5f; // 返回一半的伤害
                }
            }
        }
        return amount; // 其他情况保持原样
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void onDamageReturn(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            // 检查伤害来源是否是“飞入墙壁” (Elytra撞击)
            if (source.isOf(DamageTypes.FLY_INTO_WALL)) {
                // 检查剩余血量：0.5 颗心 = 1.0f
                // 必须活着
                if (player.getHealth() <= 1.0f && player.getHealth() > 0) {
                    // 满足“御剑飞行”解锁条件：生死时速
                    // 触发一个自定义统计数据，用于 UnlockCriterion
                    serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.SURVIVE_ELYTRA_CRASH), 1);
                }
            }
        }
    }
}