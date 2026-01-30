package com.qishui48.ascension.mixin.mechanics;

import com.qishui48.ascension.util.IEntityDataSaver;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// @Mixin 告诉游戏：我们要修改 Entity 这个原版类
@Mixin(Entity.class)
public abstract class ModEntityDataMixin implements IEntityDataSaver {

    // 注入一个新的成员变量，相当于给 Entity 结构体加了一个字段
    private NbtCompound persistentData;

    // 实现接口方法：让外部能获取这个数据包
    @Override
    public NbtCompound getPersistentData() {
        if(this.persistentData == null) {
            this.persistentData = new NbtCompound();
        }
        return this.persistentData;
    }

    // 劫持 writeNbt 函数（保存游戏时触发）
    // At("HEAD") 表示在函数执行的一开始就插入我们的代码
    @Inject(method = "writeNbt", at = @At("HEAD"))
    protected void injectWriteMethod(NbtCompound nbt, CallbackInfoReturnable info) {
        if(persistentData != null) {
            // 把我们的自定义数据打包，塞进一个叫 "ascension.data" 的标签里
            nbt.put("ascension.data", persistentData);
        }
    }

    // 劫持 readNbt 函数（加载游戏时触发）
    @Inject(method = "readNbt", at = @At("HEAD"))
    protected void injectReadMethod(NbtCompound nbt, CallbackInfo info) {
        // 如果存档里有我们的数据，就读出来
        if (nbt.contains("ascension.data", 10)) {
            persistentData = nbt.getCompound("ascension.data");
        }
        // 数据初始化检查
        // 确保施法材料区 (casting_materials) 和 技能槽 (active_skill_slots) 列表存在
        if (persistentData != null) {
            if (!persistentData.contains("casting_materials", 9)) { // 9 = List
                persistentData.put("casting_materials", new NbtList());
            }
            if (!persistentData.contains("active_skill_slots", 9)) {
                persistentData.put("active_skill_slots", new NbtList());
            }
        }
    }
}