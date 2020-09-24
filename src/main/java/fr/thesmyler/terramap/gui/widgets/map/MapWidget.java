package fr.thesmyler.terramap.gui.widgets.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;

import fr.thesmyler.smylibgui.screen.Screen;
import fr.thesmyler.smylibgui.widgets.IWidget;
import fr.thesmyler.smylibgui.widgets.MenuWidget;
import fr.thesmyler.smylibgui.widgets.MenuWidget.MenuEntry;
import fr.thesmyler.smylibgui.widgets.text.FontRendererContainer;
import fr.thesmyler.smylibgui.widgets.text.TextAlignment;
import fr.thesmyler.smylibgui.widgets.text.TextComponentWidget;
import fr.thesmyler.terramap.GeoServices;
import fr.thesmyler.terramap.MapContext;
import fr.thesmyler.terramap.TerramapMod;
import fr.thesmyler.terramap.TerramapServer;
import fr.thesmyler.terramap.gui.EarthMapConfigGui;
import fr.thesmyler.terramap.gui.widgets.ScaleIndicatorWidget;
import fr.thesmyler.terramap.gui.widgets.markers.MarkerControllerManager;
import fr.thesmyler.terramap.gui.widgets.markers.controllers.MarkerController;
import fr.thesmyler.terramap.gui.widgets.markers.controllers.RightClickMarkerController;
import fr.thesmyler.terramap.gui.widgets.markers.markers.MapMarker;
import fr.thesmyler.terramap.maps.TiledMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;

public class MapWidget extends Screen {

	private boolean interactive = true;
	private boolean focusedZoom = true; //Zoom where the cursor is (true) or at the center of the map (false) when using the wheel
	private boolean enableRightClickMenu = true;
	private boolean showCopyright = true;

	private ControllerMapLayer controller = new ControllerMapLayer();
	protected RasterMapLayerWidget background;
	private final List<MarkerController<?>> markerControllers = new ArrayList<MarkerController<?>>();
	private RightClickMarkerController rcmMarkerController;

	private double mouseLongitude, mouseLatitude;

	private MenuWidget rightClickMenu;
	private MenuEntry teleportMenuEntry;
	private MenuEntry copyMcMenuEntry;
	private MenuEntry setProjectionMenuEntry;
	private TextComponentWidget copyright;
	private ScaleIndicatorWidget scale = new ScaleIndicatorWidget(-1);
	
	private final MapContext context;

	public static final int BACKGROUND_Z = Integer.MIN_VALUE;
	public static final int CONTROLLER_Z = 0;

	public MapWidget(int x, int y, int z, int width, int height, TiledMap<?> map, MapContext context) {
		super(x, y, z, width, height, BackgroundType.NONE);
		this.context = context;
		FontRendererContainer font = new FontRendererContainer(Minecraft.getMinecraft().fontRenderer);
		this.copyright = new TextComponentWidget(CONTROLLER_Z - 1, new TextComponentString(""), font) {
			@Override
			public boolean isVisible() {
				return MapWidget.this.showCopyright;
			}
		};
		this.copyright.setBackgroundColor(0x80000000).setPadding(3).setAlignment(TextAlignment.LEFT).setBaseColor(0xFFA0A0FF).setShadow(false);
		super.addWidget(this.copyright);
		
		this.setMapBackgroud(new RasterMapLayerWidget(map));
		
		super.addWidget(this.controller);
		this.rightClickMenu = new MenuWidget(100, font);
		this.teleportMenuEntry = this.rightClickMenu.addEntry(I18n.format("terramap.mapgui.rclickmenu.teleport"), () -> {
			this.teleportPlayerTo(this.mouseLongitude, this.mouseLatitude);
		});
		this.rightClickMenu.addEntry(I18n.format("terramap.mapgui.rclickmenu.center"), () -> {
			this.setCenterPosition(this.mouseLongitude, this.mouseLatitude);
		});
		this.rightClickMenu.addEntry(I18n.format("terramap.mapgui.rclickmenu.copy_geo"), () -> {
			GuiScreen.setClipboardString("" + this.mouseLatitude + " " + this.mouseLongitude);
		});
		this.copyMcMenuEntry = this.rightClickMenu.addEntry(I18n.format("terramap.mapgui.rclickmenu.copy_mc"), ()->{
			double[] coords = TerramapServer.getServer().getProjection().fromGeo(this.mouseLongitude, this.mouseLatitude);
			String dispX = "" + Math.round(coords[0]);
			String dispY = "" + Math.round(coords[1]);
			GuiScreen.setClipboardString(dispX + " " + dispY);
		});
		this.rightClickMenu.addSeparator();
		MenuWidget openSubMenu = new MenuWidget(this.rightClickMenu.getZ(), font);
		openSubMenu.addEntry(I18n.format("terramap.mapgui.rclickmenu.open_osm"), () -> {
			GeoServices.openInOSMWeb(Math.round((float)this.getZoom()), this.getMouseLongitude(), this.getMouseLatitude());
		});
		openSubMenu.addEntry(I18n.format("terramap.mapgui.rclickmenu.open_bte"), () -> {
			GeoServices.openInBTEMap(Math.round((float)this.getZoom()), this.getMouseLongitude(), this.getMouseLatitude());
		});
		openSubMenu.addEntry(I18n.format("terramap.mapgui.rclickmenu.open_gmaps"), () -> {
			GeoServices.openInGoogleMaps(Math.round((float)this.getZoom()), this.getMouseLongitude(), this.getMouseLatitude());
		});
		openSubMenu.addEntry(I18n.format("terramap.mapgui.rclickmenu.open_gearth_web"), () -> {
			GeoServices.opentInGoogleEarthWeb(this.getMouseLongitude(), this.getMouseLatitude());
		});
		//TODO Open in google Earth pro
		openSubMenu.addEntry(I18n.format("terramap.mapgui.rclickmenu.open_gearth_pro"));
		this.rightClickMenu.addEntry(I18n.format("terramap.mapgui.rclickmenu.open"), openSubMenu);
		this.rightClickMenu.addSeparator();
		this.setProjectionMenuEntry = this.rightClickMenu.addEntry(I18n.format("terramap.mapgui.rclickmenu.set_proj"), ()-> {
			Minecraft.getMinecraft().displayGuiScreen(new EarthMapConfigGui(null, Minecraft.getMinecraft()));	
		});
		this.scale.setX(15).setY(this.height - 5);
		this.addWidget(scale);
		this.updateRightClickMenuEntries();
		this.updateMouseGeoPos(this.width/2, this.height/2);
		
		for(MarkerController<?> controller: MarkerControllerManager.createControllers(this.context)) {
			if(controller instanceof RightClickMarkerController) {
				this.rcmMarkerController = (RightClickMarkerController) controller;
			}
			this.markerControllers.add(controller);
		}
		
	}

	public MapWidget(int z, TiledMap<?> map, MapContext context) {
		this(0, 0, z, 50, 50, map, context);
	}

	/**
	 * 
	 * @param layer
	 * @return this
	 * @throws InvalidLayerLevelException
	 */
	private MapWidget addMapLayer(MapLayerWidget layer) {
		switch(layer.getZ()) {
		case BACKGROUND_Z:
			throw new InvalidLayerLevelException("Z level " + layer.getZ() + " is reserved for background layer");
		case CONTROLLER_Z:
			throw new InvalidLayerLevelException("Z level " + layer.getZ() + " is reserved for controller layer");
		}
		super.addWidget(layer);
		return this;
	}

	private MapWidget setMapBackgroud(RasterMapLayerWidget background) {
		background.z = BACKGROUND_Z;
		super.removeWidget(this.background);
		super.addWidget(background);
		this.background = background;
		this.copyright.setComponent(new TextComponentString(background.map.getCopyright())); //TODO parse the component
		return this;
	}
	
	public void setBackground(TiledMap<?> map) {
		this.setMapBackgroud(new RasterMapLayerWidget(map));
	}

	/**
	 * Adds a widget to the screen. Since this is a map before being a screen,
	 * {@link #addMapLayer(MapLayerWidget) addMapLayer} should be used instead
	 * and other types of widget should not be added to the map directly
	 * but rather on the parent screen.
	 * 
	 * @param widget to add
	 * @throws InvalidLayerLevelException if the widget has an incompatible z value
	 */
	@Override @Deprecated
	public Screen addWidget(IWidget widget) {
		if(widget instanceof MapLayerWidget) {
			this.addMapLayer((MapLayerWidget)widget);
		} else {
			switch(widget.getZ()) {
			case BACKGROUND_Z:
				throw new InvalidLayerLevelException("Z level " + widget.getZ() + " is reserved for background layer");
			case CONTROLLER_Z:
				throw new InvalidLayerLevelException("Z level " + widget.getZ() + " is reserved for controller layer");
			}
			super.addWidget(widget);
		}
		return this;
	}

	@Override
	public void onUpdate(@Nullable Screen parent) {
		super.onUpdate(parent);
		
	}

	@Override
	public void draw(int x, int y, int mouseX, int mouseY, boolean hovered, boolean focused, Screen parent) {
		this.copyright.setAnchorX(this.getWidth() - 3).setAnchorY(this.getHeight() - this.copyright.getHeight());
		if(!this.rightClickMenu.isVisible()) {
			int relativeMouseX = mouseX - x;
			int relativeMouseY = mouseY - y;
			this.updateMouseGeoPos(relativeMouseX, relativeMouseY);
		}
		Map<Class<?>, List<MapMarker>> markers = new HashMap<Class<?>, List<MapMarker>>();
		for(MarkerController<?> controller: this.markerControllers) {
			markers.put(controller.getMarkerType(), new ArrayList<MapMarker>());
		}
		for(IWidget widget: this.widgets) {
			if(widget instanceof MapLayerWidget) {
				MapLayerWidget layer = (MapLayerWidget) widget;
				layer.width = this.width;
				layer.height = this.height;
				if(!layer.equals(this.controller)) {
					layer.centerLongitude = this.controller.centerLongitude;
					layer.centerLatitude = this.controller.centerLatitude;
					layer.zoom = this.controller.zoom;
				}
			} else if(widget instanceof MapMarker) {
				for(Class<?> clazz: markers.keySet()) {
					if(clazz.isInstance(widget)) {
						markers.get(clazz).add((MapMarker)widget);
					}
				}
			}
		}
		for(MarkerController<?> controller: this.markerControllers) {
			MapMarker[] existingMarkers = markers.get(controller.getMarkerType()).toArray(new MapMarker[] {});
			for(MapMarker markerToAdd: controller.getNewMarkers(existingMarkers)) {
				this.addWidget(markerToAdd);
			}
		}
		
		/* The map markers have a higher priority than the background since they are on top,
		 * which means that they are updated before it moves,
		 * so they lag behind when the map moves fast if they are not updated again
		 */
		for(IWidget w: this.widgets) {
			if(w instanceof MapMarker) {
				w.onUpdate(this); 
			}
		}
		if(this.rcmMarkerController != null) this.rcmMarkerController.setVisibility(this.rightClickMenu.isVisible());
		super.draw(x, y, mouseX, mouseY, hovered, focused, parent);
	}

	private class ControllerMapLayer extends MapLayerWidget {

		public ControllerMapLayer() {
			this.z = CONTROLLER_Z;
		}

		@Override
		public void draw(int x, int y, int mouseX, int mouseY, boolean hovered, boolean focused, Screen parent) {
			//Literally nothing to do here, this is strictly used to handle user input
		}

		@Override
		public boolean onClick(int mouseX, int mouseY, int mouseButton, @Nullable Screen parent) {
			if(MapWidget.this.enableRightClickMenu && mouseButton == 1) {
				parent.showMenu(mouseX, mouseY, MapWidget.this.rightClickMenu);
			}
			return false;
		}

		@Override
		public boolean onDoubleClick(int mouseX, int mouseY, int mouseButton, @Nullable Screen parent) {

			// We don't care about double right clicks
			if(mouseButton == 1) this.onClick(mouseX, mouseY, mouseButton, parent);

			if(MapWidget.this.isInteractive() && mouseButton == 0) {
				this.zoom(mouseX, mouseY, 1);
			}
			return false;
		}

		@Override
		public void onMouseDragged(int mouseX, int mouseY, int dX, int dY, int mouseButton, @Nullable Screen parent) {
			if(MapWidget.this.isInteractive() && mouseButton == 0) {
				this.moveMap(dX, dY);
			}
		}

		@Override
		public void onKeyTyped(char typedChar, int keyCode, @Nullable Screen parent) {
			if(MapWidget.this.isInteractive()) {
				switch(keyCode) {
				case Keyboard.KEY_ESCAPE:
					Minecraft.getMinecraft().displayGuiScreen(null); //FIXME NOT HERE
					break;
				}
			}
		}

		@Override
		public boolean onMouseWheeled(int mouseX, int mouseY, int amount, @Nullable Screen parent) {
			if(MapWidget.this.isInteractive()) {
				int z = amount > 0? 1: -1;
				if(MapWidget.this.focusedZoom) {
					this.zoom(mouseX, mouseY, z);
				} else {
					this.zoom(z);
				}
			}
			return false;
		}

		public void zoom(int val) {
			this.zoom(this.width/2, this.height/2, val);
		}

		public void zoom(int mouseX, int mouseY, int zoom) {

			MapWidget.this.rightClickMenu.hide(null);

			double nzoom = this.zoom + zoom;
			nzoom = Math.max(getMinZoom(), nzoom);
			nzoom = Math.min(getMaxZoom(), nzoom);

			if(nzoom == this.zoom) return; // Do not move if we are not doing anything

			this.zoom = nzoom;
			double factor = Math.pow(2, zoom);
			double ndX = ((double)this.width/2 - mouseX) * factor;
			double ndY = ((double)this.height/2 - mouseY) * factor;
			if(factor > 1) {
				ndX = -ndX / 2;
				ndY = -ndY / 2;
			}

			//FIXME Re-implement better zoom
			this.setCenterLongitude(this.getScreenLongitude((double)this.width/2 + ndX));
			this.setCenterLatitude(this.getScreenLatitude((double)this.height/2 + ndY));
			//			this.mapVelocityX *= factor;
			//			this.mapVelocityY *= factor;
			//			this.updateMouseGeoPos(mouseX, mouseY);

			TerramapMod.cacheManager.clearQueue(); // We are displaying new tiles, we don't need what we needed earlier

		}

		public void moveMap(int dX, int dY) {
			//			this.closeRightClickMenu();
			//			this.followedPOI = null;
			double nlon = this.getScreenLongitude((double)this.width/2 - dX);
			double nlat = this.getScreenLatitude((double)this.height/2 - dY);
			this.setCenterLongitude(nlon);
			this.setCenterLatitude(nlat);
		}

	}

	private void updateMouseGeoPos(int mouseX, int mouseY) {
		this.mouseLongitude = controller.getScreenLongitude((double)mouseX);
		this.mouseLatitude = controller.getScreenLatitude((double)mouseY);
	}

	private void updateRightClickMenuEntries() {
		boolean hasProjection = TerramapServer.getServer().getProjection() != null;
		this.teleportMenuEntry.enabled = hasProjection;
		this.copyMcMenuEntry.enabled = hasProjection;
		this.setProjectionMenuEntry.enabled = !TerramapServer.getServer().isInstalledOnServer();
	}
	
	private void teleportPlayerTo(double longitude, double latitude) {
		String cmd = TerramapServer.getServer().getTpCommand().replace("{longitude}", ""+longitude).replace("{latitude}", ""+latitude);
		if(TerramapServer.getServer().getProjection() != null) {
			double[] xz = TerramapServer.getServer().getProjection().fromGeo(longitude, latitude);
			cmd = cmd.replace("{x}", "" + xz[0]).replace("{z}", "" + xz[1]);
		} else {
			TerramapMod.logger.error("Tried to teleport from the map but the projection was null!");
		}
		this.sendChatMessage(cmd, false);
	}
	
	public MarkerController<?>[] getMarkerControllers() {
		return this.markerControllers.toArray(new MarkerController<?>[0]);
	}

	public double getZoom() {
		return this.controller.getZoom();
	}

	public double getMaxZoom() {
		return this.background.map.getMaxZoom(); //TODO Take other layers into account
	}

	public double getMinZoom() {
		return this.background.map.getMinZoom(); //TODO Take other layers into account
	}

	public MapWidget setZoom(double zoom) {
		this.controller.setZoom(zoom);
		return this;
	}

	public MapWidget zoom(int zoom) {
		this.controller.zoom(zoom);
		return this;
	}

	public double getCenterLongitude() {
		return this.controller.getCenterLongitude();
	}

	public MapWidget setCenterLongitude(double longitude) {
		this.controller.setCenterLongitude(longitude);
		return this;
	}

	public double getCenterLatitude() {
		return this.controller.getCenterLatitude();
	}

	public MapWidget setCenterLatitude(double latitude) {
		this.controller.setCenterLatitude(latitude);
		return this;
	}

	public double[] getCenterPosition() {
		return new double[] {this.getCenterLongitude(), this.getCenterLatitude()};
	}

	public MapWidget setCenterPosition(double longitude, double latitude) {
		return this.setCenterLongitude(longitude).setCenterLatitude(latitude);
	}

	public MapWidget setCenterPosition(double[] position) {
		this.setCenterPosition(position[0], position[1]);
		return this;
	}

	public double getMouseLongitude() {
		return this.mouseLongitude;
	}

	public double getMouseLatitude() {
		return this.mouseLatitude;
	}

	public double[] getMousePosition() {
		return new double[] {this.mouseLongitude, this.mouseLatitude};
	}

	public MapWidget setX(int x) {
		this.x = x;
		return this;
	}

	public MapWidget setY(int y) {
		this.y = y;
		return this;
	}

	public MapWidget setWidth(int width) {
		this.width = width;
		return this;
	}

	public MapWidget setHeight(int height) {
		this.height = height;
		this.scale.setY(this.height - 20);
		return this;
	}

	public boolean isInteractive() {
		return this.interactive;
	}

	public MapWidget setInteractive(boolean yesNo) {
		this.interactive = yesNo;
		return this;
	}

	public boolean isRightClickMenuEnabled() {
		return this.enableRightClickMenu;
	}

	public MapWidget setRightClickMenuEnabled(boolean yesNo) {
		this.enableRightClickMenu = yesNo;
		return this;
	}

	public MapWidget enableRightClickMenu() {
		return this.setRightClickMenuEnabled(true);
	}

	public MapWidget disableRightClickMenu() {
		return this.setRightClickMenuEnabled(false);
	}
	
	public boolean getCopyrightVisibility() {
		return this.showCopyright;
	}
	
	public MapWidget setCopyrightVisibility(boolean yesNo) {
		this.showCopyright = yesNo;
		return this;
	}

	public void moveMap(int dX, int dY) {
		controller.moveMap(dX, dY);
	}

	public double getScreenX(double longitude) {
		return this.background.getScreenX(longitude);
	}

	public double getScreenY(double latitude) {
		return this.background.getScreenY(latitude);
	}
	
	public double getScreenLongitude(double xOnScreen) {
		return this.background.getScreenLongitude(xOnScreen);
	}

	public double getScreenLatitude(double yOnScreen) {
		return this.background.getScreenLatitude(yOnScreen);
	}
	
	public int getScaleX() {
		return this.scale.getX();
	}
	
	public MapWidget setScaleX(int x) {
		this.scale.setX(x);
		return this;
	}
	
	public int getScaleY() {
		return this.scale.getY();
	}
	
	public MapWidget setScaleY(int y) {
		this.scale.setY(y);
		return this;
	}
	
	public int getScaleWidth() {
		return this.scale.getWidth();
	}
	
	public MapWidget setScaleWidth(int width) {
		this.scale.setWidth(width);
		return this;
	}
	
	public boolean getScaleVisibility() {
		return this.scale.isVisible();
	}
	
	public MapWidget setScaleVisibility(boolean yesNo) {
		this.scale.setVisibility(yesNo);
		return this;
	}
	
	public MapContext getContext() {
		return this.context;
	}

}