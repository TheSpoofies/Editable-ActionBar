


import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


import javax.swing.text.JTextComponent;
import java.io.*;
import java.nio.file.*;

@Environment(EnvType.CLIENT)
public class actionbarClient implements ClientModInitializer {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static KeyBinding configModeKey;
    private static boolean initialized = false;
    public static long lastActionBarUpdateTime = 0;
    private static float scale = 1.5f;
    private static int posX = 0;
    private static int posY = 0;
    public static Text interceptedActionBar = null;
    private static final Path CONFIG_PATH = Path.of("config", "ability_editor.txt");

    @Override
    public void onInitializeClient() {
        if (initialized) return;
        initialized = true;
        loadConfig();

        if (configModeKey == null) {
            configModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "Move Abilities",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_P,
                    "Ability Editor"
            ));
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (configModeKey.wasPressed()) {
                if (!(client.currentScreen instanceof Screen)) {
                    client.setScreen(new EditorScreen());
                } else {
                    client.setScreen(null);
                }
            }
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (client.player == null || client.options.hudHidden) return;
            if (System.currentTimeMillis() - lastActionBarUpdateTime > 3000) {
                interceptedActionBar = null;
            }
            draw(context);
        });
    }

    private static void draw(DrawContext context) {
        if (interceptedActionBar == null) return;
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int x = screenWidth / 2 + posX;
        int y = screenHeight - 60 + posY;
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        int width = client.textRenderer.getWidth(interceptedActionBar);
        context.drawText(client.textRenderer, interceptedActionBar, -width / 2, 0, 0xFFFFFF, true);
        context.getMatrices().pop();
    }

    public static class EditorScreen extends Screen {
        private boolean dragging = false;
        private boolean resizing = false;
        private int dragOffsetX, dragOffsetY;

        protected EditorScreen() {
            super(Text.of("Action Bar Editor"));
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            RenderSystem.enableBlend();
            context.fill(0, 0, width, height, 0x88000000);
            Text text = interceptedActionBar != null ? interceptedActionBar : Text.of("Placeholder");
            int screenCenterX = width / 2 + posX;
            int screenBottomY = height - 60 + posY;
            int textWidth = (int) (textRenderer.getWidth(text) * scale);
            int textHeight = (int) (textRenderer.fontHeight * scale);
            context.getMatrices().push();
            context.getMatrices().translate(screenCenterX, screenBottomY, 0);
            context.getMatrices().scale(scale, scale, 1.0f);
            context.drawText(textRenderer, text, -textRenderer.getWidth(text) / 2, 0, 0xFFAAAA, true);
            context.getMatrices().pop();
            if (dragging || resizing) {
                int boxX1 = screenCenterX - textWidth / 2 - 4;
                int boxY1 = screenBottomY - 2;
                int boxX2 = screenCenterX + textWidth / 2 + 4;
                int boxY2 = screenBottomY + textHeight + 2;
                context.fill(boxX1, boxY1, boxX2, boxY2, 0x44FFFFFF);
                context.drawBorder(boxX1, boxY1, boxX2 - boxX1, boxY2 - boxY1, 0xFFFFFFFF);
            }
            int dotX = screenCenterX + textWidth / 2 + 8;
            int dotY = screenBottomY + textHeight / 2 - 4;
            context.fill(dotX, dotY, dotX + 8, dotY + 8, 0xFFFFAAAA);
            int resetButtonWidth = 60;
            int resetButtonHeight = 20;
            int resetX = width - resetButtonWidth - 10;
            int resetY = 10;

            context.fill(resetX, resetY, resetX + resetButtonWidth, resetY + resetButtonHeight, 0xFF555555);
            context.drawText(textRenderer, "Reset", resetX + 12, resetY + 6, 0xFFFFFF, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int resetButtonWidth = 60;
            int resetButtonHeight = 20;
            int resetX = width - resetButtonWidth - 10;
            int resetY = 10;
            if (mouseX >= resetX && mouseX <= resetX + resetButtonWidth &&
                    mouseY >= resetY && mouseY <= resetY + resetButtonHeight) {
                posX = 0;
                posY = -10;
                scale = 1.0f;
                saveConfig();
                return true;
            }
            Text text = interceptedActionBar != null ? interceptedActionBar : Text.of("✦ Example ✦");
            int screenCenterX = width / 2 + posX;
            int screenBottomY = height - 60 + posY;
            int textWidth = (int) (textRenderer.getWidth(text) * scale);
            int textHeight = (int) (textRenderer.fontHeight * scale);
            int dotX = screenCenterX + textWidth / 2 + 8;
            int dotY = screenBottomY + textHeight / 2 - 4;
            if (mouseX >= dotX && mouseX <= dotX + 8 && mouseY >= dotY && mouseY <= dotY + 8) {
                resizing = true;
                return true;
            }
            if (mouseX >= screenCenterX - textWidth / 2 && mouseX <= screenCenterX + textWidth / 2 &&
                    mouseY >= screenBottomY && mouseY <= screenBottomY + textHeight) {
                dragging = true;
                dragOffsetX = (int) mouseX - screenCenterX;
                dragOffsetY = (int) mouseY - screenBottomY;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            dragging = false;
            resizing = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (dragging) {
                int centerX = width / 2;
                int bottomY = height - 60;
                posX = (int) mouseX - centerX - dragOffsetX;
                posY = (int) mouseY - bottomY - dragOffsetY;
                saveConfig();
                return true;
            }

            if (resizing) {
                scale += deltaX * 0.01f;
                if (scale < 0.5f) scale = 0.5f;
                if (scale > 4.0f) scale = 4.0f;
                saveConfig();
                return true;
            }

            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
    }

    private static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (PrintWriter writer = new PrintWriter(CONFIG_PATH.toFile())) {
                writer.println(posX);
                writer.println(posY);
                writer.println(scale);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_PATH.toFile()))) {
                    posX = Integer.parseInt(reader.readLine());
                    posY = Integer.parseInt(reader.readLine());
                    scale = Float.parseFloat(reader.readLine());
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
    }

    @Mixin(InGameHud.class)
    public static class ActionBarInterceptor {
        @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
        private void intercept(Text message, boolean tinted, CallbackInfo ci) {
            Spoofies_actionbarClient.interceptedActionBar = message;
            ci.cancel();
        }
    }
}
