package fr.thesmyler.terramap.gui.widgets.markers.markers;

import fr.thesmyler.smylibgui.screen.Screen;
import fr.thesmyler.terramap.gui.widgets.markers.controllers.MarkerController;
import fr.thesmyler.terramap.network.mapsync.TerramapPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public abstract class AbstractPlayerMarker extends MovingMapMarkers {
	
	public AbstractPlayerMarker(MarkerController<?> controller, TerramapPlayer player) {
		super(controller, 16, 16);
	}

	@Override
	public void draw(int x, int y, int mouseX, int mouseY, boolean hovered, boolean focused, Screen parent) {
		GlStateManager.enableAlpha();
		if(hovered) Gui.drawRect(x +1, y +1, x + 17, y + 17, 0x50000000);
		Minecraft.getMinecraft().getTextureManager().bindTexture(this.getSkin());
		GlStateManager.color(1, 1, 1, this.getTransparency());
		Gui.drawModalRectWithCustomSizedTexture(x, y, 16, 16, 16, 16, 128, 128);
		Gui.drawModalRectWithCustomSizedTexture(x, y, 80, 16, 16, 16, 128, 128);
		
		String name = this.getName();
		int strWidth = parent.getFont().getStringWidth(name);
		int nameY = y - parent.getFont().FONT_HEIGHT - 2;
		Gui.drawRect(x + 8 - strWidth / 2 - 2, y - parent.getFont().FONT_HEIGHT - 4, x + strWidth / 2 + 10, y - 1, 0x50000000);
		parent.getFont().drawCenteredString(x + 8, nameY, name, 0xFFFFFFFF, true);
		GlStateManager.color(1, 1, 1, 1);
	}
	
	protected abstract ResourceLocation getSkin();
	
	protected abstract float getTransparency();
	
	protected abstract String getName();
	
	protected abstract boolean showName(boolean hovered);

}