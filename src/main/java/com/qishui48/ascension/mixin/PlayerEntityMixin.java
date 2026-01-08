package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import java.util.Optional;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Shadow public abstract HungerManager getHungerManager();

    @Unique private int lastFoodLevel = 20;
    @Unique private int furnaceTimer = 0;
    @Unique private ItemStack lastSmeltingStack = ItemStack.EMPTY;

    // 隐藏技能相关变量
    @Unique private double lastX, lastY, lastZ;
    @Unique private boolean initializedPos = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            int currentFood = this.getHungerManager().getFoodLevel();

            // 1. 饥饿体质逻辑
            if (currentFood < this.lastFoodLevel) {
                int level = PacketUtils.getSkillLevel(serverPlayer, "hunger_constitution");
                if (level > 0) {
                    int amplifier = level - 1;
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, amplifier));
                }
            }
            this.lastFoodLevel = currentFood;

            // 2. === 新增：饥饿爆发 (Hidden Skill) 逻辑与发现机制 ===

            // 初始化位置
            if (!initializedPos) {
                lastX = player.getX();
                lastY = player.getY();
                lastZ = player.getZ();
                initializedPos = true;
            }

            // 计算位移距离
            double distSqr = player.squaredDistanceTo(lastX, lastY, lastZ);

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
            lastX = player.getX();
            lastY = player.getY();
            lastZ = player.getZ();
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

    // 监听吃东西
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
            int currentPoints = nbt.getInt("my_global_skills");
            nbt.putInt("my_global_skills", currentPoints + points);
            PacketUtils.syncSkillData(serverPlayer);

            Text msg = Text.literal("§b[美食家] §f品尝 ")
                    .append(Text.translatable(stack.getItem().getTranslationKey()).formatted(Formatting.GOLD))
                    .append(Text.literal(" §a+" + points + " 技能点").formatted(Formatting.BOLD));
            PacketUtils.sendNotification(serverPlayer, msg);

            serverPlayer.playSound(SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.5f, 1.5f);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickBiomeCheck(CallbackInfo ci) {
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

            // 奖励 2 点 (鼓励探索)
            int points = 2;
            int currentPoints = nbt.getInt("my_global_skills");
            nbt.putInt("my_global_skills", currentPoints + points);

            PacketUtils.syncSkillData(serverPlayer);

            // 获取群系名称 (稍微麻烦点，需要用 translate key)
            // 这里的逻辑稍微简化，直接用 ID 或者尝试获取 Name
            // Text.translatableUtil.getTranslationKey 比较复杂，直接构造 key
            String transKey = "biome." + biomeId.replace(':', '.');
            Text biomeName = Text.translatable(transKey).formatted(Formatting.GREEN);

            Text msg = Text.literal("§b[新视界] §f发现群系 ")
                    .append(biomeName)
                    .append(Text.literal(" §a+" + points + " 技能点").formatted(Formatting.BOLD));

            PacketUtils.sendNotification(serverPlayer, msg);

            player.playSound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.PLAYERS, 0.5f, 1.2f);
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