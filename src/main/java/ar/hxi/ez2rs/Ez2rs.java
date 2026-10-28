package ar.hxi.ez2rs;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EZ2RS：Refined Storage 2 的附属模组。
 *
 * <p>当前唯一功能：自动合成预览在原料不足时，把缺失的原料条目排到列表最前面
 * （见 {@code ar.hxi.ez2rs.mixin.PreviewMixin}）。排序全部由 mixin 完成，
 * 主入口只负责日志。</p>
 */
public class Ez2rs implements ModInitializer {
    public static final String MOD_ID = "ez2rs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("EZ2RS enabled: missing ingredients are pinned to the top of the autocrafting preview");
    }
}
