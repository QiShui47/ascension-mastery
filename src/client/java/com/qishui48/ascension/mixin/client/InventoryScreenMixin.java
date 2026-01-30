package com.qishui48.ascension.mixin.client;

import com.qishui48.ascension.network.ModMessages;
import com.qishui48.ascension.util.IEntityDataSaver;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookProvider;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractInventoryScreen<PlayerScreenHandler> implements RecipeBookProvider {

    public InventoryScreenMixin(PlayerScreenHandler screenHandler, PlayerInventory playerInventory, Text text) {
        super(screenHandler, playerInventory, text);
    }

    // 绘制格子
    @Inject(method = "render", at = @At("TAIL"))
    public void renderMaterials(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int startX = this.x + 179;
        int startY = this.y + 7;

        IEntityDataSaver data = (IEntityDataSaver) this.client.player;
        NbtList materials = data.getPersistentData().getList("casting_materials", NbtElement.COMPOUND_TYPE);

        for (int i = 0; i < 5; i++) {
            int x = startX;
            int y = startY + (i * 20);

            // 1. 绘制格子背景
            context.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
            context.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);

            if (mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 18) {
                context.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
            }
            ItemStack stack = ItemStack.EMPTY;

            // 2. 获取并绘制物品
            if (i < materials.size()) {
                stack = ItemStack.fromNbt(materials.getCompound(i));
                if (!stack.isEmpty()) {
                    context.drawItem(stack, x + 1, y + 1);
                    context.drawItemInSlot(this.textRenderer, stack, x + 1, y + 1);
                }
            }

            // 3. 绘制 Tooltip
            if (mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 18) {
                List<Text> tooltip = new ArrayList<>();

                // 使用上面定义好的 stack
                if (!stack.isEmpty()) {
                    tooltip.addAll(this.getTooltipFromItem(stack));
                } else {
                    tooltip.add(Text.translatable("gui.ascension.material_slot", i + 1));
                }

                tooltip.add(Text.empty());
                tooltip.add(Text.translatable("gui.ascension.material_help.priority").formatted(Formatting.YELLOW));
                tooltip.add(Text.translatable("gui.ascension.material_help.reduce_cd").formatted(Formatting.AQUA));

                // 安全地获取 TooltipData
                context.drawTooltip(this.textRenderer, tooltip, stack.isEmpty() ? java.util.Optional.empty() : stack.getTooltipData(), mouseX, mouseY);
            }
        }
    }

    // 处理点击：放入/取出物品
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void onMaterialSlotClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        int startX = this.x + 179;
        int startY = this.y + 7;

        for (int i = 0; i < 5; i++) {
            int x = startX;
            int y = startY + (i * 20);

            if (mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 18) {
                // 左键 (0): 取出/放入
                if (button == 0) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeInt(i);
                    ClientPlayNetworking.send(ModMessages.MATERIAL_SLOT_CLICK_ID, buf);
                    this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    cir.setReturnValue(true);
                    return;
                }

                // === 右键 (1): 消耗材料减少冷却 ===
                if (button == 1) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeInt(i);
                    ClientPlayNetworking.send(ModMessages.REDUCE_CD_REQUEST_ID, buf); // 发送新包
                    this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 1.0F));
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
    // === 拦截鼠标松开，防止原版逻辑干扰施法材料区的拖拽动作 ===
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    public void onMaterialSlotRelease(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        int startX = this.x + 179;
        int startY = this.y + 7;

        for (int i = 0; i < 5; i++) {
            int x = startX;
            int y = startY + (i * 20);

            // 如果是在我们的槽位上松开鼠标，直接消耗掉事件，不让原版处理
            if (mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 18) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}