package com.qishui48.ascension.mixin;

import com.qishui48.ascension.util.PacketUtils;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;

@Mixin(EnchantedBookItem.class)
public class EnchantedBookItemMixin extends Item {

    public EnchantedBookItemMixin(Settings settings) {
        super(settings);
    }

    // 1. 开始读书检查
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (user instanceof ServerPlayerEntity serverPlayer) {
            // 检查技能
            if (PacketUtils.isSkillActive(serverPlayer, "golden_house")) {

                // [新增] 启动门槛：如果太饿（<= 3个鸡腿），无法开始
                if (serverPlayer.getHungerManager().getFoodLevel() <= 6) {
                    serverPlayer.sendMessage(Text.translatable("message.ascension.too_hungry_to_start"), true);
                    return TypedActionResult.fail(stack);
                }

                user.setCurrentHand(hand);
                return TypedActionResult.consume(stack);
            }
        } else if (user.getWorld().isClient) {
            // 客户端预判：如果太饿也不显示动画
            if (user.getHungerManager().getFoodLevel() <= 6) {
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.consume(stack);
        }

        return super.use(world, user, hand);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    // 2. 过程检查
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {

            // [新增] 持续检查：如果读着读着饿晕了（<= 0），强制打断
            if (player.getHungerManager().getFoodLevel() <= 0) {
                player.stopUsingItem();
                player.sendMessage(Text.translatable("message.ascension.too_hungry_to_read"), true);
                return;
            }

            int currentProgress = 0;
            if (stack.hasNbt() && stack.getNbt().contains("ReadingProgress")) {
                currentProgress = stack.getNbt().getInt("ReadingProgress");
            }

            // 读完 (200 ticks = 10秒)
            if (currentProgress >= 200) {
                finishBook(player, stack);
                player.stopUsingItem();
                return;
            }

            // 增加进度
            currentProgress++;
            stack.getOrCreateNbt().putInt("ReadingProgress", currentProgress);

            // 扣饱食度 (每10 tick 扣一次，总共扣20次)
            // 每次 addExhaustion(4.0f) 约等于消耗 1 点 hunger/saturation
            // 读完一本书消耗约 20 点 hunger (10个鸡腿)，非常昂贵！
            if (currentProgress % 10 == 0) {
                player.addExhaustion(4.0f);
            }
        }
    }

    // 3. 结算逻辑：稀有度计算
    private void finishBook(ServerPlayerEntity player, ItemStack stack) {
        int skillLevel = PacketUtils.getSkillLevel(player, "golden_house");

        // --- 计算书本价值 ---
        int bookXp = calculateBookXp(stack);

        // --- 技能等级倍率 ---
        // Lv1: 100%, Lv2: 120%, Lv3: 150%
        float multiplier = skillLevel == 3 ? 1.5f : (skillLevel == 2 ? 1.2f : 1.0f);

        int finalXp = (int) (bookXp * multiplier);

        // 确保至少给点经验 (防止某些空附魔书的边缘情况)
        if (finalXp < 10) finalXp = 10;

        // 限制：最多升一级
        int xpToNextLevel = player.getNextLevelExperience() - (int)(player.experienceProgress * player.getNextLevelExperience());
        int actualXp = Math.min(finalXp, xpToNextLevel);

        player.addExperience(actualXp);
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);

        // 播放个音效提示这是本好书
        if (bookXp > 100) {
            player.playSound(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        }

        // 消耗书本
        if (!player.isCreative()) {
            stack.decrement(1);
        } else {
            if (stack.hasNbt()) stack.getNbt().remove("ReadingProgress");
        }
    }

    // 辅助方法：计算书本的基础XP价值
    private int calculateBookXp(ItemStack stack) {
        Map<Enchantment, Integer> enchants = EnchantmentHelper.get(stack);
        int totalValue = 0;

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();
            int rarityValue = 0;

            // 根据稀有度定价
            switch (enchantment.getRarity()) {
                case COMMON:
                    rarityValue = 15;
                    break;
                case UNCOMMON:
                    rarityValue = 30;
                    break;
                case RARE:
                    rarityValue = 60;
                    break;
                case VERY_RARE:
                    rarityValue = 100;
                    break;
            }

            // 诅咒也是 Very Rare，但那是“禁忌知识”，也给高分！(或者你可以改成减分)

            // 计算公式：稀有度分 * 等级
            // 例如：锋利 V (Common) = 15 * 5 = 75
            // 例如：经验修补 (Rare) = 60 * 1 = 60
            // 例如：迅捷潜行 III (Very Rare) = 100 * 3 = 300 (极品书！)
            totalValue += rarityValue * level;
        }

        // 基础保底，防止有些书啥都没有
        return totalValue > 0 ? totalValue : 10;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().contains("ReadingProgress") && stack.getNbt().getInt("ReadingProgress") > 0;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt().contains("ReadingProgress")) {
            int progress = stack.getNbt().getInt("ReadingProgress");
            return Math.min(13, (progress * 13) / 200);
        }
        return 0;
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0xFFD700; // 金色
    }
}