package com.qishui48.ascension.mixin;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.skill.SkillEffectHandler;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import com.qishui48.ascension.util.ISacrificialState;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.Optional;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.qishui48.ascension.skill.SkillEffectHandler.applySugarMasterEffect;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements com.qishui48.ascension.util.ISacrificialState {

    @Shadow public abstract HungerManager getHungerManager();

    @Unique private int lastFoodLevel = 20;
    @Unique private int furnaceTimer = 0;
    @Unique private ItemStack lastSmeltingStack = ItemStack.EMPTY;

    // 隐藏技能相关变量
    @Unique private double hb_lastX, hb_lastY, hb_lastZ;
    @Unique private boolean initializedPos = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            int currentFood = this.getHungerManager().getFoodLevel();

            // 饥饿体质逻辑
            if (currentFood < this.lastFoodLevel) {
                int level = PacketUtils.getSkillLevel(serverPlayer, "hunger_constitution");
                if (level > 0) {
                    int amplifier = level - 1;
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, amplifier));
                }
            }
            this.lastFoodLevel = currentFood;

            // 饥饿爆发 (Hidden Skill) 逻辑与发现机制

            // 初始化位置
            if (!initializedPos) {
                hb_lastX = player.getX();
                hb_lastY = player.getY();
                hb_lastZ = player.getZ();
                initializedPos = true;
            }

            // 计算位移距离
            double distSqr = player.squaredDistanceTo(hb_lastX, hb_lastY, hb_lastZ);

            // 只有在饱食度为 0 且移动时才计算
            if (currentFood == 0 && distSqr > 0) {
                IEntityDataSaver data = (IEntityDataSaver) player;
                NbtCompound nbt = data.getPersistentData();

                // 累计距离 (单位: cm)
                float currentDist = nbt.getFloat("hunger_walk_dist");
                currentDist += (float) Math.sqrt(distSqr) * 100; // block -> cm
                nbt.putFloat("hunger_walk_dist", currentDist);

                // 阈值检查: 50米 = 5000cm
                if (currentDist >= 5000) {
                    // 触发发现隐藏技能
                    PacketUtils.revealHiddenSkill(serverPlayer, "hunger_burst");
                }

                // === 技能实际效果生效 ===
                // 如果已经解锁了该技能 (Level > 0)
                if (PacketUtils.getSkillLevel(serverPlayer, "hunger_burst") > 0) {
                    // 给予 Buff (持续时间短，需要不断补充)
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20, 2, true, false)); // III级 = amplifier 2
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20, 2, true, false));
                }
            }

            // 更新位置
            hb_lastX = player.getX();
            hb_lastY = player.getY();
            hb_lastZ = player.getZ();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickPocketFurnace(CallbackInfo ci) {
        // ... (保持原有的口袋熔炉逻辑不变) ...
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            int level = PacketUtils.getSkillLevel(serverPlayer, "pocket_furnace");
            if (level <= 0) return;
            // === [修复] 使用 isSkillActive 替代 getSkillLevel > 0 ===
            // 这样当玩家中键停用技能时，逻辑会立即停止
            if (!PacketUtils.isSkillActive(serverPlayer, "pocket_furnace")) {
                // 如果当前正在熔炼中（Timer > 0），说明技能刚被关掉，需要重置状态
                if (furnaceTimer > 0) {
                    ItemStack currentStack = player.getInventory().getStack(8);
                    resetFurnace(serverPlayer, currentStack);
                }
                return;
            }

            ItemStack currentStack = player.getInventory().getStack(8);

            if (currentStack.isEmpty() || !ItemStack.canCombine(currentStack, lastSmeltingStack)) {
                resetFurnace(serverPlayer, currentStack);
                lastSmeltingStack = currentStack.isEmpty() ? ItemStack.EMPTY : currentStack.copy();
            }

            if (currentStack.isEmpty()) return;

            World world = player.getWorld();
            SimpleInventory testInv = new SimpleInventory(currentStack);
            Optional<SmeltingRecipe> recipe = world.getRecipeManager()
                    .getFirstMatch(RecipeType.SMELTING, testInv, world);

            if (recipe.isPresent()) {
                int totalTime = (level >= 2) ? 200 : 400;

                if (furnaceTimer == 0 || !player.getItemCooldownManager().isCoolingDown(currentStack.getItem())) {
                    player.getItemCooldownManager().set(currentStack.getItem(), totalTime - furnaceTimer);
                }
                furnaceTimer++;

                if (furnaceTimer >= totalTime) {
                    ItemStack result = recipe.get().getOutput(world.getRegistryManager()).copy();
                    if (player.getInventory().insertStack(result)) {
                        currentStack.decrement(1);
                        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                                net.minecraft.sound.SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE,
                                net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 1.0f);
                        furnaceTimer = 0;
                        player.getItemCooldownManager().set(currentStack.getItem(), 0);
                    } else {
                        furnaceTimer = totalTime;
                    }
                }
            } else {
                resetFurnace(serverPlayer, currentStack);
            }
        }
    }

    @Unique private double lastNetherX, lastNetherY, lastNetherZ;
    @Unique private boolean initializedNetherPos = false;

    // 监听吃东西
    @Inject(method = "eatFood", at = @At("HEAD"))
    private void onEatFood(World world, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (!world.isClient && (Object)this instanceof ServerPlayerEntity serverPlayer) {

            // === 火锅食客 (Hotpot Diner) ===
            if (PacketUtils.isSkillActive(serverPlayer, "hotpot_diner")) {
                // 检查是否是熟肉 (列举常见熟肉)
                Item item = stack.getItem();
                boolean isCookedMeat = (item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP ||
                        item == Items.COOKED_MUTTON || item == Items.COOKED_CHICKEN ||
                        item == Items.COOKED_RABBIT);

                if (isCookedMeat) {
                    int level = PacketUtils.getSkillLevel(serverPlayer, "hotpot_diner");
                    // 概率: Lv1=10%, Lv2=20%, Lv3=30%
                    float chance = level * 0.1f;

                    if (serverPlayer.getRandom().nextFloat() < chance) {
                        // 恢复 3 颗心 (6点)
                        serverPlayer.heal(6.0f);
                        // 播放奖励音效
                        world.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                                SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.5f, 1.0f); // 嘶啦声
                    }
                }
            }

            // === 糖分主理人逻辑（南瓜派部分） ===
            if (PacketUtils.isSkillActive(serverPlayer, "sugar_master")) {
                Item item = stack.getItem();
                if (item == Items.PUMPKIN_PIE) {
                    applySugarMasterEffect(serverPlayer, item.getFoodComponent());
                }
            }

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

    @Unique private double lastBedrockX, lastBedrockZ;
    @Unique private boolean initializedBedrockPos = false;
    @Unique private double lastX, lastY, lastZ;
    // 用于人力发电机的累积计数器
    @Unique private float dynamoDistAccumulator = 0;
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickEnvironmentCheck(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        // 1. 服务端检查，且每 40 tick (2秒) 检查一次，节省性能
        if (!player.getWorld().isClient && player.age % 40 == 0 && player instanceof ServerPlayerEntity serverPlayer) {

            // === 1. Bedrock Walk Detection (Runs Every Tick) ===
            // Initialize previous position if needed
            if (!initializedBedrockPos) {
                lastBedrockX = player.getX();
                lastBedrockZ = player.getZ();
                initializedBedrockPos = true;
            }

            // Calculate distance moved since last tick (Horizontal only)
            double dx = player.getX() - lastBedrockX;
            double dz = player.getZ() - lastBedrockZ;
            double br_distSqr = dx * dx + dz * dz;

            // Update last position for next tick
            lastBedrockX = player.getX();
            lastBedrockZ = player.getZ();

            // Logic: Must be on ground, moving, and standing on Bedrock
            // Threshold 0.0001 to avoid jitter counting
            if (player.isOnGround() && br_distSqr > 0.0001) {
                net.minecraft.util.math.BlockPos groundPos = player.getBlockPos().down();
                if (player.getWorld().getBlockState(groundPos).isOf(net.minecraft.block.Blocks.BEDROCK)) {
                    // Convert blocks to cm (1 block = 100 cm)
                    int cmMoved = (int) (Math.sqrt(br_distSqr) * 100.0);
                    if (cmMoved > 0) {
                        serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.WALK_ON_BEDROCK), cmMoved);
                    }
                }
            }

            // === 下界旅行检测 (每 Tick 检测) ===
            // 只有在下界时才计算
            if (player.getWorld().getRegistryKey() == World.NETHER) {
                if (!initializedNetherPos) {
                    lastNetherX = player.getX();
                    lastNetherY = player.getY();
                    lastNetherZ = player.getZ();
                    initializedNetherPos = true;
                }

                double nether_dx = player.getX() - lastNetherX;
                double nether_dy = player.getY() - lastNetherY;
                double nether_dz = player.getZ() - lastNetherZ;
                // 计算三维距离
                double nether_distSqr = nether_dx*nether_dx + nether_dy*nether_dy + nether_dz*nether_dz;

                if (nether_distSqr > 0.0001) {
                    int cmMoved = (int)(Math.sqrt(nether_distSqr) * 100.0);
                    if (cmMoved > 0) {
                        serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.TRAVEL_NETHER), cmMoved);
                    }
                }

                // 更新坐标
                lastNetherX = player.getX();
                lastNetherY = player.getY();
                lastNetherZ = player.getZ();
            } else {
                // 如果离开了下界，重置初始化标志，以便下次进入时重新锚定
                initializedNetherPos = false;
            }

            if (!initializedPos) {
                lastX = player.getX();
                lastY = player.getY();
                lastZ = player.getZ();
                initializedPos = true;
            }

            double distSqr = player.squaredDistanceTo(lastX, lastY, lastZ);

            // 只有移动了才计算
            if (distSqr > 0.0001) {
                double distCm = Math.sqrt(distSqr) * 100.0;

                // 1. 维度里程统计 (用于解锁技能)
                Identifier dimId = player.getWorld().getRegistryKey().getValue();
                if (dimId.equals(new Identifier("minecraft:overworld"))) {
                    serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.TRAVEL_OVERWORLD), (int)distCm);
                } else if (dimId.equals(new Identifier("minecraft:the_nether"))) {
                    // 原有的 TRAVEL_NETHER 统计也可以复用，以后重构
                    serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.TRAVEL_NETHER), (int)distCm);
                } else if (dimId.equals(new Identifier("minecraft:the_end"))) {
                    serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.TRAVEL_END), (int)distCm);
                }

                // 2. 人力发电机效果
                if (PacketUtils.isSkillActive(serverPlayer, "human_dynamo")) {
                    dynamoDistAccumulator += distCm;

                    // 100米 = 10000cm
                    if (dynamoDistAccumulator >= 10000) {
                        int level = PacketUtils.getSkillLevel(serverPlayer, "human_dynamo");
                        int xpAmount = 0;
                        if (level == 1) xpAmount = 5;
                        else if (level == 2) xpAmount = 8;
                        else if (level >= 3) xpAmount = 11;

                        player.addExperience(xpAmount);
                        // 播放轻微的音效提示
                        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 2.0f);

                        // 减去 100米，保留余数
                        dynamoDistAccumulator -= 10000;
                    }
                }
                // === 水下移动统计 ===
                // 只要整个人泡在水里 (isSubmergedIn) 且移动，就算 (包含游泳和水底走)
                if (player.isSubmergedIn(FluidTags.WATER)) {
                    serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.MOVE_UNDERWATER), (int)distCm);
                }
            }

            // 更新坐标
            lastX = player.getX();
            lastY = player.getY();
            lastZ = player.getZ();

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

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickFireLogic(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            // 1. === 火焰抵抗 Lv2：燃烧时间减半 ===
            // 逻辑：每 Tick 额外减少 1 点 fireTicks
            if (player.isOnFire() && PacketUtils.getSkillLevel(serverPlayer, "fire_resistance") >= 2) {
                // getFireTicks 是 Accessor 或者原版方法，如果 Fabric 映射没有，可能需要 ((Entity)player).getFireTicks()
                // 这里假设能直接调用或者你有 Accessor
                int ticks = player.getFireTicks();
                if (ticks > 0) {
                    // 额外减 1，加上原版的减 1，等于每 Tick 减 2 -> 时间减半
                    player.setFireTicks(ticks - 1);
                }
            }

            // 2. === 升级条件：熔浆游泳追踪 ===
            if (player.isInLava()) {
                // 简单的距离判定：如果这一刻在移动
                double velocity = player.getVelocity().length();
                if (velocity > 0.01) {
                    // 增加统计数据 (粗略估算，每 Tick 移动的距离转为 cm)
                    // 更精确的做法是计算 (x-lastX, y-lastY, z-lastZ)，这里简化处理
                    int distCm = (int)(velocity * 100);
                    if (distCm > 0) {
                        serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.SWIM_IN_LAVA), distCm);

                        // 实时检查是否达标并同步 (可选，PacketUtils.syncSkillData 可能会在其他地方统一调用)
                        // 为了 UI 实时更新，建议加上
                        // PacketUtils.syncSkillData(serverPlayer);
                    }
                }
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickThermal(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        // 只有服务端玩家执行
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            // 每 10 tick (0.5秒) 更新一次即可，不需要每 tick 都更新，节省性能
            // 这样着火灭了之后最多延迟 0.5秒 属性消失，玩家感觉不到
            if (player.age % 10 == 0) {
                SkillEffectHandler.updateThermalDynamo(serverPlayer);
            }
        }
    }

    // 挖掘速度修改
    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void modifyMiningSpeed(BlockState block, CallbackInfoReturnable<Float> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        // === [重要] 移除 "if (player.getWorld().isClient) return;" ===
        // 客户端必须运行此逻辑，否则挖掘进度条会显示错误（挖掘看起来很慢，但方块突然消失）。

        float speed = cir.getReturnValue();

        // === [修复] 双端通用的技能检查 ===
        // 我们不能使用 PacketUtils，因为它需要 ServerPlayerEntity。
        // 我们直接通过接口读取 NBT，这在 ClientPlayerEntity 和 ServerPlayerEntity 上都有效。
        IEntityDataSaver dataSaver = (IEntityDataSaver) player;
        NbtCompound nbt = dataSaver.getPersistentData();

        // 辅助变量：安全获取技能等级 (如果未解锁则为 0)
        int lumberjackLevel = 0;
        int minerFrenzyLevel = 0;

        if (nbt.contains("skill_levels")) {
            NbtCompound levels = nbt.getCompound("skill_levels");
            NbtCompound disabled = nbt.contains("disabled_skills") ? nbt.getCompound("disabled_skills") : new NbtCompound();

            // 获取等级，同时检查是否被禁用
            if (!disabled.getBoolean("lumberjack")) {
                lumberjackLevel = levels.getInt("lumberjack");
            }
            if (!disabled.getBoolean("miner_frenzy")) {
                minerFrenzyLevel = levels.getInt("miner_frenzy");
            }
        }

        // A. 森林主宰 (Lumberjack)
        // 条件：目标是原木或树叶
        if (block.isIn(BlockTags.LOGS) || block.isIn(BlockTags.LEAVES)) {
            if (lumberjackLevel > 0) {
                // 提升倍率：Lv1=1.2x, Lv2=1.4x, Lv3=1.6x
                float multiplier = 1.0f + (lumberjackLevel * 0.2f) + (lumberjackLevel == 3 ? 0.2f : 0);
                speed *= multiplier;
            }
        }

        // B. 矿工狂热 (Miner's Frenzy)
        if (player.getMainHandStack().getItem() instanceof PickaxeItem) {
            String id = net.minecraft.registry.Registries.BLOCK.getId(block.getBlock()).getPath();
            // 简单的关键词匹配
            boolean isStoneType = id.contains("stone") || id.contains("deepslate") || id.contains("diorite") ||
                    id.contains("andesite") || id.contains("granite");

            if (isStoneType && minerFrenzyLevel > 0) {
                // 提升倍率
                float multiplier = 1.0f + (minerFrenzyLevel * 0.9f);
                speed *= multiplier;
            }
        }

        cir.setReturnValue(speed);
    }

    @Unique private int glassWaterBreathingTicks = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickBrainInAJar(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        // 只在服务端处理 Buff 给予 (客户端会自动同步 Buff 状态)
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            if (PacketUtils.isSkillActive(serverPlayer, "brain_in_a_jar")) {
                ItemStack headStack = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
                boolean isGlass = !headStack.isEmpty() && headStack.getItem() instanceof net.minecraft.item.BlockItem bi &&
                        bi.getBlock() instanceof net.minecraft.block.AbstractGlassBlock;

                if (isGlass) {
                    // 1. 如果头在空气中 (不在水里)
                    if (!player.isSubmergedIn(FluidTags.WATER)) {

                        // 获取最大时间
                        int level = PacketUtils.getSkillLevel(serverPlayer, "brain_in_a_jar");
                        int duration = 12 * 20;
                        if (level >= 2) duration = 24 * 20;
                        if (level >= 3) duration = 36 * 20;
                        if (level >= 4) duration = 48 * 20;
                        if (level >= 5) duration = 60 * 20;

                        // 2. 补充 Buff (如果当前没有，或者持续时间不满，就覆盖)
                        // 使用 ambient=false, showParticles=false, showIcon=true
                        // 这样每 tick 刷新，玩家看到的就是满的时间
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, duration, 0, false, false, true));
                    }
                    // 3. 如果头在水里 -> 什么都不做，让 Buff 自然倒计时
                }
            }
        }
    }

    @Unique private int melancholicTimer = 0;
    @Unique private int melancholicBuffRetention = 0; // 抗性 buff 的残留时间

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickMelancholic(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {

            if (PacketUtils.isSkillActive(serverPlayer, "melancholic_personality")) {
                int level = PacketUtils.getSkillLevel(serverPlayer, "melancholic_personality");
                World world = player.getWorld();
                BlockPos pos = player.getBlockPos();

                // 1. 环境判定：(下雨 或 雷暴) 且 头顶能看到天
                boolean isWeatherBad = world.isRaining() || world.isThundering();
                // isSkyVisible 判定位置是否能直视天空 (透明方块也算遮挡，如果需要穿过玻璃判定更复杂，这里用原版逻辑)
                boolean isExposed = world.isSkyVisible(pos);

                // 生物群系也得下雨才行 (沙漠里下雨天是不下雨的)
                boolean biomeRains = world.getBiome(pos).value().getPrecipitation(pos) != net.minecraft.world.biome.Biome.Precipitation.NONE;

                boolean conditionMet = isWeatherBad && isExposed && biomeRains;

                // === 效果 1: 缓慢恢复 (Lv1+) ===
                if (conditionMet) {
                    melancholicTimer++;

                    // 每 4秒 (80 tick) 回半颗心
                    if (melancholicTimer % 80 == 0) {
                        if (player.getHealth() < player.getMaxHealth()) {
                            player.heal(1.0f);
                        }
                    }
                    // 每 6秒 (120 tick) 回半个鸡腿
                    if (melancholicTimer % 120 == 0) {
                        player.getHungerManager().add(1, 0.0f);
                    }

                    // 满足条件时，重置残留时间 (12秒 = 240 tick)
                    if (level >= 2) {
                        melancholicBuffRetention = 240;
                    }
                } else {
                    // 不满足条件，计时器不重置，但也不增加，或者你可以选择归零
                    // 这里归零比较合理，断了就要重新蓄力
                    melancholicTimer = 0;
                }

                // === 效果 2: 抗性提升 (Lv2+) ===
                if (level >= 2) {
                    if (melancholicBuffRetention > 0) {
                        // 给予抗性 I (showParticles=false)
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, 0, false, false, true));
                        melancholicBuffRetention--;
                    }
                }
            }
        }
    }

    // === 补全：上升高度统计 ===
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

            // 1. 只统计上升 (dy > 0)
            // 2. 过滤掉瞬间传送 (例如 dy > 10 通常是传送)
            if (dy > 0 && dy < 10) {
                // 增加统计 (单位 cm)
                serverPlayer.getStatHandler().increaseStat(serverPlayer, Stats.CUSTOM.getOrCreateStat(Ascension.ASCEND_HEIGHT), (int)(dy * 100));
            }

            // 更新高度
            lastYForAscend = player.getY();
        }
    }

    @Unique
    private void resetFurnace(ServerPlayerEntity player, ItemStack currentStack) {
        furnaceTimer = 0;
        lastSmeltingStack = currentStack.copy();
        if (!currentStack.isEmpty()) {
            player.getItemCooldownManager().set(currentStack.getItem(), 0);
        }
    }
}