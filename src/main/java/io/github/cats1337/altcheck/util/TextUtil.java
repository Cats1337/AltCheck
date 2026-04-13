package io.github.cats1337.altcheck.util;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Utility for consistent chat formatting.
 */
public class TextUtil {

    public static Text prefix() {
        return Text.literal("[AltCheck] ")
                .formatted(Formatting.DARK_AQUA);
    }

    public static Text success(String msg) {
        return prefix().copy().append(Text.literal(msg).formatted(Formatting.GREEN));
    }

    public static Text error(String msg) {
        return prefix().copy().append(Text.literal(msg).formatted(Formatting.RED));
    }

    public static Text info(String msg) {
        return prefix().copy().append(Text.literal(msg).formatted(Formatting.GRAY));
    }

    public static Text raw(String msg) {
        return Text.literal(msg);
    }

    // =========================================================
    // IP DISPLAY WITH HOVER + COPY
    // =========================================================
    public static MutableText ip(String full, String masked) {
        return Text.literal(masked)
                .styled(style -> style
                        .withColor(Formatting.AQUA)
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Text.literal("§9AltCheck\n§7" + full + "\n§8Click to copy")
                        ))
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.COPY_TO_CLIPBOARD,
                                full
                        ))
                );
    }
}