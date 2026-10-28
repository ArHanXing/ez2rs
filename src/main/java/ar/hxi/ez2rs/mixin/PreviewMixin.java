package ar.hxi.ez2rs.mixin;

import com.refinedmods.refinedstorage.api.autocrafting.preview.Preview;
import com.refinedmods.refinedstorage.api.autocrafting.preview.PreviewItem;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让自动合成预览里「原料不足」的条目排到列表最前面（AE2 风格）。
 *
 * <p>RS2 的列表预览条目顺序由服务端 {@code PreviewBuilder} 按合成计算顺序写入
 * {@link Preview#items()}，缺失项因此和其余条目混在一起。这里在 {@code items()} 访问器
 * 返回前做一次稳定分区：{@code missing > 0} 的条目整体前置，组内保持 RS2 原本的顺序，
 * 其余条目顺序不变。</p>
 *
 * <p>注入点选在访问器而不是 {@code Preview} 的构造器：</p>
 * <ul>
 *     <li>服务端序列化预览时走 {@code Preview::items}，客户端反序列化后渲染也走它，
 *     一处注入即可让「只装客户端」和「只装服务端」两种情况都得到排序后的结果；</li>
 *     <li>不触碰 record 的 final 字段，也不需要构造器 HEAD 处的 {@code this} 注入。</li>
 * </ul>
 *
 * <p>没有任何缺失项时原样返回入参列表，不做多余分配。</p>
 */
@Mixin(Preview.class)
public abstract class PreviewMixin {
    @Inject(method = "items", at = @At("RETURN"), cancellable = true)
    private void ez2rs$missingFirst(final CallbackInfoReturnable<List<PreviewItem>> cir) {
        final List<PreviewItem> items = cir.getReturnValue();
        if (items == null || items.size() < 2) {
            return;
        }
        boolean anyMissing = false;
        for (final PreviewItem item : items) {
            if (item.missing() > 0) {
                anyMissing = true;
                break;
            }
        }
        if (!anyMissing) {
            return;
        }
        final List<PreviewItem> sorted = new ArrayList<>(items.size());
        for (final PreviewItem item : items) {
            if (item.missing() > 0) {
                sorted.add(item);
            }
        }
        for (final PreviewItem item : items) {
            if (item.missing() <= 0) {
                sorted.add(item);
            }
        }
        cir.setReturnValue(sorted);
    }
}
