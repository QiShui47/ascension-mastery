package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.util.IEntityDataSaver;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class MobEntityDeathMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity victim = (LivingEntity) (Object) this;

        if (!victim.getWorld().isClient) {
            Entity attacker = source.getAttacker();

            // 如果攻击者是狼
            if (attacker instanceof WolfEntity wolf && wolf.isTamed()) {
                LivingEntity owner = wolf.getOwner();

                // 且主人是玩家
                if (owner instanceof ServerPlayerEntity serverPlayer) {
                    // 增加统计
                    serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.DOG_KILL_MOB_COUNT), 1);
                }
            }
        }
    }
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onUndeadDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;

        // 必须是玩家击杀 (或者玩家附近死亡?)
        // 题目要求：解锁条件是"击杀"，重置条件是"被烧死"

        // 获取击杀者 (如果有)
        Entity attacker = source.getAttacker();

        // === 1. 解锁条件统计 (击杀8种亡灵) ===
        if (entity.getGroup() == net.minecraft.entity.EntityGroup.UNDEAD && attacker instanceof ServerPlayerEntity player) {
            IEntityDataSaver data = (IEntityDataSaver) player;
            NbtCompound nbt = data.getPersistentData();

            // 读取已杀列表
            NbtCompound killedTypes;
            if (nbt.contains("killed_undead_types_set")) {
                killedTypes = nbt.getCompound("killed_undead_types_set");
            } else {
                killedTypes = new NbtCompound();
            }

            // 获取生物 ID (例如 "minecraft:zombie")
            String typeId = net.minecraft.entity.EntityType.getId(entity.getType()).toString();

            if (!killedTypes.contains(typeId)) {
                killedTypes.putBoolean(typeId, true);
                nbt.put("killed_undead_types_set", killedTypes);

                // 更新计数器 (UnlockCriterion 直接读这个 int)
                int count = killedTypes.getSize();
                nbt.putInt("undead_type_count", count);

                // 检查是否达成 (可选，PacketUtils 会自动同步，但这里可以立即发个通知)
                if (count == 8) {
                    // ... 可以在这里播放个音效提示条件达成 ...
                }
                com.qishui48.ascension.util.PacketUtils.syncSkillData(player);
            }
        }

        // === 2. 持续时间重置逻辑 ===
        // 条件：亡灵生物 + 附近15米 + 玩家技能激活 + "被烧死" (source.isOnFire 或 fire tick > 0?)
        // 题目说"被烧死时"，通常指 damageSource.isInFire() 或 isOnFire()
        // 或者只要是玩家光环造成的伤害致死
        if (entity.getGroup() == net.minecraft.entity.EntityGroup.UNDEAD) {
            entity.getWorld().getEntitiesByClass(ServerPlayerEntity.class,
                            entity.getBoundingBox().expand(15.0),
                            p -> true)
                    .forEach(p -> {
                        IEntityDataSaver pData = (IEntityDataSaver) p;
                        NbtCompound pNbt = pData.getPersistentData();

                        if (pNbt.contains("radiant_damage_end")) {
                            long currentEnd = pNbt.getLong("radiant_damage_end");
                            long now = p.getWorld().getTime();

                            if (now < currentEnd) {
                                // 使用 Tag 判断火焰伤害
                                // 检查死因：是否是被火烧死 (在火里，或者受到火焰类伤害)
                                if (source.isIn(DamageTypeTags.IS_FIRE) || entity.isOnFire()) {

                                    long newEnd = now + 240; // 重置为 12秒
                                    pNbt.putLong("radiant_damage_end", newEnd);

                                    // [修复] 现在 updateSkillSlotBus 是 public 的了，可以调用
                                    com.qishui48.ascension.skill.SkillActionHandler.updateSkillSlotBus(p, "radiant_avatar", 240, newEnd);

                                    p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                                            SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.PLAYERS, 1.0f, 1.5f);
                                    com.qishui48.ascension.util.PacketUtils.syncSkillData(p);
                                }
                            }
                        }
                    });
        }
    }
}