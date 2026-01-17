package com.qishui48.ascension.mixin.enchantments;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.util.IModProjectile;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.util.math.Vec3d;

@Mixin(BowItem.class)
public class BowItemMixin {

    @Redirect(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
    private boolean onSpawnArrow(World world, net.minecraft.entity.Entity entity) {
        if (entity instanceof PersistentProjectileEntity arrow && arrow.getOwner() instanceof PlayerEntity player) {
            ItemStack currentBow = player.getActiveItem();
            if (!currentBow.isEmpty() && currentBow.getItem() instanceof BowItem) {

                // === 1. 歼星炮 ===
                if (EnchantmentHelper.getLevel(Ascension.DEATH_STAR_CANNON, currentBow) > 0) {
                    ((IModProjectile) arrow).setDeathStar(true);
                    arrow.setNoGravity(true);
                    Vec3d v = arrow.getVelocity();
                    arrow.setVelocity(v.x*3, v.y*3, v.z*3);
                    if (!player.isCreative()) {
                        currentBow.damage(50, player, (p) -> p.sendToolBreakStatus(player.getActiveHand()));
                    }
                }

                // === 2. 生命之力 ===
                if (EnchantmentHelper.getLevel(Ascension.LIFE_FORCE, currentBow) > 0) {
                    ((IModProjectile) arrow).setLifeForce(true);
                }

                // === 3. 随身水桶 (重构版调用) ===
                if (EnchantmentHelper.getLevel(Ascension.POCKET_BUCKET, currentBow) > 0) {
                    // 水桶比较特殊，它的逻辑是替换，所以如果你不想用通用方法，保留之前的逻辑也可以
                    // 这里为了统一演示，假设我们只消耗水桶（不返还空桶的话用通用方法；要返还空桶建议保留你原来的写法）
                    // **注意**：为了简单，这里我保留你之前的“水桶替换”逻辑，不使用 removeItems，因为它需要特殊的“返还桶”逻辑
                    boolean hasBucket = false;
                    if (player.isCreative()) {
                        hasBucket = true;
                    } else {
                        for (int i = 0; i < player.getInventory().size(); i++) {
                            ItemStack stack = player.getInventory().getStack(i);
                            if (stack.getItem() == Items.WATER_BUCKET) {
                                player.getInventory().setStack(i, new ItemStack(Items.BUCKET));
                                hasBucket = true;
                                break;
                            }
                        }
                    }
                    if (hasBucket) ((IModProjectile) arrow).setPocketBucket(true);
                }

                // === 4. 随身护卫 (消耗 18 铁锭) ===
                if (EnchantmentHelper.getLevel(Ascension.POCKET_GUARD, currentBow) > 0) {
                    if (player.isCreative()) {
                        ((IModProjectile) arrow).setPocketGuard(true);
                    } else {
                        // 注意参数顺序变了：先传数量，再传物品
                        if (removeItems(player, 18, Items.IRON_INGOT)) {
                            ((IModProjectile) arrow).setPocketGuard(true);
                        }
                    }
                }

                // === 5. 随身猫猫 (消耗 3 条鱼：鳕鱼 或 鲑鱼) ===
                if (EnchantmentHelper.getLevel(Ascension.POCKET_CAT, currentBow) > 0) {
                    if (player.isCreative()) {
                        ((IModProjectile) arrow).setPocketCat(true);
                    } else {
                        // 2. 升级：同时支持鳕鱼和鲑鱼
                        // 逻辑：只要背包里的 鳕鱼+鲑鱼 总数 >= 3，就会扣除
                        if (removeItems(player, 3, Items.COD, Items.SALMON)) {
                            ((IModProjectile) arrow).setPocketCat(true);
                        }
                    }
                }
            }
        }
        return world.spawnEntity(entity);
    }

    /**
     * 辅助方法：检查背包并扣除指定数量的物品（支持多种物品混合支付）
     * 例如：removeItems(player, 3, Items.COD, Items.SALMON)
     * 表示可以扣除 2个鳕鱼+1个鲑鱼，或者3个鲑鱼。
     */
    @Unique
    private boolean removeItems(PlayerEntity player, int countNeeded, Item... validItems) {
        // 1. 先计算背包里所有符合条件的物品总和
        int totalCount = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            // 检查这个格子的物品是否在 validItems 列表里
            for (Item validItem : validItems) {
                if (stack.isOf(validItem)) {
                    totalCount += stack.getCount();
                    break;
                }
            }
        }

        // 2. 如果不够，直接返回 false
        if (totalCount < countNeeded) {
            return false;
        }

        // 3. 如果够，开始扣除
        int remaining = countNeeded;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (remaining <= 0) break; // 扣完了

            ItemStack stack = player.getInventory().getStack(i);

            // 再次检查是否是有效物品
            boolean isValid = false;
            for (Item validItem : validItems) {
                if (stack.isOf(validItem)) {
                    isValid = true;
                    break;
                }
            }

            if (isValid) {
                int deduct = Math.min(stack.getCount(), remaining);
                stack.decrement(deduct); // 扣除数量
                remaining -= deduct;
            }
        }
        return true; // 扣除成功
    }
}