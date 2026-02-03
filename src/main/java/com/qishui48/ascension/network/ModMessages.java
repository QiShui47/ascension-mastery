package com.qishui48.ascension.network;

import com.qishui48.ascension.Ascension;
import com.qishui48.ascension.skill.*;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.util.PacketUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class ModMessages {
    public static final Identifier UNLOCK_REQUEST_ID = new Identifier(Ascension.MOD_ID, "request_unlock_skill");
    public static final Identifier JUMP_REQUEST_ID = new Identifier(Ascension.MOD_ID, "request_rocket_boost");
    public static final Identifier CHARGED_JUMP_ID = new Identifier(Ascension.MOD_ID, "request_charged_jump");
    public static final Identifier SYNC_REQUEST_ID = new Identifier(Ascension.MOD_ID, "request_sync");
    public static final Identifier TOGGLE_REQUEST_ID = new Identifier(Ascension.MOD_ID, "request_toggle_skill");
    public static final Identifier SHOW_NOTIFICATION_ID = new Identifier(Ascension.MOD_ID, "show_notification");
    public static final Identifier BAMBOO_CUTTING_SYNC_ID = new Identifier(Ascension.MOD_ID, "bamboo_cutting_sync");
    public static final Identifier RESET_SKILLS_ID = new Identifier(Ascension.MOD_ID, "request_reset_skills");
    public static final Identifier EQUIP_REQUEST_ID = new Identifier(Ascension.MOD_ID, "request_equip_skill");
    public static final Identifier SWITCH_SLOT_ID = new Identifier(Ascension.MOD_ID, "request_switch_slot");
    public static final Identifier USE_ACTIVE_SKILL_ID = new Identifier(Ascension.MOD_ID, "request_use_active_skill");
    public static final Identifier MATERIAL_SLOT_CLICK_ID = new Identifier(Ascension.MOD_ID, "click_material_slot");
    public static final Identifier UNEQUIP_REQUEST_ID = new Identifier(Ascension.MOD_ID, "request_unequip_skill");
    public static final Identifier REDUCE_CD_REQUEST_ID = new Identifier(Ascension.MOD_ID, "request_reduce_cd");

    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(UNLOCK_REQUEST_ID, (server, player, handler, buf, responseSender) -> {
            String skillId = buf.readString();

            server.execute(() -> {
                Skill skill = SkillRegistry.get(skillId);
                if (skill == null) return;

                int currentLevel = PacketUtils.getSkillLevel(player, skillId);

                // 检查满级
                if (currentLevel >= skill.maxLevel) {
                    player.sendMessage(Text.translatable("message.ascension.max_level").formatted(Formatting.RED), true);
                    return;
                }

                // 检查互斥
                for (String mutexId : skill.mutexSkills) {
                    if (PacketUtils.isSkillUnlocked(player, mutexId)) {
                        // 如果互斥技能已解锁，发送错误提示
                        player.sendMessage(Text.translatable("message.ascension.mutex_locked",
                                SkillRegistry.get(mutexId).getName()).formatted(Formatting.RED), true);
                        return;
                    }
                }

                // 多父节点检查逻辑 (OR 关系)
                // 原有的逻辑只检查 parentId，现在我们允许 visualParents 中的任意一个解锁也算满足条件
                if (currentLevel == 0) {
                    boolean hasParent = (skill.parentId != null) || !skill.visualParents.isEmpty();

                    if (hasParent) {
                        boolean anyParentUnlocked = false;

                        // 1. 检查主逻辑父节点
                        if (skill.parentId != null && PacketUtils.isSkillUnlocked(player, skill.parentId)) {
                            anyParentUnlocked = true;
                        }

                        // 2. 检查所有视觉父节点
                        if (!anyParentUnlocked) {
                            for (String vParentId : skill.visualParents) {
                                if (PacketUtils.isSkillUnlocked(player, vParentId)) {
                                    anyParentUnlocked = true;
                                    break;
                                }
                            }
                        }

                        // 3. 如果没有任何一个父节点解锁，则禁止解锁该技能
                        if (!anyParentUnlocked) {
                            // 这里为了简单，我们还是显示主父节点的名字，或者你可以改为通用提示
                            String parentName = skill.parentId != null ? SkillRegistry.get(skill.parentId).getName().getString() : "???";
                            player.sendMessage(Text.translatable("message.ascension.parent_locked", parentName).formatted(Formatting.RED), true);
                            return;
                        }
                    }
                }

                // 检查前置
                if (currentLevel == 0 && skill.parentId != null && !PacketUtils.isSkillUnlocked(player, skill.parentId)) {
                    Skill parent = SkillRegistry.get(skill.parentId);
                    player.sendMessage(Text.translatable("message.ascension.parent_locked", parent.getName()).formatted(Formatting.RED), true);
                    return;
                }

                // === 检查当前等级对应的升级条件 ===
                // 0 -> 1级: 检查 level 1 的条件
                // 1 -> 2级: 检查 level 2 的条件
                if (!skill.checkCriteria(player, currentLevel + 1)) {
                    player.sendMessage(Text.translatable("message.ascension.criteria_failed").formatted(Formatting.RED), true);
                    return;
                }

                // 计算消耗 (直接使用 Skill 里的配置，不再额外乘倍率)
                int actualCost = skill.getCost(currentLevel + 1);

                IEntityDataSaver dataSaver = (IEntityDataSaver) player;
                int currentPoints = dataSaver.getPersistentData().getInt("skill_points");

                if (currentPoints < actualCost) {
                    player.sendMessage(Text.translatable("message.ascension.points_needed", actualCost).formatted(Formatting.RED), true);
                    return;
                }

                // 执行交易
                dataSaver.getPersistentData().putInt("skill_points", currentPoints - actualCost);
                // 记录花费（以进行平衡补偿）
                PacketUtils.recordSpending(player, actualCost);
                PacketUtils.unlockSkill(player, skillId);
                SkillEffectHandler.refreshAttributes(player);
                SkillEffectHandler.onSkillUnlocked(player, skillId);

                player.sendMessage(Text.translatable("message.ascension.unlock_success", skill.getName(), (currentLevel + 1)).formatted(Formatting.GREEN), true);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(JUMP_REQUEST_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                if (PacketUtils.isSkillUnlocked(player, "rocket_boost")) {
                    SkillActionHandler.executeBoost(player);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CHARGED_JUMP_ID, (server, player, handler, buf, responseSender) -> {
            float powerRatio = buf.readFloat();
            server.execute(() -> {
                if (PacketUtils.isSkillUnlocked(player, "charged_jump")) {
                    SkillActionHandler.executeChargedJump(player, powerRatio);
                    // === 触发舍身一击准备状态 ===
                    // 只要进行了二段跳操作，就进入准备状态
                    ((com.qishui48.ascension.util.ISacrificialState) player).setSacrificialReady(true);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SYNC_REQUEST_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                PacketUtils.syncSkillData(player);
            });
        });

        // 新增：处理中键切换
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_REQUEST_ID, (server, player, handler, buf, responseSender) -> {
            String skillId = buf.readString();
            server.execute(() -> {
                // 只有已解锁的技能才能切换
                if (PacketUtils.isSkillUnlocked(player, skillId)) {
                    PacketUtils.toggleSkill(player, skillId);
                }
            });
        });

        // 删除了原来手写的一大段逻辑，改为直接调用 PacketUtils 的标准方法
        ServerPlayNetworking.registerGlobalReceiver(RESET_SKILLS_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                PacketUtils.resetSkills(player);
            });
        });

        // 1. 装备技能 (Drag & Drop)
        ServerPlayNetworking.registerGlobalReceiver(EQUIP_REQUEST_ID, (server, player, handler, buf, responseSender) -> {
            String skillId = buf.readString();
            int slotIndex = buf.readInt();

            server.execute(() -> {
                if (!PacketUtils.isSkillUnlocked(player, skillId)) return;

                IEntityDataSaver data = (IEntityDataSaver) player;
                NbtCompound nbt = data.getPersistentData();
                NbtList activeSlots = nbt.getList("active_skill_slots", NbtElement.COMPOUND_TYPE);

                // 查重
                for (int i = 0; i < activeSlots.size(); i++) {
                    // 如果这个技能已经在别的槽位里了
                    if (activeSlots.getCompound(i).getString("id").equals(skillId)) {
                        // 方案 A: 互换 (高级) -> 这里为了简单，直接拒绝并提示
                        // 方案 B: 拒绝
                        if (i != slotIndex) { // 如果不是拖到自己身上
                            player.sendMessage(Text.translatable("message.ascension.skill_already_equipped").formatted(Formatting.RED), true);
                            return;
                        }
                    }
                }

                // 填充空槽位
                while (activeSlots.size() <= slotIndex) {
                    NbtCompound empty = new NbtCompound();
                    empty.putString("id", "");
                    empty.putInt("cooldown", 0);
                    activeSlots.add(empty);
                }

                NbtCompound slotNbt = activeSlots.getCompound(slotIndex);
                String oldSkillId = slotNbt.getString("id");

                // 如果当前槽位里的技能正在冷却，则禁止替换
                // 只有冷却结束 (<=0) 或者槽位本来是空的，才允许换
                if (slotNbt.getInt("cooldown") > 0 && !slotNbt.getString("id").isEmpty()) {
                    player.sendMessage(Text.translatable("message.ascension.slot_cooldown_active").formatted(Formatting.RED), true);
                    return;
                }

                // === 如果槽位里原本有技能，检查旧技能是否充能满 ===
                if (!oldSkillId.isEmpty()) {
                    int charges = slotNbt.getInt("charges");
                    int level = PacketUtils.getSkillLevel(player, oldSkillId);
                    Skill oldSkill = SkillRegistry.get(oldSkillId);

                    if (oldSkill instanceof ActiveSkill activeOldSkill) {
                        int maxCharges = activeOldSkill.getMaxCharges(level);
                        if (charges < maxCharges) {
                            player.sendMessage(Text.translatable("message.ascension.skill_not_recharged").formatted(Formatting.RED), true);
                            return;
                        }
                    }
                }

                slotNbt.putString("id", skillId);
                slotNbt.putLong("cooldown_end", 0);
                slotNbt.putInt("cooldown_total", 0);

                // === 初始化充能 ===
                Skill skill = SkillRegistry.get(skillId);
                if (skill instanceof ActiveSkill activeSkill) {
                    // 获取当前等级的最大充能
                    int level = PacketUtils.getSkillLevel(player, skillId);
                    int maxCharges = activeSkill.getMaxCharges(level);
                    slotNbt.putInt("charges", maxCharges);
                } else {
                    slotNbt.putInt("charges", 1);
                }

                nbt.put("active_skill_slots", activeSlots);
                PacketUtils.syncSkillData(player);
            });
        });

        // 2. 切换槽位
        ServerPlayNetworking.registerGlobalReceiver(SWITCH_SLOT_ID, (server, player, handler, buf, responseSender) -> {
            int slotIndex = buf.readInt();
            server.execute(() -> {
                if (slotIndex >= 0 && slotIndex < 5) {
                    PacketUtils.setData(player, "selected_active_slot", slotIndex);
                }
            });
        });

        // 3. 使用技能 (多态调用)
        ServerPlayNetworking.registerGlobalReceiver(USE_ACTIVE_SKILL_ID, (server, player, handler, buf, responseSender) -> {
            boolean isSecondary = buf.readBoolean();

            server.execute(() -> {
                IEntityDataSaver data = (IEntityDataSaver) player;
                NbtCompound nbt = data.getPersistentData();
                int selectedSlot = nbt.getInt("selected_active_slot");
                NbtList activeSlots = nbt.getList("active_skill_slots", 10);

                if (selectedSlot >= activeSlots.size()) return;
                NbtCompound slotNbt = activeSlots.getCompound(selectedSlot);
                String skillId = slotNbt.getString("id");

                long currentTime = player.getWorld().getTime();
                long cooldownEnd = slotNbt.getLong("cooldown_end");

                // 懒加载恢复逻辑：如果冷却已结束且充能为0，补满充能
                int currentCharges = slotNbt.getInt("charges");
                Skill rawSkill = SkillRegistry.get(skillId);
                if (!(rawSkill instanceof ActiveSkill)) return;
                ActiveSkill activeSkill = (ActiveSkill) rawSkill;
                int level = PacketUtils.getSkillLevel(player, skillId);
                int maxCharges = activeSkill.getMaxCharges(level);

                if (currentTime >= cooldownEnd && currentCharges == 0) {
                    currentCharges = maxCharges;
                    // 这里不急着写回NBT，下面操作完一起写
                }

                // 冷却检查 (如果有充能，视为无冷却)
                if (currentCharges <= 0) {
                    // 确实在冷却中，且没充能了
                    return;
                }

                // 尝试执行技能
                boolean success = activeSkill.cast(player, isSecondary);

                nbt.put("active_skill_slots", activeSlots);
                PacketUtils.syncSkillData(player);
            });
        });

        // 处理材料槽点击
        ServerPlayNetworking.registerGlobalReceiver(MATERIAL_SLOT_CLICK_ID, (server, player, handler, buf, responseSender) -> {
            int slotIndex = buf.readInt();
            server.execute(() -> {
                IEntityDataSaver data = (IEntityDataSaver) player;
                NbtCompound nbt = data.getPersistentData();
                NbtList materials = nbt.getList("casting_materials", 10); // 10 = Compound

                // 确保列表大小
                while (materials.size() <= slotIndex) {
                    materials.add(new NbtCompound());
                }

                ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
                ItemStack slotStack = ItemStack.fromNbt(materials.getCompound(slotIndex));

                // 逻辑：
                // 1. 鼠标拿着东西 -> 放入 (如果是空的) 或 交换
                // 2. 鼠标没拿东西 -> 取出

                if (!cursorStack.isEmpty()) {
                    // 放入 / 交换
                    if (slotStack.isEmpty()) {
                        // 放入
                        NbtCompound newSlotNbt = new NbtCompound();
                        cursorStack.writeNbt(newSlotNbt);
                        materials.set(slotIndex, newSlotNbt);
                        player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
                    } else {
                        // 交换
                        // 简单处理：如果是同种物品，尝试堆叠；否则交换
                        if (ItemStack.canCombine(cursorStack, slotStack)) {
                            int space = slotStack.getMaxCount() - slotStack.getCount();
                            if (space > 0) {
                                int moveCount = Math.min(space, cursorStack.getCount());

                                // 增加槽位物品数量
                                slotStack.increment(moveCount);
                                NbtCompound newSlotNbt = new NbtCompound();
                                slotStack.writeNbt(newSlotNbt); // 更新 NBT 数据
                                materials.set(slotIndex, newSlotNbt);

                                // 减少鼠标物品数量
                                cursorStack.decrement(moveCount);
                                player.currentScreenHandler.setCursorStack(cursorStack);
                            }
                        } else {
                            // 交换
                            NbtCompound newSlotNbt = new NbtCompound();
                            cursorStack.writeNbt(newSlotNbt);
                            materials.set(slotIndex, newSlotNbt);
                            player.currentScreenHandler.setCursorStack(slotStack);
                        }
                    }
                } else {
                    // 取出
                    if (!slotStack.isEmpty()) {
                        player.currentScreenHandler.setCursorStack(slotStack);
                        materials.set(slotIndex, new NbtCompound()); // 清空槽位
                    }
                }

                nbt.put("casting_materials", materials);
                PacketUtils.syncSkillData(player);
                // 这会告诉客户端“现在光标上确实有这个物品”，防止客户端因为预测失败而丢弃物品
                player.currentScreenHandler.syncState();
            });
        });

        // 处理卸载技能
        ServerPlayNetworking.registerGlobalReceiver(UNEQUIP_REQUEST_ID, (server, player, handler, buf, responseSender) -> {
            int slotIndex = buf.readInt();
            server.execute(() -> {
                IEntityDataSaver data = (IEntityDataSaver) player;
                NbtCompound nbt = data.getPersistentData();
                NbtList activeSlots = nbt.getList("active_skill_slots", 10);

                if (slotIndex >= 0 && slotIndex < activeSlots.size()) {
                    NbtCompound slotNbt = activeSlots.getCompound(slotIndex);
                    String skillId = slotNbt.getString("id");

                    if (!skillId.isEmpty()) {
                        // 检查充能是否满
                        int charges = slotNbt.getInt("charges");
                        int level = PacketUtils.getSkillLevel(player, skillId);
                        Skill skill = SkillRegistry.get(skillId);

                        if (skill instanceof ActiveSkill activeSkill) {
                            int maxCharges = activeSkill.getMaxCharges(level);
                            // 如果充能不满，拒绝卸载
                            if (charges < maxCharges) {
                                player.sendMessage(Text.translatable("message.ascension.skill_not_recharged").formatted(Formatting.RED), true);
                                return;
                            }
                        }
                    }

                    // 允许卸载
                    activeSlots.set(slotIndex, new NbtCompound());
                    nbt.put("active_skill_slots", activeSlots);
                    PacketUtils.syncSkillData(player);
                }
            });
        });

        // 中键减冷却
        ServerPlayNetworking.registerGlobalReceiver(REDUCE_CD_REQUEST_ID, (server, player, handler, buf, responseSender) -> {
            int slotIndex = buf.readInt();
            server.execute(() -> {
                IEntityDataSaver data = (IEntityDataSaver) player;
                NbtCompound nbt = data.getPersistentData();
                NbtList materials = nbt.getList("casting_materials", 10);
                NbtList activeSlots = nbt.getList("active_skill_slots", 10);
                long now = player.getWorld().getTime();

                if (slotIndex >= materials.size()) return;

                ItemStack materialStack = ItemStack.fromNbt(materials.getCompound(slotIndex));
                if (materialStack.isEmpty()) return;

                boolean anyReduced = false; // 标记是否有技能成功减少了冷却

                // === 1. 遍历所有主动技能槽 ===
                for (int i = 0; i < activeSlots.size(); i++) {
                    NbtCompound slotNbt = activeSlots.getCompound(i);
                    long end = slotNbt.getLong("cooldown_end");
                    int total = slotNbt.getInt("cooldown_total");
                    String skillId = slotNbt.getString("id");

                    // 检查是否在冷却中
                    if (end > now && total > 0 && !skillId.isEmpty()) {
                        Skill rawSkill = SkillRegistry.get(skillId);

                        // === 核心判定：材料是否匹配？ ===
                        if (rawSkill instanceof ActiveSkill activeSkill) {
                            boolean isMatch = false;
                            // 遍历该技能的所有合法耗材
                            for (ActiveSkill.CastIngredient ingredient : activeSkill.ingredients) {
                                if (ingredient.item == materialStack.getItem()) {
                                    isMatch = true;
                                    break;
                                }
                            }

                            if (isMatch) {
                                // 执行减 CD 逻辑
                                long reduced = (long)(total * 0.25);
                                long newEnd = end - reduced;
                                if (newEnd < now) newEnd = now;

                                slotNbt.putLong("cooldown_end", newEnd);
                                anyReduced = true;
                            }
                        }
                    }
                }

                // === 2. 只有当至少有一个技能被加速时，才消耗材料 ===
                if (anyReduced) {
                    materialStack.decrement(1);
                    if (materialStack.isEmpty()) {
                        materials.set(slotIndex, new NbtCompound());
                    } else {
                        NbtCompound newNbt = new NbtCompound();
                        materialStack.writeNbt(newNbt);
                        materials.set(slotIndex, newNbt);
                    }

                    nbt.put("casting_materials", materials);
                    nbt.put("active_skill_slots", activeSlots);
                    PacketUtils.syncSkillData(player);
                    player.currentScreenHandler.syncState();

                    player.sendMessage(Text.translatable("message.ascension.cooldown_reduced").formatted(Formatting.AQUA), true);
                } else {
                    // 没有技能需要用这个材料加速
                    player.sendMessage(Text.translatable("message.ascension.cooldown_full").formatted(Formatting.YELLOW), true);
                }
            });
        });
    }
}