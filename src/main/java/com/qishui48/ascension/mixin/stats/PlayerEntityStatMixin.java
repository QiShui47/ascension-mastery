package com.qishui48.ascension.mixin.stats;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.util.DistanceUtils;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityStatMixin {
    // 美食家统计 //
    @Inject(method = "eatFood", at = @At("HEAD"))
    private void onEatFood(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (!world.isClient && (Object)this instanceof ServerPlayerEntity serverPlayer) {

            String foodId = Registries.ITEM.getId(stack.getItem()).toString();

            IEntityDataSaver dataSaver = (IEntityDataSaver) serverPlayer;
            NbtCompound nbt = dataSaver.getPersistentData();

            NbtList eatenList;
            if (nbt.contains("eaten_foods", NbtElement.LIST_TYPE)) {
                eatenList = nbt.getList("eaten_foods", NbtElement.STRING_TYPE);
            } else {
                eatenList = new NbtList();
            }

            // 查重
            for (NbtElement element : eatenList) {
                if (element.asString().equals(foodId)) return;
            }

            // === 新的美食！===
            eatenList.add(NbtString.of(foodId));
            nbt.put("eaten_foods", eatenList);

            // 奖励 2 点
            int points = 2;
            int currentPoints = nbt.getInt("skill_points");
            nbt.putInt("skill_points", currentPoints + points);
            PacketUtils.syncSkillData(serverPlayer);

            Text msg = Text.translatable("notification.ascension.header.food").formatted(Formatting.AQUA)
                    .append(" ")
                    .append(Text.translatable("notification.ascension.verb.taste").formatted(Formatting.WHITE))
                    .append(" ")
                    .append(Text.translatable(stack.getItem().getTranslationKey()).formatted(Formatting.GOLD))
                    .append(" ")
                    .append(Text.translatable("notification.ascension.suffix.points", points).formatted(Formatting.BOLD, Formatting.GREEN));

            PacketUtils.sendNotification(serverPlayer, msg);

            serverPlayer.playSound(SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.5f, 1.5f);
        }
    }

    // 熔浆游泳统计 //
    // 原 PlayerEntityMixin.onTickFireLogic 的一部分
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickLavaSwimStat(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            if (player.isInLava()) {
                // 使用 DistanceUtils 计算瞬时速度
                int distCm = DistanceUtils.getSpeedCm(player);

                // 简单的阈值：如果动了 (大于1cm)
                if (distCm > 0) {
                    serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.SWIM_IN_LAVA), distCm);
                }
            }
        }
    }

    // 上升高度统计 //
    @Unique private double lastYForAscend = -9999; // 初始值设为特殊值

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickAscension(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            // 初始化
            if (lastYForAscend == -9999) {
                lastYForAscend = player.getY();
            }

            double dy = player.getY() - lastYForAscend;

            // 只统计上升 (dy > 0)
            // 过滤掉瞬间传送 (例如 dy > 10 通常是传送)
            if (dy > 0 && dy < 10) {
                // 增加统计 (单位 cm)
                serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.ASCEND_HEIGHT), (int)(dy * 100));
            }

            // 更新高度
            lastYForAscend = player.getY();
        }
    }



    // 移动距离统计 //
    // 环境探索统计 (Travel Stats)
    @Unique private double statLastX, statLastY, statLastZ;
    @Unique private boolean statInit = false;
    // 专门为基岩统计维护的坐标 (因为基岩只看水平距离，且逻辑独立)
    @Unique private double bedrockLastX, bedrockLastZ;
    @Unique private boolean bedrockInit = false;
    // 饥饿爆发统计专用变量
    @Unique private double hbLastX, hbLastY, hbLastZ;
    @Unique private boolean hbInit = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickTravelStat(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        // 频率控制：每 40 tick (2秒) 统计一次，对统计数据来说足够了，极大节省性能
        if (player.getWorld().isClient || player.age % 40 != 0 || !(player instanceof ServerPlayerEntity serverPlayer)) return;

        // 1. 通用 3D 移动统计 (维度、水下)
        if (!statInit) {
            statLastX = player.getX(); statLastY = player.getY(); statLastZ = player.getZ();
            statInit = true;
        }

        if (DistanceUtils.hasMoved(player.getX(), player.getY(), player.getZ(), statLastX, statLastY, statLastZ)) {
            // [重构] 获取 3D 距离
            int distCm = DistanceUtils.getDistanceCm(player.getX(), player.getY(), player.getZ(), statLastX, statLastY, statLastZ);

            Identifier dimId = player.getWorld().getRegistryKey().getValue();
            if (dimId.equals(new Identifier("minecraft:overworld"))) {
                serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.TRAVEL_OVERWORLD), distCm);
            } else if (dimId.equals(new Identifier("minecraft:the_nether"))) {
                serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.TRAVEL_NETHER), distCm);
            } else if (dimId.equals(new Identifier("minecraft:the_end"))) {
                serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.TRAVEL_END), distCm);
            }

            // 水下移动
            if (player.isSubmergedIn(FluidTags.WATER)) {
                serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.MOVE_UNDERWATER), distCm);
            }
        }
        // 更新通用坐标
        statLastX = player.getX(); statLastY = player.getY(); statLastZ = player.getZ();


        // --- 2. 基岩行走统计 (Bedrock Walk) ---
        if (!bedrockInit) {
            bedrockLastX = player.getX(); bedrockLastZ = player.getZ();
            bedrockInit = true;
        }

        // [重构] 基岩行走只看水平移动
        // 使用工具类计算水平距离 (传入 y=0 即可忽略高度，或者使用专门的 horizontal 方法)
        int hDistCm = DistanceUtils.getHorizontalDistanceCm(player.getX(), player.getZ(), bedrockLastX, bedrockLastZ);

        // 只有当玩家在地面上，且脚下是基岩时才统计
        if (hDistCm > 0 && player.isOnGround()) {
            net.minecraft.util.math.BlockPos groundPos = player.getBlockPos().down();
            if (player.getWorld().getBlockState(groundPos).isOf(net.minecraft.block.Blocks.BEDROCK)) {
                serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.WALK_ON_BEDROCK), hDistCm);
            }
        }
        // 更新基岩坐标
        bedrockLastX = player.getX(); bedrockLastZ = player.getZ();

        // 饥饿爆发：发现机制 (Hunger Burst Discovery)
        // 逻辑：饱食度为0 + 移动
        if (player.getHungerManager().getFoodLevel() == 0) {
            // 只有当技能还没解锁时，才浪费性能去计算距离
            if (PacketUtils.getSkillLevel(serverPlayer, "hunger_burst") == 0) {

                if (!hbInit) {
                    hbLastX = player.getX(); hbLastY = player.getY(); hbLastZ = player.getZ();
                    hbInit = true;
                }

                if (DistanceUtils.hasMoved(player.getX(), player.getY(), player.getZ(), hbLastX, hbLastY, hbLastZ)) {
                    int distCm = DistanceUtils.getDistanceCm(player.getX(), player.getY(), player.getZ(), hbLastX, hbLastY, hbLastZ);

                    IEntityDataSaver data = (IEntityDataSaver) player;
                    NbtCompound nbt = data.getPersistentData();
                    float currentDist = nbt.getFloat("hunger_walk_dist");
                    currentDist += distCm;
                    nbt.putFloat("hunger_walk_dist", currentDist);

                    // 阈值: 50米 (5000cm)
                    if (currentDist >= 5000) {
                        PacketUtils.revealHiddenSkill(serverPlayer, "hunger_burst");
                    }
                }
                // 更新坐标
                hbLastX = player.getX(); hbLastY = player.getY(); hbLastZ = player.getZ();
            }
        } else {
            // 吃饱了就重置，防止下次饿的时候产生瞬移距离
            hbInit = false;
        }
    }

    // 生物群系统计 & 结构统计 //
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickBiomeStructureCheck(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        // 1. 服务端检查，且每 40 tick (2秒) 检查一次，节省性能
        if (!player.getWorld().isClient && player.age % 40 == 0 && player instanceof ServerPlayerEntity serverPlayer) {
            // 2. 获取当前群系 ID
            // getBiome 返回的是 RegistryEntry，需要取 Key
            var biomeEntry = player.getWorld().getBiome(player.getBlockPos());
            // 如果 biome 没有注册 Key (极少见)，跳过
            if (biomeEntry.getKey().isEmpty()) return;

            String biomeId = biomeEntry.getKey().get().getValue().toString(); // 例如 "minecraft:plains"

            // 3. 读取 NBT
            IEntityDataSaver dataSaver = (IEntityDataSaver) player;
            NbtCompound nbt = dataSaver.getPersistentData();

            NbtList exploredBiomes;
            if (nbt.contains("explored_biomes", NbtElement.LIST_TYPE)) {
                exploredBiomes = nbt.getList("explored_biomes", NbtElement.STRING_TYPE);
            } else {
                exploredBiomes = new NbtList();
            }

            // 4. 查重
            for (NbtElement element : exploredBiomes) {
                if (element.asString().equals(biomeId)) return;
            }

            // 5. === 新群系！===
            exploredBiomes.add(NbtString.of(biomeId));
            nbt.put("explored_biomes", exploredBiomes);

            // 奖励 10 点
            int points = 10;
            int currentPoints = nbt.getInt("skill_points");
            nbt.putInt("skill_points", currentPoints + points);

            PacketUtils.syncSkillData(serverPlayer);

            // === 群系名称翻译 ===
            // 原版群系翻译键格式：biome.namespace.path
            // 例如 minecraft:plains -> biome.minecraft.plains
            String biomeTranslationKey = Util.createTranslationKey("biome", new Identifier(biomeId));

            // === 构建完全本地化的消息 ===
            Text msg = Text.translatable("notification.ascension.header.biome").formatted(Formatting.GREEN) // [新视界]
                    .append(" ")
                    .append(Text.translatable("notification.ascension.verb.find").formatted(Formatting.WHITE)) // 发现
                    .append(" ")
                    .append(Text.translatable(biomeTranslationKey).formatted(Formatting.GREEN)) // 群系名
                    .append(" ")
                    .append(Text.translatable("notification.ascension.suffix.points", points).formatted(Formatting.BOLD, Formatting.GREEN));

            PacketUtils.sendNotification(serverPlayer, msg);

            player.playSound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.PLAYERS, 0.5f, 1.2f);

            // 手动遍历检测矿井 (最稳健方案)
            if (player.getWorld() instanceof ServerWorld serverWorld) {
                var structureAccessor = serverWorld.getStructureAccessor();
                // 1. 获取结构注册表
                var structureRegistry = serverWorld.getRegistryManager().get(RegistryKeys.STRUCTURE);

                // 2. 获取所有属于 "废弃矿井" 标签的结构类型列表
                // (这会包含 minecraft:mineshaft 和 minecraft:mineshaft_mesa)
                var mineshaftList = structureRegistry.getEntryList(StructureTags.MINESHAFT);

                if (mineshaftList.isPresent()) {
                    // 3. 遍历列表，逐个检查
                    for (RegistryEntry<Structure> entry : mineshaftList.get()) {
                        // 使用 getStructureAt 检查具体的结构类型 (这是最底层的 API，绝不会出错)
                        if (structureAccessor.getStructureAt(player.getBlockPos(), entry.value()).hasChildren()) {

                            // 4. 只要命中其中任何一个，就视为成功
                            serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.EXPLORE_MINESHAFT), 1);

                            // 既然已经找到了，就没必要继续检查其他类型了，节省性能
                            break;
                        }
                    }
                }
            }
        }
    }
}
