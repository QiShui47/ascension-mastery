package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PiglinBruteEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(EntityType<?> type, net.minecraft.world.World world) {
        super(type, world);
    }

    // 监听生物死亡事件
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeathCheck(DamageSource source, CallbackInfo ci) {
        // 1. 检查世界状态
        if (this.getWorld().isClient) return;

        // 2. 检查击杀者是否是玩家
        if (source.getAttacker() instanceof ServerPlayerEntity player) {

            // 3. 获取被杀生物的 ID (例如 "minecraft:zombie")
            String entityId = EntityType.getId(this.getType()).toString();

            // 忽略玩家杀玩家 (或者你可以保留，看需求)
            if (this.getType() == EntityType.PLAYER) return;

            // 4. 读取玩家数据
            IEntityDataSaver dataSaver = (IEntityDataSaver) player;
            NbtCompound nbt = dataSaver.getPersistentData();

            NbtList killedList;
            if (nbt.contains("killed_mobs", NbtElement.LIST_TYPE)) {
                killedList = nbt.getList("killed_mobs", NbtElement.STRING_TYPE);
            } else {
                killedList = new NbtList();
            }

            // 5. 查重
            for (NbtElement element : killedList) {
                if (element.asString().equals(entityId)) {
                    return; // 已经杀过这种生物了
                }
            }

            // === 是首杀！计算点数 ===
            int points = 1; // 基础分 (比如杀牛杀羊)
            String rankName = ""; // 评级名称
            Formatting color = Formatting.WHITE;

            LivingEntity victim = (LivingEntity)(Object)this;

            // 1. 敌对生物检测 (Monster 包含僵尸、骷髅、蜘蛛、苦力怕等)
            boolean isHostile = victim instanceof Monster;

            // 2. 困难生物检测 (手动列表)
            boolean isHard = victim instanceof IronGolemEntity
                    || victim instanceof PiglinBruteEntity
                    || victim instanceof RaiderEntity; // 劫掠者/唤魔者等

            // 3. BOSS 检测
            boolean isBoss = victim instanceof WitherEntity
                    || victim instanceof EnderDragonEntity
                    || victim instanceof WardenEntity; // 监守者不算Boss类但算Boss级

            // 计算逻辑
            if (isBoss) {
                points = 10;
                rankName = " [BOSS击杀]";
                color = Formatting.LIGHT_PURPLE; // 史诗紫
            } else {
                if (isHostile) {
                    points += 1; // 敌对 +1
                    rankName = " [狩猎]";
                    color = Formatting.YELLOW;
                }
                if (isHard) {
                    points += 1; // 困难 +1 (如果是敌对且困难，就是 1+1+1=3)
                    rankName = " [强敌击破]";
                    color = Formatting.RED; // 红色
                }
                // 普通被动生物 points = 1, rankName = ""
            }

            // 存数据
            killedList.add(NbtString.of(entityId));
            nbt.put("killed_mobs", killedList);
            int currentPoints = nbt.getInt("my_global_skills");
            nbt.putInt("my_global_skills", currentPoints + points);

            // 同步
            PacketUtils.syncSkillData(player);

            // 使用新的通知系统
            Text msg = Text.literal("§6" + rankName + " §f击败 ")
                    .append(this.getType().getName().copy().formatted(color))
                    .append(Text.literal(" §a+" + points + " 技能点").formatted(Formatting.BOLD));

            PacketUtils.sendNotification(player, msg);

            player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.4f, 1.0f + (points * 0.1f));
        }
    }
}