package com.qishui48.ascension.screen;

import com.qishui48.ascension.AscensionClient;
import com.qishui48.ascension.network.ModMessages; // 确保导入了这个
import com.qishui48.ascension.skill.Skill;
import com.qishui48.ascension.skill.SkillRegistry;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.skill.UnlockCriterion;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class SkillTreeScreen extends Screen {
    private static double savedScrollX = Double.NaN;
    private static double savedScrollY = Double.NaN;
    private static double savedScale = 1.0;

    private double scrollX = 0;
    private double scrollY = 0;
    private double scale = 1.0;
    private boolean isDragging = false;
    private int tickCounter = 0;

    public SkillTreeScreen() { super(Text.translatable("gui.ascension.title")); }

    @Override
    protected void init() {
        super.init();
        if (!Double.isNaN(savedScrollX)) {
            this.scrollX = savedScrollX;
            this.scrollY = savedScrollY;
            this.scale = savedScale;
        } else {
            this.scrollX = 0;
            this.scrollY = 40 - (this.height / 2.0);
            this.scale = 1.0;
        }

        // === 1. 每次打开 UI 时，强制请求同步 ===
        ClientPlayNetworking.send(ModMessages.SYNC_REQUEST_ID, PacketByteBufs.create());
    }

    @Override
    public void close() {
        savedScrollX = this.scrollX;
        savedScrollY = this.scrollY;
        savedScale = this.scale;
        super.close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (AscensionClient.openGuiKey.matchesKey(keyCode, scanCode)) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount > 0) this.scale = Math.min(this.scale + 0.1, 2.0);
        else if (amount < 0) this.scale = Math.max(this.scale - 0.1, 0.5);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        tickCounter++;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // === 关键：将鼠标屏幕坐标 -> 转换到缩放后的 UI 局部坐标 ===
        double localMouseX = (mouseX - centerX) / scale + centerX;
        double localMouseY = (mouseY - centerY) / scale + centerY;

        // 开始缩放变换
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale((float)scale, (float)scale, 1.0f);
        context.getMatrices().translate(-centerX, -centerY, 0);

        // 1. 渲染连线
        // 第一遍：绘制所有连线（白色/背景色）
        renderLinks(context, centerX, centerY, false);
        // 第二遍：只绘制已激活的连线（金色），覆盖在上面
        renderLinks(context, centerX, centerY, true);

        // 2. 渲染图标、悬浮框、文字
        Skill hoveredSkill = null;

        for (Skill skill : SkillRegistry.getAll()) {
            int currentLevel = getLevel(skill.id);
            boolean isRevealed = isRevealed(skill.id);
            if (skill.isHidden && !isRevealed && currentLevel == 0) continue;

            int drawX = (int)(centerX + skill.x + scrollX);
            int drawY = (int)(centerY + skill.y + scrollY);

            // 碰撞检测（使用局部坐标）
            boolean isHovered = (localMouseX >= drawX && localMouseX <= drawX + 16 &&
                    localMouseY >= drawY && localMouseY <= drawY + 16);

            if (isHovered) {
                hoveredSkill = skill;
                context.fill(drawX - 2, drawY - 2, drawX + 18, drawY + 18, 0x40FFFFFF);
            }

            // 画图标
            context.drawItem(skill.getIcon(), drawX, drawY);

            boolean isUnlocked = currentLevel > 0;
            boolean isMaxed = currentLevel >= skill.maxLevel;
            boolean isDisabled = isUnlocked && isSkillDisabled(skill.id); // 是否被停用

            // === 检查互斥锁定状态 ===
            boolean isMutexLocked = false;
            for (String mutexId : skill.mutexSkills) {
                if (getLevel(mutexId) > 0) {
                    isMutexLocked = true;
                    break;
                }
            }

            // === 如果已解锁但被停用，画一个半透明黑框盖住图标 ===
            if (isDisabled) {
                context.fill(drawX, drawY, drawX + 16, drawY + 16, 0xAA000000);
            }
            // === 如果互斥锁定，画一个半透明红框盖住图标 ===
            else if (isMutexLocked) {
                context.fill(drawX, drawY, drawX + 16, drawY + 16, 0x80FF0000);
            }

            // 状态边框
            if (isMaxed) {
                context.drawBorder(drawX - 1, drawY - 1, 18, 18, 0xFFFFD700);
            } else if (isUnlocked) {
                context.drawBorder(drawX - 1, drawY - 1, 18, 18, 0xFF00FF00);
            } else if (skill.isHidden && isRevealed) {
                int phase = (tickCounter / 2) % 30;
                int color = (phase < 10) ? 0xFFFF0000 : ((phase < 20) ? 0xFFFFD700 : 0xFFFFFFFF);
                context.drawBorder(drawX - 1, drawY - 1, 18, 18, color);
            }

            // 等级角标 (Z轴抬高)
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);

            if (isUnlocked) {
                String count = String.valueOf(currentLevel);
                context.drawText(this.textRenderer, count,
                        drawX + 17 - this.textRenderer.getWidth(count),
                        drawY + 17 - this.textRenderer.fontHeight,
                        0xFFFFFF, true);
            }

            // === 渲染技能名 (居中 + 背景条) ===
            Text name = skill.getName();
            float textScale = 0.7f;
            int textWidth = this.textRenderer.getWidth(name);

            // 坐标计算：移动到图标上方中心 (drawX + 8, drawY - 8)
            context.getMatrices().push();
            context.getMatrices().translate(drawX + 8, drawY - 8, 10); // Z=10 仅比图标高
            context.getMatrices().scale(textScale, textScale, 1.0f);

            // 此时 (0,0) 即为图标上方的中心点
            int drawTextX = -textWidth / 2;
            int drawTextY = 0;

            // 1. 绘制半透明背景条
            //context.fill(drawTextX - 2, drawTextY - 2, drawTextX + textWidth + 2, drawTextY + 9, 0x80000000);

            // 2. 绘制文字
            // 颜色逻辑：如果被停用，显示深灰色；互斥锁定显示红色；否则显示正常颜色
            int textColor = isMaxed ? 0xFFFFD700 : (isUnlocked ? 0xFFFFFFFF : 0xFFAAAAAA);
            if (isDisabled) {
                textColor = 0xFF555555; // 深灰
            } else if (isMutexLocked) {
                textColor = 0xFFFF5555; // 浅红
            }

            context.drawText(this.textRenderer, name, drawTextX, drawTextY, textColor, true);

            context.getMatrices().pop(); // 结束文字变换
            context.getMatrices().pop(); // 结束Z轴抬高
        }

        context.getMatrices().pop(); // 结束整体缩放变换

        // 3. 渲染 Tooltip
        if (hoveredSkill != null) {
            renderSkillTooltip(context, mouseX, mouseY, hoveredSkill);
        }

        // HUD
        renderHud(context,mouseX,mouseY);
    }

    // 辅助方法：绘制连线
    private void renderLinks(DrawContext context, int centerX, int centerY, boolean renderActiveOnly) {
        for (Skill skill : SkillRegistry.getAll()) {
            int currentLevel = getLevel(skill.id);
            boolean isRevealed = isRevealed(skill.id);
            if (skill.isHidden && !isRevealed && currentLevel == 0) continue;

            // 收集所有父节点 (visualParents 已经包含了 addParent 的内容)
            List<String> drawLineTo = new ArrayList<>();
            if (skill.parentId != null) drawLineTo.add(skill.parentId);
            drawLineTo.addAll(skill.visualParents);

            for (String pid : drawLineTo) {
                Skill parent = SkillRegistry.get(pid);
                if (parent == null) continue;

                boolean parentRevealed = isRevealed(parent.id);
                int parentLevel = getLevel(parent.id);
                if (parent.isHidden && !parentRevealed && parentLevel == 0) continue;

                int x1 = (int)(centerX + parent.x + scrollX) + 8;
                int y1 = (int)(centerY + parent.y + scrollY) + 8;
                int x2 = (int)(centerX + skill.x + scrollX) + 8;
                int y2 = (int)(centerY + skill.y + scrollY) + 8;

                boolean isLinked = currentLevel > 0 && parentLevel > 0;

                // 如果是“只画激活线”模式，但这条线未激活，则跳过
                if (renderActiveOnly && !isLinked) continue;

                // 如果是“画所有线”模式，且这条线是激活的，也跳过（留给第二遍画，或者画到底层也没关系，反正会被覆盖）
                // 为了性能和避免锯齿混合，我们通常第一遍画所有线(白色)，第二遍画金线覆盖

                int color = isLinked ? 0xFFFFD700 : 0xFFFFFFFF;

                // 只有当我们在第二遍循环(renderActiveOnly=true) 且 线是激活的，才画金色
                // 或者我们在第一遍循环(renderActiveOnly=false) 且 线是不激活的，才画白色
                // 这里采用简单策略：第一遍全画(白色)，第二遍只画金。
                if (!renderActiveOnly) {
                    // 第一遍：只画白色，忽略已经是金色的（避免重复绘制导致边缘锯齿，或者干脆全画白色打底）
                    color = 0xFFFFFFFF;
                } else {
                    // 第二遍：只画金色
                    if (!isLinked) continue;
                }

                if (x1 != x2) context.fill(Math.min(x1, x2), y1 - 1, Math.max(x1, x2), y1 + 1, color);
                if (y1 != y2) context.fill(x2 - 1, Math.min(y1, y2), x2 + 1, Math.max(y1, y2), color);
            }
        }
    }

    private void renderHud(DrawContext context,int mouseX,int mouseY){
        if (this.client != null && this.client.player != null) {
            int points = ((IEntityDataSaver) this.client.player).getPersistentData().getInt("skill_points");

            // 1. 定义位置参数
            int iconX = 10;
            int iconY = 10;
            int textX = iconX + 20; // 文字在图标右侧
            int textY = iconY + 4;  // 文字垂直居中

            // 2. 绘制图标 (知识之书) -> 代表技能点
            ItemStack bookIcon = new ItemStack(Items.KNOWLEDGE_BOOK);
            context.drawItem(bookIcon, iconX, iconY);

            // 3. 绘制文字 (使用翻译键)
            Text pointsText = Text.translatable("gui.ascension.points_remaining", points);
            context.drawText(this.textRenderer, pointsText, textX, textY, 0xFFFFFFFF, true);

            // 4. 悬浮检测区域 (包含图标和文字)
            int textWidth = this.textRenderer.getWidth(pointsText);
            // 检测范围：从图标左边到文字右边，高度为图标高度
            if (mouseX >= iconX && mouseX <= textX + textWidth && mouseY >= iconY && mouseY <= iconY + 16) {

                // 绘制高亮背景框，提示这里可以交互
                context.fill(iconX - 2, iconY - 2, textX + textWidth + 2, iconY + 18, 0x40FFFFFF);

                List<Text> helpTooltip = new ArrayList<>();
                helpTooltip.add(Text.translatable("gui.ascension.help_tip").formatted(Formatting.YELLOW, Formatting.BOLD));
                helpTooltip.add(Text.translatable("tooltip.ascension.help.collect").formatted(Formatting.GRAY));
                helpTooltip.add(Text.translatable("tooltip.ascension.help.food").formatted(Formatting.GRAY));
                helpTooltip.add(Text.translatable("tooltip.ascension.help.explore").formatted(Formatting.GRAY));
                helpTooltip.add(Text.translatable("tooltip.ascension.help.loot").formatted(Formatting.GRAY));
                helpTooltip.add(Text.translatable("tooltip.ascension.help.kill").formatted(Formatting.GRAY));
                helpTooltip.add(Text.translatable("tooltip.ascension.help.advancement").formatted(Formatting.GRAY));

                context.drawTooltip(this.textRenderer, helpTooltip, mouseX, mouseY);
            }

            // 底部操作提示
            Text hintText = Text.translatable("gui.ascension.controls_hint");
            context.drawText(this.textRenderer, hintText, 10, this.height - 20, 0xFFAAAAAA, true);
        }
    }

    private void renderSkillTooltip(DrawContext context, int mouseX, int mouseY, Skill skill) {
        int currentLevel = getLevel(skill.id);
        boolean isRevealed = isRevealed(skill.id);
        boolean isMaxed = currentLevel >= skill.maxLevel;
        boolean isUnlocked = currentLevel > 0;

        List<Text> tooltip = new ArrayList<>();
        Text desc = isMaxed ? skill.getDescription(Math.max(1, currentLevel)) : skill.getDescription(Math.max(1, currentLevel + 1));
        tooltip.add(skill.getName().copy().formatted(Formatting.YELLOW));
        tooltip.add(desc.copy().formatted(Formatting.GRAY));

        // 等级显示 (使用翻译键)
        tooltip.add(Text.translatable("gui.ascension.tooltip.level", currentLevel, skill.maxLevel).formatted(Formatting.GRAY));

        // === 启用状态提示 ===
        if (isUnlocked) {
            if (isSkillDisabled(skill.id)) {
                tooltip.add(Text.translatable("gui.ascension.tooltip.status.disabled").formatted(Formatting.RED)
                        .append(" ")
                        .append(Text.translatable("gui.ascension.tooltip.hint.enable").formatted(Formatting.GRAY)));
            } else {
                tooltip.add(Text.translatable("gui.ascension.tooltip.status.enabled").formatted(Formatting.GREEN)
                        .append(" ")
                        .append(Text.translatable("gui.ascension.tooltip.hint.disable").formatted(Formatting.GRAY)));
            }
        }

        // 隐藏技能提示
        if (skill.isHidden && isRevealed && currentLevel == 0) {
            tooltip.add(Text.translatable("gui.ascension.tooltip.hidden_revealed").formatted(Formatting.GOLD));
        }

        // === [新增] 互斥锁定提示 ===
        boolean isMutexLocked = false;
        for (String mutexId : skill.mutexSkills) {
            if (getLevel(mutexId) > 0) {
                isMutexLocked = true;
                break;
            }
        }
        if (isMutexLocked) {
            tooltip.add(Text.translatable("gui.ascension.tooltip.mutex_locked").formatted(Formatting.RED));
        }

        // 获取本地缓存数据
        IEntityDataSaver data = (IEntityDataSaver) this.client.player;
        boolean clientCriteriaMet = true;
        net.minecraft.nbt.NbtCompound cache = null;
        int[] progresses = null;

        if (data.getPersistentData().contains("criteria_cache")) {
            cache = data.getPersistentData().getCompound("criteria_cache");
        }
        if (data.getPersistentData().contains("criteria_progress")) {
            progresses = data.getPersistentData().getCompound("criteria_progress").getIntArray(skill.id);
        }

        int targetLevel = currentLevel + 1;
        List<com.qishui48.ascension.skill.UnlockCriterion> criteriaList = skill.getCriteria(targetLevel);

        if (!criteriaList.isEmpty()) {
            if (cache != null && cache.contains(skill.id)) {
                clientCriteriaMet = cache.getBoolean(skill.id);
            } else {
                clientCriteriaMet = false;
            }
        }

        if (!isMaxed) {
            int nextLevelCost = skill.getCost(targetLevel);
            // [修改] 升级消耗: 使用翻译键，并将数字设为青色(AQUA)
            tooltip.add(Text.translatable("gui.ascension.tooltip.cost",
                    Text.literal(String.valueOf(nextLevelCost)).formatted(Formatting.AQUA)).formatted(Formatting.GRAY));

            if (!criteriaList.isEmpty()) {
                tooltip.add(Text.of("")); // 空行
                if (clientCriteriaMet) {
                    // [修改] 条件达成
                    tooltip.add(Text.translatable("gui.ascension.tooltip.criteria.met").formatted(Formatting.GREEN));
                } else {
                    // [修改] 需要满足
                    tooltip.add(Text.translatable("gui.ascension.tooltip.criteria.required").formatted(Formatting.RED));

                    for (int i = 0; i < criteriaList.size(); i++) {
                        com.qishui48.ascension.skill.UnlockCriterion c = criteriaList.get(i);
                        int currentVal = (progresses != null && i < progresses.length) ? progresses[i] : 0;
                        String color = (currentVal >= c.getThreshold()) ? "§a" : "§7";

                        // [修改] 拼接条件详情：颜色 + " - " + 描述 + " (当前/目标)"
                        tooltip.add(Text.translatable("gui.ascension.tooltip.criterion_progress",
                                color,
                                c.getDescription(),
                                currentVal,
                                c.getThreshold()));
                    }
                }
            }

            boolean hasParent = (skill.parentId != null);
            boolean parentUnlocked = false;

            // === [新增] 多父节点解锁检查 ===
            List<String> allParents = new ArrayList<>();
            if (skill.parentId != null) allParents.add(skill.parentId);
            allParents.addAll(skill.visualParents);

            if (allParents.isEmpty()) {
                parentUnlocked = true; // 根节点
            } else {
                // 只要有一个父节点解锁了，就算前置达成
                for (String pid : allParents) {
                    if (getLevel(pid) > 0) {
                        parentUnlocked = true;
                        break;
                    }
                }
            }

            if (!parentUnlocked) {
                // [修改] 前置未解锁
                tooltip.add(Text.translatable("gui.ascension.tooltip.parent_locked").formatted(Formatting.RED));
            } else if (!criteriaList.isEmpty() && !clientCriteriaMet) {
                // [修改] 条件未满足
                tooltip.add(Text.translatable("gui.ascension.tooltip.criteria.failed").formatted(Formatting.RED));
            } else if (currentLevel > 0) {
                // [修改] 点击升级
                tooltip.add(Text.translatable("gui.ascension.tooltip.action.upgrade").formatted(Formatting.GREEN));
            } else {
                // [修改] 点击解锁
                tooltip.add(Text.translatable("gui.ascension.tooltip.action.unlock").formatted(Formatting.GREEN));
            }
        } else {
            // [修改] 已满级
            tooltip.add(Text.translatable("gui.ascension.tooltip.max_level").formatted(Formatting.GOLD));
        }
        context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        double localMouseX = (mouseX - centerX) / scale + centerX;
        double localMouseY = (mouseY - centerY) / scale + centerY;

        for (Skill skill : SkillRegistry.getAll()) {
            int currentLevel = getLevel(skill.id);
            boolean isRevealed = isRevealed(skill.id);
            if (skill.isHidden && !isRevealed && currentLevel == 0) continue;

            int drawX = (int)(centerX + skill.x + scrollX);
            int drawY = (int)(centerY + skill.y + scrollY);

            if (localMouseX >= drawX && localMouseX <= drawX + 16 &&
                    localMouseY >= drawY && localMouseY <= drawY + 16) {

                if (this.client != null) {
                    this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }

                // 左键 (0): 升级/解锁
                if (button == 0) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeString(skill.id);
                    ClientPlayNetworking.send(ModMessages.UNLOCK_REQUEST_ID, buf);
                    return true;
                }

                // === [新增] 中键 (2): 切换启用状态 ===
                if (button == 2) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeString(skill.id);
                    ClientPlayNetworking.send(ModMessages.TOGGLE_REQUEST_ID, buf);
                    return true;
                }
            }
        }
        if (button == 0) {
            this.isDragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDragging) {
            this.scrollX += deltaX / scale;
            this.scrollY += deltaY / scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private int getLevel(String id) {
        if (this.client == null || this.client.player == null) return 0;
        IEntityDataSaver data = (IEntityDataSaver) this.client.player;
        if (data.getPersistentData().contains("skill_levels")) {
            return data.getPersistentData().getCompound("skill_levels").getInt(id);
        }
        return 0;
    }

    private boolean isRevealed(String id) {
        if (this.client == null || this.client.player == null) return false;
        IEntityDataSaver data = (IEntityDataSaver) this.client.player;
        if (data.getPersistentData().contains("revealed_skills")) {
            return data.getPersistentData().getCompound("revealed_skills").getBoolean(id);
        }
        return false;
    }

    // === [新增] 辅助方法：检查本地是否停用 ===
    private boolean isSkillDisabled(String id) {
        if (this.client == null || this.client.player == null) return false;
        IEntityDataSaver data = (IEntityDataSaver) this.client.player;
        if (data.getPersistentData().contains("disabled_skills")) {
            return data.getPersistentData().getCompound("disabled_skills").getBoolean(id);
        }
        return false;
    }
}