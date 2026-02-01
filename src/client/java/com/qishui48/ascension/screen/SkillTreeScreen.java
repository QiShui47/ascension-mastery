package com.qishui48.ascension.screen;

import com.qishui48.ascension.AscensionClient;
import com.qishui48.ascension.network.ModMessages;
import com.qishui48.ascension.skill.ActiveSkill; // [新增] 导入 ActiveSkill
import com.qishui48.ascension.skill.Skill;
import com.qishui48.ascension.skill.SkillRegistry;
import com.qishui48.ascension.util.IEntityDataSaver;
import com.qishui48.ascension.skill.UnlockCriterion;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis; // [新增] 用于旋转

import java.util.ArrayList;
import java.util.List;

public class SkillTreeScreen extends Screen {
    // 假定的纹理路径，你可以替换为你自己的 active_skill_slot.png
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/gui/container/inventory.png");

    private static double savedScrollX = Double.NaN;
    private static double savedScrollY = Double.NaN;
    private static double savedScale = 1.0;

    private double scrollX = 0;
    private double scrollY = 0;
    private double scale = 1.0;
    private boolean isDraggingMap = false; // 重命名，区分地图拖拽和技能拖拽
    private int tickCounter = 0;

    // === 拖拽逻辑变量 ===
    private Skill draggingSkill = null; // 当前正在拖拽的主动技能

    // === 槽位布局常量 ===
    private static final int SLOT_SIZE = 26;
    private static final int SLOT_SPACING = 30;

    // 记录从哪个槽位开始拖拽 (-1 表示从树上)
    private int draggingSlotIndex = -1;

    public SkillTreeScreen() { super(Text.translatable("gui.ascension.title")); }

    @Override
    protected void init() {
        super.init();
        if (!Double.isNaN(savedScrollX)) {
            this.scrollX = savedScrollX;
            this.scrollY = savedScrollY;
            this.scale = savedScale;
        } else {
            this.scrollX = - this.width / 2.0;
            this.scrollY = (this.height * 0.15) - (this.height / 2.0);
            this.scale = 1.0;
        }

        // === 1. 每次打开 UI 时，强制请求同步 ===
        ClientPlayNetworking.send(ModMessages.SYNC_REQUEST_ID, PacketByteBufs.create());

        // ... (重置按钮代码保持不变) ...
        initResetButton();
    }

    private void initResetButton() {
        // === 重置按钮逻辑 (保持原样，为了代码整洁稍微提取了一下) ===
        int resetBtnWidth = 100;
        int resetBtnHeight = 20;
        int resetBtnX = 10;
        int resetBtnY = 35;

        // 1. 获取状态
        boolean canReset = true;
        List<Text> tooltip = new ArrayList<>();
        tooltip.add(Text.translatable("gui.ascension.reset.tooltip"));

        if (this.client != null && this.client.player != null) {
            IEntityDataSaver data = (IEntityDataSaver) this.client.player;
            NbtCompound nbt = data.getPersistentData();

            // 计算消耗
            int resetCount = nbt.contains("reset_count") ? nbt.getInt("reset_count") : 0;
            int cost = 1395 * (1 + resetCount);
            // 获取玩家当前经验总量 (估算或直接访问 totalExperience)
            int currentXp = this.client.player.totalExperience;

            // 检查冷却
            long lastResetTime = nbt.contains("last_reset_time") ? nbt.getLong("last_reset_time") : 0;
            long currentTime = this.client.world.getTime();
            long cooldownTicks = 240000L;

            if (lastResetTime != 0 && (currentTime - lastResetTime) < cooldownTicks) {
                canReset = false;
                long daysLeft = (cooldownTicks - (currentTime - lastResetTime)) / 24000L;
                tooltip.add(Text.translatable("message.ascension.reset_cooldown", daysLeft + 1).formatted(Formatting.RED));
            } else if (currentXp < cost) {
                canReset = false;
                tooltip.add(Text.translatable("message.ascension.not_enough_xp", cost).formatted(Formatting.RED));
            } else {
                tooltip.add(Text.translatable("gui.ascension.reset.cost", cost).formatted(Formatting.GRAY));
            }
        }

        // 2. 创建按钮
        var btn = net.minecraft.client.gui.widget.ButtonWidget.builder(
                        Text.translatable("gui.ascension.reset"),
                        (button) -> {
                            ClientPlayNetworking.send(ModMessages.RESET_SKILLS_ID, PacketByteBufs.empty());
                            this.close();
                        })
                .dimensions(resetBtnX, resetBtnY, resetBtnWidth, resetBtnHeight)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.empty().append(tooltip.get(0)).append("\n").append(tooltip.size() > 1 ? tooltip.get(1) : Text.empty())))
                .build();

        // 3. 设置启用状态 (Active = false 会自动变灰)
        btn.active = canReset;

        this.addDrawableChild(btn);
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

        // === 1. 渲染技能树 (应用缩放) ===
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale((float)scale, (float)scale, 1.0f);
        context.getMatrices().translate(-centerX, -centerY, 0);

        // 1. 渲染连线
        // 第一遍：绘制所有连线（白色/背景色）
        renderLinks(context, centerX, centerY, false);
        // 第二遍：只绘制已激活的连线（金色），覆盖在上面
        renderLinks(context, centerX, centerY, true);

        // 使用新的 drawNode 方法替代原有的大循环
        Skill hoveredSkillInTree = null;
        for (Skill skill : SkillRegistry.getAll()) {
            int currentLevel = getLevel(skill.id);
            boolean isRevealed = isRevealed(skill.id);
            if (skill.isHidden && !isRevealed && currentLevel == 0) continue;

            int drawX = (int)(centerX + skill.x + scrollX);
            int drawY = (int)(centerY + skill.y + scrollY);

            // 绘制节点 (核心修改点)
            drawNode(context, skill, drawX, drawY, localMouseX, localMouseY);

            // 简单的悬停检测用于记录，具体 Tooltip 逻辑在后面
            if (localMouseX >= drawX && localMouseX <= drawX + 26 &&
                    localMouseY >= drawY && localMouseY <= drawY + 26) {
                hoveredSkillInTree = skill;
            }
        }

        context.getMatrices().pop(); // 结束技能树缩放

        // === 2. 渲染 UI 覆盖层 (不随技能树缩放) ===

        // 渲染右下角的主动技能槽
        renderActiveSkillSlots(context, mouseX, mouseY);

        // 渲染 HUD (技能点数等)
        renderHud(context, mouseX, mouseY);

        // === 3. 渲染拖拽中的技能图标 (最顶层) ===
        if (draggingSkill != null) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300); // 极高的 Z 轴，确保在所有物体之上
            context.drawItem(draggingSkill.getIcon(), mouseX - 8, mouseY - 8); // 图标跟随鼠标
            context.getMatrices().pop();
        }

        // 先调用 super.render 绘制按钮等标准控件，这样可以确保按钮的状态操作（如 Scissor）都已经结束并复位，不会影响后续的 Tooltip
        super.render(context, mouseX, mouseY, delta);

        // === 4. 渲染 Tooltip (最后渲染以防被遮挡) ===
        // 优先渲染正在拖拽的槽位提示
        if (draggingSkill == null && hoveredSkillInTree != null) {
            renderSkillTooltip(context, mouseX, mouseY, hoveredSkillInTree);
        }
    }

    // === [核心] 绘制单个节点 (处理菱形/矩形逻辑) ===
    private void drawNode(DrawContext context, Skill skill, int x, int y, double localMouseX, double localMouseY) {
        boolean isActiveSkill = skill instanceof ActiveSkill;
        boolean isHovered = (localMouseX >= x && localMouseX <= x + 26 && localMouseY >= y && localMouseY <= y + 26);

        int currentLevel = getLevel(skill.id);
        boolean isUnlocked = currentLevel > 0;
        boolean isMaxed = currentLevel >= skill.maxLevel;
        boolean isDisabled = isUnlocked && isSkillDisabled(skill.id);

        // 检查互斥
        boolean isMutexLocked = false;
        for (String mutexId : skill.mutexSkills) {
            if (getLevel(mutexId) > 0) { isMutexLocked = true; break; }
        }

        // 1. 绘制背景 (形状)
        if (isActiveSkill) {
            // === 绘制菱形 ===
            context.getMatrices().push();
            // 移动到中心点 (假设节点 26x26，中心+13)
            context.getMatrices().translate(x + 13, y + 13, 0);
            context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45)); // 旋转45度
            context.getMatrices().translate(-13, -13, 0); // 回到左上角

            // 绘制旋转后的正方形 -> 视觉上的菱形
            // 边框颜色逻辑
            int color = 0xFFFFFFFF; // 默认白
            if (isMaxed) color = 0xFFFFD700; // 金
            else if (isUnlocked) color = 0xFF00FF00; // 绿
            else if (isMutexLocked) color = 0xFFFF0000; // 红

            // 绘制填充背景 (由于旋转了，使用 fill 也会是菱形)
            context.fill(0, 0, 26, 26, 0x80000000); // 半透明黑底
            context.drawBorder(0, 0, 26, 26, color); // 边框

            // 高亮
            if (isHovered) {
                context.fill(0, 0, 26, 26, 0x40FFFFFF);
            }

            context.getMatrices().pop();
        } else {
            // === 绘制普通矩形 ===
            int color = 0xFFFFFFFF;
            if (isMaxed) color = 0xFFFFD700;
            else if (isUnlocked) color = 0xFF00FF00;
            else if (isMutexLocked) color = 0xFFFF0000;

            context.fill(x, y, x + 26, y + 26, 0x80000000);
            context.drawBorder(x, y, 26, 26, color);

            if (isHovered) {
                context.fill(x, y, x + 26, y + 26, 0x40FFFFFF);
            }
        }

        // 2. 绘制图标 (始终正向)
        context.drawItem(skill.getIcon(), x + 5, y + 5);

        // 3. 绘制遮罩 (停用/互斥)
        if (isDisabled) {
            context.fill(x, y, x + 26, y + 26, 0xAA000000); // 黑色遮罩
        } else if (isMutexLocked) {
            context.fill(x, y, x + 26, y + 26, 0x80FF0000); // 红色遮罩
        }

        // 4. 等级角标 (Z轴抬高)
        if (isUnlocked) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            String count = String.valueOf(currentLevel);
            context.drawText(this.textRenderer, count,
                    x + 24 - this.textRenderer.getWidth(count),
                    y + 24 - this.textRenderer.fontHeight,
                    0xFFFFFF, true);
            context.getMatrices().pop();
        }

        // 5. 技能名称 (带背景)
        Text name = skill.getName();
        float textScale = 0.7f;
        int textWidth = this.textRenderer.getWidth(name);

        context.getMatrices().push();
        context.getMatrices().translate(x + 13, y - 8, 10);
        context.getMatrices().scale(textScale, textScale, 1.0f);

        int drawTextX = -textWidth / 2;
        int textColor = isMaxed ? 0xFFFFD700 : (isUnlocked ? 0xFFFFFFFF : 0xFFAAAAAA);
        if (isDisabled) textColor = 0xFF555555;

        context.drawText(this.textRenderer, name, drawTextX, 0, textColor, true);
        context.getMatrices().pop();
    }

    private void renderActiveSkillSlots(DrawContext context, int mouseX, int mouseY) {
        int startX = this.width - 20 - (5 * SLOT_SPACING);
        int startY = this.height - 40;

        // 标题
        context.drawText(this.textRenderer, Text.translatable("gui.ascension.active_slots"), startX, startY - 24, 0xFFDDDDDD, true);

        // === 帮助图标/文字 ===
        // 在标题右侧画一个小的 (?)
        int helpX = startX - 24; // 根据实际宽度调整
        int helpY = startY - 23;
        Text helpIcon = Text.literal("[?]");
        context.drawText(this.textRenderer, helpIcon, helpX, helpY, 0xFFFFFF55, true);

        // 检测鼠标是否悬浮在 [?] 上
        if (mouseX >= helpX && mouseX <= helpX + 10 && mouseY >= helpY && mouseY <= helpY + 8) {
            List<net.minecraft.text.OrderedText> helpTooltip = new ArrayList<>();

            // 1. 对于不需要换行的普通行，使用 .asOrderedText()
            helpTooltip.add(Text.translatable("gui.ascension.help.active_skill.title").formatted(Formatting.GOLD).asOrderedText());
            helpTooltip.add(Text.translatable("gui.ascension.help.active_skill.drag").formatted(Formatting.GRAY).asOrderedText());

            // 2. 对于包含 \n 的长文本，使用 wrapLines 自动切分
            // 200 是最大宽度（像素），如果文本没超过这个宽度但有 \n，它也会在 \n 处换行
            Text keysText = Text.translatable("gui.ascension.help.active_skill.keys").formatted(Formatting.GRAY);
            helpTooltip.addAll(this.textRenderer.wrapLines(keysText, 200));

            // 3. 使用 drawOrderedTooltip 绘制 (注意方法名变了)
            context.drawOrderedTooltip(this.textRenderer, helpTooltip, mouseX, mouseY);
        }

        IEntityDataSaver data = (IEntityDataSaver) this.client.player;
        NbtList activeSlots = null;
        if (data.getPersistentData().contains("active_skill_slots", NbtElement.LIST_TYPE)) {
            activeSlots = data.getPersistentData().getList("active_skill_slots", NbtElement.COMPOUND_TYPE);
        }

        long currentTime = this.client.world.getTime();

        for (int i = 0; i < 5; i++) {
            int x = startX + (i * SLOT_SPACING);
            int y = startY;

            // 1. 绘制底座
            context.getMatrices().push();
            context.getMatrices().translate(x + 13, y + 13, 0);
            context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
            context.getMatrices().translate(-13, -13, 0);

            context.fill(0, 0, 26, 26, 0x80000000);
            boolean isHovered = (mouseX >= x && mouseX <= x + 26 && mouseY >= y && mouseY <= y + 26);
            int selectedSlot = data.getPersistentData().getInt("selected_active_slot");
            int borderColor = (i == selectedSlot) ? 0xFFFFFFFF : 0xFF555555;
            if (isHovered) borderColor = 0xFFFFFFAA;

            context.drawBorder(0, 0, 26, 26, borderColor);
            context.getMatrices().pop();

            // 2. 绘制技能内容
            if (activeSlots != null && i < activeSlots.size()) {
                NbtCompound slotNbt = activeSlots.getCompound(i);
                String skillId = slotNbt.getString("id");

                if (!skillId.isEmpty()) {
                    Skill skill = SkillRegistry.get(skillId); // 定义 skill

                    if (skill != null) {
                        // A. 绘制图标
                        context.getMatrices().push();
                        context.getMatrices().translate(x + 13, y + 13, 0);
                        context.getMatrices().scale(1.3f, 1.3f, 1f);
                        context.getMatrices().translate(-8, -8, 0);
                        context.drawItem(skill.getIcon(), 0, 0);
                        context.getMatrices().pop();

                        // B. 冷却遮罩
                        long endTime = slotNbt.getLong("cooldown_end");
                        int totalTime = slotNbt.getInt("cooldown_total");

                        if (currentTime < endTime && totalTime > 0) {
                            float progress = (float)(endTime - currentTime) / totalTime;
                            int cx = x + 13;
                            int cy = y + 13;
                            int halfDiag = 19;
                            int maskHeight = (int)(38 * progress);
                            int waterLevelY = (cy + 19) - maskHeight;

                            context.enableScissor(cx - halfDiag, waterLevelY, cx + halfDiag, cy + 19);
                            context.getMatrices().push();
                            context.getMatrices().translate(cx, cy, 10);
                            context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
                            context.getMatrices().translate(-13, -13, 0);
                            context.fill(0, 0, 26, 26, 0x60FFFFFF);
                            context.getMatrices().pop();
                            context.disableScissor();
                        }

                        // C. 充能数字
                        int charges = slotNbt.getInt("charges");
                        if (charges >= 0) {
                            String countText = String.valueOf(charges);
                            int textColor = (charges > 0) ? 0xFFFFFFFF : 0xFFFF5555;
                            context.getMatrices().push();
                            context.getMatrices().translate(0, 0, 20);
                            context.drawText(this.textRenderer, countText, x + 20, y + 18, textColor, true);
                            context.getMatrices().pop();
                        }

                        // === D. Tooltip (移动到了 skill != null 的作用域内) ===
                        if (isHovered && draggingSkill == null) {
                            context.drawTooltip(this.textRenderer, skill.getName(), mouseX, mouseY);
                        }
                    }
                }
            }

            // 索引数字
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 5);
            context.drawText(this.textRenderer, String.valueOf(i+1), x + 10, y + 24, 0xFFAAAAAA, true);
            context.getMatrices().pop();
        }
    }

    // 定义变量 (添加到类成员变量区)
    private double clickStartX, clickStartY;
    private boolean isPotentialClick = false; // 是否可能是点击事件

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. 优先处理重置按钮等原生组件
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0) {
            this.clickStartX = mouseX;
            this.clickStartY = mouseY;
            this.isPotentialClick = true;

            // A. 检测是否点击了【技能槽】(用于拖出/卸载)
            int startX = this.width - 20 - (5 * SLOT_SPACING);
            int startY = this.height - 40;
            for (int i = 0; i < 5; i++) {
                int x = startX + (i * SLOT_SPACING);
                if (mouseX >= x && mouseX <= x + 26 && mouseY >= startY && mouseY <= startY + 26) {
                    // 获取该槽位的技能
                    IEntityDataSaver data = (IEntityDataSaver) this.client.player;
                    NbtList activeSlots = data.getPersistentData().getList("active_skill_slots", NbtElement.COMPOUND_TYPE);
                    if (i < activeSlots.size()) {
                        String skillId = activeSlots.getCompound(i).getString("id");
                        if (!skillId.isEmpty()) {
                            // 开始拖拽：记录来源槽位
                            this.draggingSkill = SkillRegistry.get(skillId);
                            this.draggingSlotIndex = i; // [重点] 标记来源是槽位 i
                            this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            return true;
                        }
                    }
                }
            }
            // B. 检测是否点击了【技能树节点】(原有逻辑)
            Skill skillInTree = getSkillAt(mouseX, mouseY);
            if (skillInTree != null) {
                // ... 检测解锁状态等 ...
                int currentLevel = getLevel(skillInTree.id);
                if (skillInTree instanceof ActiveSkill && currentLevel > 0 && !isSkillDisabled(skillInTree.id)) {
                    this.draggingSkill = skillInTree;
                    this.draggingSlotIndex = -1; // [重点] 标记来源是技能树
                    return true;
                }
            }
            // C. 拖拽地图
            this.isDraggingMap = true;
            return true;
        }

        // 中键 (2): 切换启用状态
        if (button == 2) {
            Skill targetSkill = getSkillAt(mouseX, mouseY);
            if (targetSkill != null) {
                // 只有非主动技能才允许切换
                if (targetSkill instanceof ActiveSkill) {
                    return false; // 忽略主动技能的中键
                }
                // 播放音效
                this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));

                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeString(targetSkill.id);
                ClientPlayNetworking.send(ModMessages.TOGGLE_REQUEST_ID, buf);
                return true;
            }
            // 注意：这里也可以添加逻辑：中键点击 ActiveSlot 也可以 Toggle
            // ...
            return false;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0) {
            // 计算总移动距离
            double dist = Math.sqrt(Math.pow(mouseX - clickStartX, 2) + Math.pow(mouseY - clickStartY, 2));

            // === 阈值判定 (比如移动超过 3 像素算拖拽) ===
            if (dist > 3) {
                this.isPotentialClick = false; // 移动了，不再算作“点击”
            }

            if (this.draggingSkill != null) {
                // 正在拖拽技能图标，消耗事件，不拖拽地图
                return true;
            }

            if (this.isDraggingMap) {
                this.scrollX += deltaX / scale;
                this.scrollY += deltaY / scale;
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingSkill != null) {
            double dist = Math.sqrt(Math.pow(mouseX - clickStartX, 2) + Math.pow(mouseY - clickStartY, 2));
            boolean isDragAction = dist > 3;

            // 检测是否放置在槽位上
            int startX = this.width - 20 - (5 * SLOT_SPACING);
            int startY = this.height - 40;
            int dropSlotIndex = -1;

            for (int i = 0; i < 5; i++) {
                int x = startX + (i * SLOT_SPACING);
                if (mouseX >= x && mouseX <= x + 26 && mouseY >= startY && mouseY <= startY + 26) {
                    dropSlotIndex = i;
                    break;
                }
            }

            // === 逻辑分支 ===

            // 情况 1: 从技能树 -> 拖到槽位 (装备)
            if (draggingSlotIndex == -1) {
                if (isDragAction && dropSlotIndex != -1) {
                    // 发送装备包
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeString(draggingSkill.id);
                    buf.writeInt(dropSlotIndex);
                    ClientPlayNetworking.send(ModMessages.EQUIP_REQUEST_ID, buf);
                    this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, 1.0F));
                } else if (!isDragAction) {
                    // 点击升级
                    handleUnlockClick(draggingSkill);
                }
            }
            // 情况 2: 从槽位 -> 拖到外面 (卸载)
            else { // draggingSlotIndex != -1
                if (isDragAction) {
                    if (dropSlotIndex == -1) {
                        // 拖到了空地 -> 卸载
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeInt(draggingSlotIndex);
                        ClientPlayNetworking.send(ModMessages.UNEQUIP_REQUEST_ID, buf);
                        this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 1.0F));
                    } else if (dropSlotIndex != draggingSlotIndex) {
                        // 拖到了另一个槽位 -> (可选：交换位置，目前简单处理为移动/装备)
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeString(draggingSkill.id);
                        buf.writeInt(dropSlotIndex);
                        ClientPlayNetworking.send(ModMessages.EQUIP_REQUEST_ID, buf);

                        // 同时清空旧槽位?
                        // 目前 EQUIP_REQUEST 会覆盖目标，但不会清空源。
                        // 如果需要完美的“移动”效果，建议让服务端 EQUIP 逻辑检测该技能是否已在其他槽，如果在则互换或清空。
                        // (ModMessages 已有查重逻辑，会阻止装备，所以这里实际上会操作失败并提示“已装备”，符合预期)
                    }
                }
            }

            this.draggingSkill = null;
            this.draggingSlotIndex = -1;
            return true;
        }

        if (button == 0 && isPotentialClick) {
            Skill clickedSkill = getSkillAt(mouseX, mouseY);
            if (clickedSkill != null) {
                handleUnlockClick(clickedSkill);
            }
        }

        this.isDraggingMap = false;
        this.isPotentialClick = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // 辅助方法：处理升级请求
    private void handleUnlockClick(Skill skill) {
        this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(skill.id);
        ClientPlayNetworking.send(ModMessages.UNLOCK_REQUEST_ID, buf);
    }

    // 辅助方法：获取鼠标下的技能
    private Skill getSkillAt(double mouseX, double mouseY) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        double localMouseX = (mouseX - centerX) / scale + centerX;
        double localMouseY = (mouseY - centerY) / scale + centerY;

        for (Skill skill : SkillRegistry.getAll()) {
            int currentLevel = getLevel(skill.id);
            if (skill.isHidden && !isRevealed(skill.id) && currentLevel == 0) continue;
            int drawX = (int)(centerX + skill.x + scrollX);
            int drawY = (int)(centerY + skill.y + scrollY);
            if (localMouseX >= drawX && localMouseX <= drawX + 26 &&
                    localMouseY >= drawY && localMouseY <= drawY + 26) {
                return skill;
            }
        }
        return null;
    }

    // 连线渲染
    private void renderLinks(DrawContext context, int centerX, int centerY, boolean renderActiveOnly) {
        final int LINE_WIDTH = 2;

        // 基础半径 (矩形)
        final double RADIUS_SQUARE = 13.0;
        // 菱形半径 (13 * sqrt(2) ≈ 18.38)，取 18.0 以确保稍微接触不留缝隙
        final double RADIUS_DIAMOND = 18.0;

        for (Skill skill : SkillRegistry.getAll()) {
            int currentLevel = getLevel(skill.id);
            boolean isRevealed = isRevealed(skill.id);
            if (skill.isHidden && !isRevealed && currentLevel == 0) continue;

            List<String> parents = new ArrayList<>();
            if (skill.parentId != null) parents.add(skill.parentId);
            parents.addAll(skill.visualParents);

            for (String pid : parents) {
                Skill parent = SkillRegistry.get(pid);
                if (parent == null) continue;

                boolean parentRevealed = isRevealed(parent.id);
                int parentLevel = getLevel(parent.id);
                if (parent.isHidden && !parentRevealed && parentLevel == 0) continue;

                // 计算中心坐标
                double x1 = (centerX + parent.x + scrollX) + 13;
                double y1 = (centerY + parent.y + scrollY) + 13;
                double x2 = (centerX + skill.x + scrollX) + 13;
                double y2 = (centerY + skill.y + scrollY) + 13;

                boolean isLinked = currentLevel > 0 && parentLevel > 0;

                if (renderActiveOnly && !isLinked) continue;
                int color = (!renderActiveOnly) ? 0xFFFFFFFF : 0xFFFFD700;
                if (renderActiveOnly && !isLinked) continue;

                // === 关键修改：根据技能类型决定避让半径 ===
                double offsetStart = (parent instanceof ActiveSkill) ? RADIUS_DIAMOND : RADIUS_SQUARE;
                double offsetEnd = (skill instanceof ActiveSkill) ? RADIUS_DIAMOND : RADIUS_SQUARE;

                // === 绘制 L 型连线 ===
                double dx = x2 - x1;
                double dy = y2 - y1;

                // 1. 水平段: 从起点横向走到终点的 X 轴
                if (Math.abs(dx) > 0.5) {
                    double sx = x1;
                    double ex = x2;
                    double dirX = Math.signum(dx);

                    // 起点避让 (Parent)
                    sx += dirX * offsetStart;

                    // 特殊情况：如果是纯水平直线 (没有垂直段)，终点也要避让 (Skill)
                    if (Math.abs(dy) < 0.5) {
                        ex -= dirX * offsetEnd;
                    }

                    // 绘制 (保证 start < end)
                    if ((dirX > 0 && sx < ex) || (dirX < 0 && sx > ex)) {
                        fillLine(context, sx, y1, ex, y1, LINE_WIDTH, color);
                    }
                }

                // 2. 垂直段: 从拐点纵向走到终点的 Y 轴
                if (Math.abs(dy) > 0.5) {
                    double sy = y1;
                    double ey = y2;
                    double dirY = Math.signum(dy);

                    // 终点避让 (Skill)
                    ey -= dirY * offsetEnd;

                    // 特殊情况：如果是纯垂直直线 (没有水平段)，起点也要避让 (Parent)
                    if (Math.abs(dx) < 0.5) {
                        sy += dirY * offsetStart;
                    }

                    // 绘制
                    if ((dirY > 0 && sy < ey) || (dirY < 0 && sy > ey)) {
                        fillLine(context, x2, sy, x2, ey, LINE_WIDTH, color);
                    }
                }
            }
        }
    }

    // 辅助方法：绘制水平或垂直的矩形线段 (保持不变)
    private void fillLine(DrawContext context, double x1, double y1, double x2, double y2, int width, int color) {
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);

        double halfWidth = width / 2.0;

        if (Math.abs(y1 - y2) < 0.1) {
            // 横线
            context.fill((int)minX, (int)(y1 - halfWidth), (int)maxX + 1, (int)(y1 + halfWidth) + 1, color);
        } else {
            // 竖线
            context.fill((int)(x1 - halfWidth), (int)minY, (int)(x1 + halfWidth) + 1, (int)maxY + 1, color);
        }
    }

    // 辅助获取父节点坐标
    private int getParentX(Skill skill) {
        if(skill.parentId == null) return 0;
        Skill p = SkillRegistry.get(skill.parentId);
        return p != null ? p.x : 0;
    }
    private int getParentY(Skill skill) {
        if(skill.parentId == null) return 0;
        Skill p = SkillRegistry.get(skill.parentId);
        return p != null ? p.y : 0;
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
        List<OrderedText> tooltip = new ArrayList<>();
        int level = getLevel(skill.id);
        boolean isUnlocked = level > 0;
        boolean isMaxed = level >= skill.maxLevel;

        // 1. 技能名
        tooltip.add(skill.getName().copy().formatted(Formatting.BOLD, isUnlocked ? Formatting.YELLOW : Formatting.AQUA).asOrderedText());

        // 2. 启用状态
        if (isUnlocked) {
            boolean isDisabled = isSkillDisabled(skill.id);
            if (isDisabled) {
                tooltip.add(Text.translatable("gui.ascension.tooltip.status.disabled").formatted(Formatting.RED).asOrderedText());
                tooltip.add(Text.translatable("gui.ascension.tooltip.hint.enable").formatted(Formatting.DARK_GRAY, Formatting.ITALIC).asOrderedText());
            } else {
                tooltip.add(Text.translatable("gui.ascension.tooltip.status.enabled").formatted(Formatting.GREEN).asOrderedText());
            }
        }

        // 3. 等级
        tooltip.add(Text.translatable("gui.ascension.tooltip.level", level, skill.maxLevel).formatted(Formatting.GRAY).asOrderedText());

        tooltip.add(OrderedText.EMPTY);

        // 4. 背景故事 (Flavor Text)
        addMultiLineText(tooltip, "skill.ascension." + skill.id + ".desc", Formatting.GRAY);

        // 5. 本级效果 (Current Absolute Effect)
        if (isUnlocked) {
            tooltip.add(OrderedText.EMPTY);

            // 读取绝对描述: skill.id.desc.X
            String key = "skill.ascension." + skill.id + ".desc." + level;
            addMultiLineText(tooltip, key, Formatting.WHITE);
        }

        // 6. 主动技能参数 (自动生成的数值面板)
        if (skill instanceof ActiveSkill activeSkill) {
            tooltip.add(OrderedText.EMPTY);
            int displayLevel = isUnlocked ? level : 1;

            int maxCharges = activeSkill.getMaxCharges(displayLevel);
            double cooldownSeconds = activeSkill.getCooldown(displayLevel) / 20.0;
            String cdText = (cooldownSeconds % 1 == 0) ? String.format("%.0f", cooldownSeconds) : String.format("%.1f", cooldownSeconds);

            // 充能
            tooltip.add(Text.translatable("gui.ascension.tooltip.charges", maxCharges)
                    .formatted(Formatting.LIGHT_PURPLE).asOrderedText());
            // 冷却
            tooltip.add(Text.translatable("gui.ascension.tooltip.cooldown", cdText)
                    .formatted(Formatting.LIGHT_PURPLE).asOrderedText());
        }

        // 7. 升级预览 (Upgrade Preview)
        if (!isMaxed) {
            tooltip.add(OrderedText.EMPTY);
            String headerKey = isUnlocked ? "gui.ascension.tooltip.upgrade_header" : "gui.ascension.tooltip.level1_header";
            tooltip.add(Text.translatable(headerKey).formatted(Formatting.DARK_GREEN).asOrderedText());

            int nextLevel = level + 1;

            // 优先查找增量描述 (upgrade.X)，如果没有则显示绝对描述 (desc.X)
            String upgradeKey = "skill.ascension." + skill.id + ".upgrade." + nextLevel;
            String absoluteKey = "skill.ascension." + skill.id + ".desc." + nextLevel;

            if (I18n.hasTranslation(upgradeKey)) {
                addMultiLineText(tooltip, upgradeKey, Formatting.GRAY);
            } else {
                addMultiLineText(tooltip, absoluteKey, Formatting.GRAY);
            }

            // 8. 消耗与条件
            tooltip.add(OrderedText.EMPTY);
            int cost = skill.getCost(nextLevel);
            tooltip.add(Text.translatable("gui.ascension.tooltip.cost", cost).formatted(Formatting.BLUE).asOrderedText());
            renderCriteriaTooltip(tooltip, skill, nextLevel);
        } else {
            tooltip.add(OrderedText.EMPTY);
            tooltip.add(Text.translatable("gui.ascension.tooltip.max_level").formatted(Formatting.GOLD, Formatting.BOLD).asOrderedText());
        }

        // ... (互斥警告等代码保持不变) ...
        if (!skill.mutexSkills.isEmpty()) {
            boolean isMutexLocked = false;
            for (String mutexId : skill.mutexSkills) {
                if (getLevel(mutexId) > 0) { isMutexLocked = true; break; }
            }
            if (isMutexLocked) {
                tooltip.add(Text.translatable("gui.ascension.tooltip.mutex_locked").formatted(Formatting.RED).asOrderedText());
            }
        }

        // 隐藏技能提示
        if (skill.isHidden && isRevealed(skill.id) && level == 0) {
            tooltip.add(Text.translatable("gui.ascension.tooltip.hidden_revealed").formatted(Formatting.GOLD).asOrderedText());
        }

        context.drawOrderedTooltip(this.textRenderer, tooltip, mouseX, mouseY);
    }

    // === 辅助方法：支持 \n 换行并自动折行 ===
    private void addMultiLineText(List<OrderedText> tooltip, String translationKey, Formatting formatting) {
        if (!I18n.hasTranslation(translationKey)) return;

        String rawText = I18n.translate(translationKey);
        // 如果翻译内容为空字符串，也直接跳过
        if (rawText.isEmpty()) return;
        // 支持 "\n" (JSON中的换行) 或 "\\n" (转义换行)
        String[] lines = rawText.split("\n|\\\\n");

        for (String line : lines) {
            // 对每一行进行宽度限制折行 (160像素)
            tooltip.addAll(this.textRenderer.wrapLines(Text.literal(line).formatted(formatting), 220));
        }
    }

    // 复用原有的条件渲染逻辑，适配 List<OrderedText>
    private void renderCriteriaTooltip(List<OrderedText> tooltip, Skill skill, int targetLevel) {
        IEntityDataSaver data = (IEntityDataSaver) this.client.player;
        int[] progresses = null;
        boolean clientCriteriaMet = false;

        if (data.getPersistentData().contains("criteria_cache")) {
            clientCriteriaMet = data.getPersistentData().getCompound("criteria_cache").getBoolean(skill.id);
        }
        if (data.getPersistentData().contains("criteria_progress")) {
            progresses = data.getPersistentData().getCompound("criteria_progress").getIntArray(skill.id);
        }

        List<UnlockCriterion> criteriaList = skill.getCriteria(targetLevel);
        List<List<UnlockCriterion>> groups = skill.getCriteriaGroups(targetLevel);

        if (criteriaList.isEmpty()) return;

        // 如果全部达成
        if (clientCriteriaMet) {
            tooltip.add(Text.translatable("gui.ascension.tooltip.criteria.met").formatted(Formatting.GREEN).asOrderedText());
            return;
        }

        // 分组渲染
        for (int i = 0; i < groups.size(); i++) {
            List<UnlockCriterion> group = groups.get(i);

            if (i > 0) {
                tooltip.add(Text.translatable("gui.ascension.tooltip.criteria.or").formatted(Formatting.GOLD).asOrderedText());
            }

            if (group.size() > 1) {
                tooltip.add(Text.translatable("gui.ascension.tooltip.criteria.required_all").formatted(Formatting.RED).asOrderedText());
            } else if (groups.size() == 1) {
                tooltip.add(Text.translatable("gui.ascension.tooltip.criteria.required").formatted(Formatting.RED).asOrderedText());
            }

            for (UnlockCriterion c : group) {
                int flatIndex = criteriaList.indexOf(c);
                int rawVal = (progresses != null && flatIndex >= 0 && flatIndex < progresses.length) ? progresses[flatIndex] : 0;
                int displayVal = (int) (rawVal / c.getDisplayDivisor());
                int targetVal = (int) (c.getThreshold() / c.getDisplayDivisor());

                String color = (rawVal >= c.getThreshold()) ? "§a" : "§7";
                tooltip.add(Text.translatable("gui.ascension.tooltip.criterion_progress", color, c.getDescription(), displayVal, targetVal).asOrderedText());
            }
        }

        // 前置未解锁提示
        if (targetLevel == 1) {
            boolean parentUnlocked = (skill.parentId == null);
            if(!parentUnlocked) parentUnlocked = (getLevel(skill.parentId) > 0);
            if(!parentUnlocked && !skill.visualParents.isEmpty()) {
                for(String pid : skill.visualParents) {
                    if(getLevel(pid) > 0) { parentUnlocked = true; break; }
                }
            }
            if (!parentUnlocked) {
                tooltip.add(Text.translatable("gui.ascension.tooltip.parent_locked").formatted(Formatting.RED).asOrderedText());
            }
        }
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

    private boolean isSkillDisabled(String id) {
        if (this.client == null || this.client.player == null) return false;
        IEntityDataSaver data = (IEntityDataSaver) this.client.player;
        if (data.getPersistentData().contains("disabled_skills")) {
            return data.getPersistentData().getCompound("disabled_skills").getBoolean(id);
        }
        return false;
    }
}