package com.bettercontent.bettercooking.client;

import com.bettercontent.bettercooking.kitchen.KitchenMenu;
import com.bettercontent.bettercooking.kitchen.KitchenNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Hold input is a heartbeat, never a quantity of work. All timing is server authoritative. */
public final class KitchenScreen extends AbstractContainerScreen<KitchenMenu> {
    private boolean holding;
    private long nextPulse;
    private Button nativeCutButton;
    public KitchenScreen(KitchenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageHeight = menu.supportsNativeCuts() ? 236 : 208; inventoryLabelY = 114;
    }
    @Override protected void init() {
        super.init(); holding = false;
        addRenderableWidget(Button.builder(Component.literal("Pick recipe"), b -> click(0)).bounds(leftPos + 68, topPos + 24, 100, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Begin batch"), b -> click(1)).bounds(leftPos + 68, topPos + 64, 100, 18).build());
        if (menu.supportsNativeCuts()) nativeCutButton = addRenderableWidget(Button.builder(Component.literal("Next cut (input on cursor)"), b -> click(2)).bounds(leftPos + 8, topPos + 210, 160, 18).build());
    }
    private void click(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && x >= leftPos + 8 && x < leftPos + 168 && y >= topPos + 88 && y < topPos + 108 && menu.preparing()) {
            holding = true; nextPulse = 0; return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) { if (button == 0) holding = false; return super.mouseReleased(x, y, button); }
    @Override protected void containerTick() {
        super.containerTick();
        if (minecraft == null || minecraft.level == null || minecraft.player == null || minecraft.player.containerMenu != menu || !minecraft.isWindowActive()) { holding = false; return; }
        if (nativeCutButton != null) {
            var cut = menu.selectedNativeCut();
            nativeCutButton.setMessage(Component.literal(cut == null ? "Next cut (input on cursor)" : "Cut: " + font.plainSubstrByWidth(cut.getId().getPath(), 124)));
            nativeCutButton.active = !menu.preparing() && !menu.getCarried().isEmpty();
        }
        var recipe = menu.selected(); long now = minecraft.level.getGameTime();
        if (holding && menu.preparing() && recipe != null && now >= nextPulse) { KitchenNetwork.pulse(menu.containerId, recipe.getId()); nextPulse = now + 5; }
    }
    @Override protected void renderBg(GuiGraphics gui, float partial, int mouseX, int mouseY) {
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffc6c6c6);
        for (var slot : menu.slots) { gui.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff555555); gui.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xff999999); }
        gui.fill(leftPos + 8, topPos + 88, leftPos + 168, topPos + 108, holding ? 0xff668855 : 0xff777777);
        var recipe = menu.selected();
        String label = "Hold to prepare";
        if (recipe != null && menu.stage() < recipe.stages().size()) {
            var stage = recipe.stages().get(menu.stage());
            label = stage.action() + " " + (menu.repetition() + 1) + "/" + stage.repetitions() + " (hold)";
            int width = (int) (160L * Math.min(menu.elapsed(), stage.durationTicks()) / stage.durationTicks());
            gui.fill(leftPos + 8, topPos + 105, leftPos + 8 + width, topPos + 108, 0xff99dd88);
        }
        gui.drawCenteredString(font, label, leftPos + 88, topPos + 94, 0xffffffff);
    }
    @Override protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        super.renderLabels(gui, mouseX, mouseY);
        var recipe = menu.selected();
        if (recipe != null) {
            var result = recipe.getResultItem(minecraft.level.registryAccess());
            gui.drawString(font, font.plainSubstrByWidth(result.getHoverName().getString(), 100), 68, 46, 0xff222222, false);
            if (menu.stage() < recipe.stages().size()) {
                String tool = recipe.stages().get(menu.stage()).toolTag();
                String hint = tool == null || tool.isBlank() ? "No tool needed" : "Tool: #" + tool.replaceFirst("^#", "");
                gui.drawString(font, font.plainSubstrByWidth(hint, 100), 68, 55, 0xff444444, false);
            }
        }
    }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        renderBackground(gui); super.render(gui, mouseX, mouseY, partial); renderTooltip(gui, mouseX, mouseY);
        var cut = menu.selectedNativeCut();
        if (cut != null && mouseX >= leftPos + 8 && mouseX < leftPos + 168 && mouseY >= topPos + 210 && mouseY < topPos + 228) {
            java.util.List<Component> lines = new java.util.ArrayList<>();
            lines.add(Component.literal(cut.getId().toString()));
            lines.add(Component.literal("Possible outputs, in native order (not a roll):"));
            var entries = com.bettercontent.bettercooking.cutting.NativeCuttingSelection.preview(cut);
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i); var stack = entry.stack();
                lines.add(Component.literal((i + 1) + ". " + stack.getCount() + "x " + stack.getHoverName().getString() + " — " + com.bettercontent.bettercooking.cutting.NativeCuttingSelection.chanceLabel(entry.chance())));
            }
            lines.add(Component.literal("KC knife" + (cut instanceof com.bettercontent.bettercooking.cutting.NativeCuttingRecipe own ? " AND " + own.plan().tool().toJson() : "")));
            lines.add(Component.literal("Close menu, place input, then cut; selection grants no output/work."));
            gui.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        var recipe = menu.selected();
        if (recipe != null && menu.stage() < recipe.stages().size() && mouseX >= leftPos + 68 && mouseX < leftPos + 168 && mouseY >= topPos + 55 && mouseY < topPos + 64) {
            String tool = recipe.stages().get(menu.stage()).toolTag();
            gui.renderTooltip(font, Component.literal(tool == null || tool.isBlank() ? "No tool needed" : "Main or off hand: #" + tool.replaceFirst("^#", "")), mouseX, mouseY);
        }
    }
}
