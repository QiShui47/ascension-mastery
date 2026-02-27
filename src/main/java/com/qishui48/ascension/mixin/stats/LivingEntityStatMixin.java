package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
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
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityStatMixin extends Entity {

    public LivingEntityStatMixin(EntityType<?> type, net.minecraft.world.World world) {
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

            LivingEntity victim = (LivingEntity)(Object)this;
            // === 击杀燃烧骷髅判定 ===
            if (victim instanceof net.minecraft.entity.mob.SkeletonEntity && victim.isOnFire()) {
                player.getStatHandler().increaseStat(player, Stats.CUSTOM.getOrCreateStat(Ascension.KILL_BURNING_SKELETON), 1);
            }

            // === 升级条件：击杀高压爬行者 ===
            if (victim instanceof net.minecraft.entity.mob.CreeperEntity creeper) {
                if (creeper.shouldRenderOverlay()) { // shoudRenderOverlay 返回是否是高压状态
                    player.getStatHandler().increaseStat(player, Stats.CUSTOM.getOrCreateStat(Ascension.KILL_CHARGED_CREEPER), 1);
                }
            }

            // === 升级条件：空中击杀僵尸 ===
            if (victim instanceof net.minecraft.entity.mob.ZombieEntity) {
                // 玩家必须在空中 (不能在地面)
                if (!player.isOnGround()) {
                    player.getStatHandler().increaseStat(player, Stats.CUSTOM.getOrCreateStat(Ascension.KILL_ZOMBIE_AIR), 1);
                }
            }

            // 5. 查重
            for (NbtElement element : killedList) {
                if (element.asString().equals(entityId)) {
                    return; // 已经杀过这种生物了
                }
            }

            // === 是首杀！计算点数 ===
            int points = 5; // 基础分 (比如杀牛杀羊)
            String rankKey = "notification.ascension.header.kill.first"; // 默认：首杀
            Formatting color = Formatting.WHITE;

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
                rankKey = "notification.ascension.header.kill.boss"; // BOSS击杀
                color = Formatting.LIGHT_PURPLE;
            } else {
                if (isHostile) {
                    points += 5;
                    rankKey = "notification.ascension.header.kill.hostile"; // 狩猎
                    color = Formatting.YELLOW;
                }
                if (isHard) {
                    points += 5;
                    // 如果既是敌对又是强敌，这里会覆盖上面的 Key，显示“强敌击破”
                    rankKey = "notification.ascension.header.kill.hard";
                    color = Formatting.RED;
                }
            }

            // 存数据
            killedList.add(NbtString.of(entityId));
            nbt.put("killed_mobs", killedList);
            int currentPoints = nbt.getInt("skill_points");
            nbt.putInt("skill_points", currentPoints + points);

            // 同步
            PacketUtils.syncSkillData(player);

            // 使用新的通知系统
            Text msg = Text.translatable(rankKey).formatted(Formatting.GOLD)
                    .append(" ")
                    .append(Text.translatable("notification.ascension.verb.defeat").formatted(Formatting.WHITE))
                    .append(" ")
                    .append(this.getType().getName().copy().formatted(color))
                    .append(" ")
                    .append(Text.translatable("notification.ascension.suffix.points", points).formatted(Formatting.BOLD, Formatting.GREEN));

            PacketUtils.sendNotification(player, msg);

            player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.4f, 1.0f + (points * 0.1f));
        }
    }
    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"))
    private void onAddStatusEffect(net.minecraft.entity.effect.StatusEffectInstance effect, net.minecraft.entity.Entity source, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof net.minecraft.server.network.ServerPlayerEntity player) {
            if (effect.getEffectType() == net.minecraft.entity.effect.StatusEffects.GLOWING) {
                // 判断是否是夜间 (13000 ~ 23000)
                long timeOfDay = player.getWorld().getTimeOfDay() % 24000;
                if (timeOfDay >= 13000 && timeOfDay <= 23000) {
                    player.increaseStat(net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(Ascension.GLOWING_AT_NIGHT), 1);
                }
            }
        }
    }
}