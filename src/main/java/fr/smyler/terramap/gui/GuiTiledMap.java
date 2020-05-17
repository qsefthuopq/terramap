package fr.smyler.terramap.gui;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import fr.smyler.terramap.TerramapMod;
import fr.smyler.terramap.maps.TiledMap;
import fr.smyler.terramap.maps.tiles.RasterWebTile;
import fr.smyler.terramap.maps.tiles.RasterWebTile.InvalidTileCoordinatesException;
import fr.smyler.terramap.maps.utils.TerramapUtils;
import fr.smyler.terramap.maps.utils.WebMercatorUtils;
import io.github.terra121.EarthBiomeProvider;
import io.github.terra121.projection.GeographicProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;

//TODO Better zoom
//TODO Custom scaling
public class GuiTiledMap extends GuiScreen {

	/*
	 * The position of the map on the GUI
	 */
	protected int x;
	protected int y;

	protected boolean visible;
	protected boolean hovered;

	protected TiledMap<?> map;

	protected double focusLatitude;
	protected double focusLongitude;
	protected boolean debug = false; //Show tiles borders or not

	protected int zoomLevel;

	public GuiTiledMap(TiledMap<?> map) {
		this.visible = true;
		this.hovered = false;
		this.map = map;
		this.zoomLevel = map.getZoomLevel();
		this.setZoom(13);
		this.focusLatitude = 0;
		this.focusLongitude = 0;
	}

	@Override
	public void initGui() {
		Minecraft mc = Minecraft.getMinecraft();
		this.initGui(0, 0, mc.displayWidth, mc.displayHeight);
	}

	public void initGui(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		GeographicProjection proj = ((EarthBiomeProvider)Minecraft.getMinecraft().getIntegratedServer().getWorld(0).getBiomeProvider()).projection;
		EntityPlayerSP p = Minecraft.getMinecraft().player;
		double coords[] = proj.toGeo(p.posX, p.posZ);
		this.focusLatitude = coords[1];
		this.focusLongitude = coords[0];
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawMap(mouseX, mouseY, partialTicks);
		Gui.drawRect(0, 0, 200, 150, 0xAA000000);
		String dispLat = "" + (float)Math.round(this.focusLatitude * 100000) / 100000;
		String dispLong = "" + (float)Math.round(this.focusLongitude * 100000) / 100000;
		this.drawString(this.fontRenderer, "Map position: " + dispLat + " " + dispLong, 10, 10, 0xFFFFFF);
		this.drawString(this.fontRenderer, "Zoom level: " + this.zoomLevel, 10, 20 + this.fontRenderer.FONT_HEIGHT, 0xFFFFFF);
		this.drawString(this.fontRenderer, "Cache queue: " + TerramapMod.cacheManager.getQueueSize(), 10, 30 + + this.fontRenderer.FONT_HEIGHT * 2, 0xFFFFFF );
		this.drawString(this.fontRenderer, "Loaded tiles: " + this.map.getLoadedCount() + "/" + this.map.getMaxLoad(), 10, 40 + this.fontRenderer.FONT_HEIGHT * 3, 0xFFFFFF);
	}

	private void drawMap(int mouseX, int mouseY, float partialTicks) {

		if((int)this.zoomLevel != this.map.getZoomLevel()) {
			TerramapMod.logger.info("Zooms are differents: GUI: " + this.zoomLevel + " | Map: " + this.map.getZoomLevel());
		}
		double renderFactor = this.getSizeFactor();
		int renderSize = WebMercatorUtils.TILE_DIMENSIONS;

		long upperLeftX = this.getUpperLeftX(this.zoomLevel, this.focusLongitude);
		long upperLeftY = this.getUpperLeftY(this.zoomLevel, this.focusLatitude);

		//TODO handle keybord input in a method
		this.handleMouseInput();

		if(Keyboard.isKeyDown(Keyboard.KEY_L)) {
			this.debug = !this.debug;
		}

		Minecraft mc = Minecraft.getMinecraft();
		TextureManager textureManager = mc.getTextureManager();

		int maxTileXY = (int) map.getSizeInTiles();
		long maxX = (long) (upperLeftX + this.width / renderFactor);
		long maxY = (long) (upperLeftY + this.height / renderFactor);

		int lowerTX = (int) Math.floor((double)upperLeftX / (double)renderSize);
		int lowerTY = (int) Math.floor((double)upperLeftY / (double)renderSize);

		for(int tX = lowerTX; tX * renderSize < maxX; tX++) {

			for(int tY = lowerTY; tY * renderSize < maxY; tY++) {

				RasterWebTile tile;

				try {
					tile = map.getTile(TerramapUtils.modulus(tX, maxTileXY), tY, this.zoomLevel);
				} catch(InvalidTileCoordinatesException e) { continue ;}
				//This is the tile we would like to render, but it is not possible if it hasn't been cached yet
				RasterWebTile bestTile = tile;
				boolean lowerResRender = false;

				if(!TerramapMod.cacheManager.isCached(tile)) {
					lowerResRender = true;
					if(!TerramapMod.cacheManager.isBeingCached(tile))
						TerramapMod.cacheManager.cacheAsync(tile);
					while(tile.getZoom() > 0 && !TerramapMod.cacheManager.isCached(tile)) {
						tile = this.map.getTile(tile.getX() /2, tile.getY() /2, tile.getZoom()-1);
					}
				}

				int dispX = Math.round(this.x + tX * renderSize - upperLeftX);
				int displayWidth = (int) Math.min(renderSize, maxX - tX * renderSize);

				int displayHeight = (int) Math.min(renderSize, maxY - tY * renderSize);
				int dispY = Math.round(this.y + tY * renderSize - upperLeftY);

				int renderSizedSize = renderSize;

				int dX = 0;
				int dY = 0;

				if(lowerResRender) {
					int sizeFactor = (1 <<(bestTile.getZoom() - tile.getZoom()));

					int xInBiggerTile = (int) (bestTile.getX() - sizeFactor * tile.getX());
					int yInBiggerTile = (int) (bestTile.getY() - sizeFactor * tile.getY());

					double factorX = (double)xInBiggerTile / (double)sizeFactor;
					double factorY = (double)yInBiggerTile / (double)sizeFactor;
					renderSizedSize *= sizeFactor;
					dX += (int) (factorX * renderSizedSize);
					dY += (int) (factorY * renderSizedSize);
				}

				if(tX == lowerTX) {
					dX += this.x-dispX;
					dispX = this.x;
				}

				if(tY == lowerTY) {
					dY += this.y-dispY;
					dispY = this.y;
				}

				textureManager.bindTexture(tile.getTexture());
				drawModalRectWithCustomSizedTexture(
						dispX,
						dispY,
						dX, dY,
						displayWidth,
						displayHeight,
						renderSizedSize,
						renderSizedSize);

				if(this.debug) {
					final int RED = 0xFFFF0000;
					final int WHITE = 0xFFFFFFFF;
					this.drawHorizontalLine(
							dispX,
							dispX + displayWidth - 1,
							dispY,
							lowerResRender? RED : WHITE);
					this.drawHorizontalLine(
							dispX,
							dispX + displayWidth - 1,
							dispY + displayHeight - 1,
							lowerResRender? RED : WHITE);
					this.drawVerticalLine(
							dispX,
							dispY,
							dispY + displayHeight - 1,
							lowerResRender? RED : WHITE);
					this.drawVerticalLine(
							dispX + displayWidth - 1,
							dispY,
							dispY + displayHeight - 1,
							lowerResRender? RED : WHITE);
				}
				GlStateManager.color(255, 255, 255, 255);


			}

		}

	}


	@Override
	public void updateScreen(){
		if(!this.isPositionValid(this.zoomLevel, this.focusLongitude, this.focusLatitude)) {
			TerramapMod.logger.error("Map is in an invalid state! Reseting!");
			this.setZoomToMinimum();
		}
	}		

	/**
	 * Handles mouse input.
	 */
	@Override
	public void handleMouseInput(){

		//Moving
		if(Mouse.isButtonDown(0)) {

			//TODO This should adapt to the zoom level
			int dX = Mouse.getDX();
			int dY = Mouse.getDY();

			double nlon = this.focusLongitude - dX/Math.pow(2, this.zoomLevel)/2;
			double nlat = this.focusLatitude - dY/Math.pow(2, this.zoomLevel)/2;
			this.setLongitude(nlon);
			this.setLatitude(nlat);


		}

		//Scrolling
		int i = Mouse.getDWheel();
		int z;
		if (i != 0){
			if (i > 0) z = 1;
			else z = - 1;
			this.zoom(z);
		}
	}

	@Override
	public void onGuiClosed() {
		this.map.unloadAll();
	}

	public void zoom(int val) {

		int nzoom = this.zoomLevel + val;
		if(!this.isPositionValid(nzoom, this.focusLongitude, this.focusLatitude)) return;

		//int oldRenderSize = this.getTileRenderSize(this.map.getZoomLevel());

		//int mouseX = Mouse.getX() - this.x;
		//int mouseY = Mouse.getY() - this.y;

		//TODO TEMP
		//IRLW.logger.info(mouseX);
		//IRLW.logger.info(mouseY);
		//    	long newUpperLeftX = (long) ((double)this.upperLeftX / oldRenderSize * newRenderSize);
		//long newUpperLeftY = (long) ((double)this.upperLeftY / oldRenderSize * newRenderSize);
		//long newUpperLeftX = (long) ((double)this.upperLeftX / oldRenderSize * newRenderSize);
		//newUpperLeftX -=  (double)mouseX/this.width/oldRenderSize * newRenderSize;
		//    	long newUpperLeftY = (long) ((double)(this.upperLeftY - mouseY) / oldRenderSize * newRenderSize);
		//    	IRLW.logger.info(newUpperLeftX);
		//    	IRLW.logger.info(newUpperLeftY);
		//if(this.setPosition(newUpperLeftX, newUpperLeftY)){   
		TerramapMod.cacheManager.clearQueue(); // We are displaying new tiles, we don't need what we needed earlier
		this.zoomLevel = nzoom;
		//}
		this.setTiledMapZoom();

		//FIXME
	}


	public void setZoomToMinimum() {
		int i = this.zoomLevel;
		while(!this.isPositionValid(i, 0, 0)) i++;
		this.setZoom(i);
		this.setPosition(0, 0);
	}

	private void setTiledMapZoom() {
		this.map.setZoomLevel((int)this.zoomLevel);
	}

	private double getSizeFactor() {
		return Minecraft.getMinecraft().gameSettings.guiScale;
	}

	/**
	 * 
	 * @param zoom
	 * @return The size of the full map, in pixel
	 */
	private long getMaxMapSize(int zoom) {
		return (long) (WebMercatorUtils.getDimensionsInTile(zoom) * WebMercatorUtils.TILE_DIMENSIONS);
	}

	/* === Getters and Setters from this point === */

	public void setSize(int width, int height) {
		this.width = width;
		this.height = height;
	}

	public boolean setPosition(double lon, double lat) {
		if(!this.isPositionValid(this.zoomLevel, lon, lat)) return false;
		this.focusLongitude = lon;
		this.focusLatitude = lat;
		return true;
	}

	public boolean setLongitude(double lon) {
		if(!this.isPositionValid(this.zoomLevel, lon, this.focusLatitude)) return false;
		this.focusLongitude = lon;
		return true;
	}

	public boolean setLatitude(double lat) {
		if(!this.isPositionValid(this.zoomLevel, this.focusLongitude, lat)) return false;
		this.focusLatitude = lat;
		return true;
	}

	private void setZoom(int zoom) {
		this.zoomLevel = zoom;
		this.setTiledMapZoom();
	}

	private long getUpperLeftX(int zoomLevel, double centerLong) {
		double renderFactor = this.getSizeFactor();
		return (long)(
				(double)(WebMercatorUtils.getXFromLongitude(centerLong, zoomLevel))
				- ((double)this.width) / 2f / renderFactor);
	}

	private long getUpperLeftY(int zoomLevel, double centerLat) {
		double renderFactor = this.getSizeFactor();
		return (long)(
				(double)WebMercatorUtils.getYFromLatitude(centerLat, zoomLevel)
				- (double)this.height / 2f / renderFactor);
	}

	private boolean isPositionValid(int zoomLevel, double centerLong, double centerLat) {
		if(zoomLevel < 0) return false;
		if(zoomLevel > 19) return false;
		long upperLeftY = this.getUpperLeftY(zoomLevel, centerLat);
		long lowerLeftY = (long) (upperLeftY + this.height / this.getSizeFactor());
		if(upperLeftY < 0) return false;
		if(lowerLeftY > this.getMaxMapSize(zoomLevel)) return false;
		return true;
	}

}
