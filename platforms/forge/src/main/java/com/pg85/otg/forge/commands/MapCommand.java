package com.pg85.otg.forge.commands;

import com.pg85.otg.forge.biome.OTGBiomeProvider;
import com.pg85.otg.forge.gen.ForgeChunkBuffer;
import com.pg85.otg.forge.gen.OTGNoiseChunkGenerator;
import com.pg85.otg.forge.materials.ForgeMaterialData;
import com.pg85.otg.util.BlockPos2D;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.materials.LocalMaterials;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MapCommand
{
	private static final Object queueLock = new Object();
	private static final Object imgLock = new Object();
	
	protected static int mapBiomes(CommandSource source, int width, int height, int threads)
	{
		if (
			!(source.getLevel().getChunkSource().generator instanceof OTGNoiseChunkGenerator) || 
			!(source.getLevel().getChunkSource().generator.getBiomeSource() instanceof OTGBiomeProvider)
		)
		{
			source.sendSuccess(new StringTextComponent("Please run this command in an OTG world."), false);
			return 1;
		}
		
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		
		Instant start = Instant.now();	
		handleArea(width, height, img, source, (OTGNoiseChunkGenerator)source.getLevel().getChunkSource().generator, (OTGBiomeProvider) source.getLevel().getChunkSource().generator.getBiomeSource(), true, threads);
		Instant finish = Instant.now();
		Duration duration = Duration.between(start, finish); // Note: This is probably the least helpful time duration helper class I've ever seen ...
		
		String fileName = source.getServer().getWorldData().getLevelName() + " biomes.png";        
		Path p = Paths.get(fileName);
		try
		{
			ImageIO.write(img, "png", p.toAbsolutePath().toFile());
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		source.sendSuccess(new StringTextComponent("Finished mapping in " + (duration.toHours() > 9 ? duration.toHours() : "0" + duration.toHours()) + ":" + (duration.toMinutes() % 60 > 9 ? (duration.toMinutes() % 60) : "0" + (duration.toMinutes() % 60)) + ":" + (duration.get(ChronoUnit.SECONDS) % 60 > 9 ? (duration.get(ChronoUnit.SECONDS) % 60) : "0" + (duration.get(ChronoUnit.SECONDS) % 60)) + "! The resulting image is located at " + fileName + "."), true);
		
		return 0;
	}
	
	static int mapTerrain(CommandSource source, int width, int height, int threads)
	{
		if (
			!(source.getLevel().getChunkSource().generator instanceof OTGNoiseChunkGenerator) || 
			!(source.getLevel().getChunkSource().generator.getBiomeSource() instanceof OTGBiomeProvider)
		)
		{
			source.sendSuccess(new StringTextComponent("Please run this command in an OTG world."), false);
			return 1;
		}
		
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		
		Instant start = Instant.now();
		handleArea(width, height, img, source, (OTGNoiseChunkGenerator)source.getLevel().getChunkSource().generator, (OTGBiomeProvider) source.getLevel().getChunkSource().generator.getBiomeSource(), false, threads);
		Instant finish = Instant.now();
		Duration duration = Duration.between(start, finish);		
		
		String fileName = source.getServer().getWorldData().getLevelName() + " terrain.png";        
		Path p = Paths.get(fileName);
		try
		{
			ImageIO.write(img, "png", p.toAbsolutePath().toFile());
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		source.sendSuccess(new StringTextComponent("Finished mapping in " + (duration.toHours() > 9 ? duration.toHours() : "0" + duration.toHours()) + ":" + (duration.toMinutes() % 60 > 9 ? (duration.toMinutes() % 60) : "0" + (duration.toMinutes() % 60)) + ":" + (duration.get(ChronoUnit.SECONDS) % 60 > 9 ? (duration.get(ChronoUnit.SECONDS) % 60) : "0" + (duration.get(ChronoUnit.SECONDS) % 60)) + "! The resulting image is located at " + fileName + "."), true);
		
		return 0;
	}
	
	static void handleArea(int width, int height, BufferedImage img, CommandSource source, OTGNoiseChunkGenerator generator, OTGBiomeProvider provider, boolean mapBiomes, int threads)
	{
		// TODO: Optimise this, List<BlockPos2D> is lazy and handy for having workers pop a task 
		// off a stack until it's empty, ofc it's not efficient or pretty and doesn't scale.
        List<BlockPos2D> coordsToHandle = new ArrayList<BlockPos2D>(width * height);
		for (int chunkX = 0; chunkX < (int)Math.ceil(height / 16f); chunkX++)
		{
			for (int chunkZ = 0; chunkZ < (int)Math.ceil(width / 16f); chunkZ++)
			{
				coordsToHandle.add(new BlockPos2D(chunkX, chunkZ));
			}
		}

        CountDownLatch latch = new CountDownLatch(threads);
        MapCommand outer = new MapCommand();
        int totalSize = coordsToHandle.size();
		for(int i = 0; i < threads; i++)
		{
			outer.new Worker(latch, source, generator, provider, img, coordsToHandle, totalSize, mapBiomes, width, height).start();
		}
		
        try {
        	latch.await();
    	} catch (InterruptedException e) {
        	e.printStackTrace();
		}
	}
	
	static int shadeColor(int rgbColor, int percent)
	{
		int red = (rgbColor >> 16) & 0xFF;
		int green = (rgbColor >> 8) & 0xFF;
		int blue = rgbColor & 0xFF;
		
		red = red * percent / 100;
		red = red > 255 ? 255 : red;
		green = green * percent / 100;
		green = green > 255 ? 255 : green;
		blue = blue * percent / 100;
		blue = blue > 255 ? 255 : blue;

	    return 65536 * red + 256 * green + blue;
	}
	
	public class Worker implements Runnable
	{
		private Thread runner;		
		private final int totalSize;
		private final List<BlockPos2D> coordsToHandle;
	    private final CountDownLatch latch;
	    private final OTGNoiseChunkGenerator generator;
	    private final OTGBiomeProvider provider;
	    private final CommandSource source;
	    private final BufferedImage img;
	    private final int progressUpdate;
	    private final boolean mapBiomes;
	    private final int width;
	    private final int height;

	    public Worker(CountDownLatch latch, CommandSource source, OTGNoiseChunkGenerator generator, OTGBiomeProvider provider, BufferedImage img, List<BlockPos2D> coordsToHandle, int totalSize, boolean mapBiomes, int width, int height)
	    {
	        this.latch = latch;
	        this.generator = generator;
	        this.provider = provider;
	        this.source = source;
	        this.img = img;
	        this.progressUpdate = (int)Math.floor(totalSize / 100f);
	        this.coordsToHandle = coordsToHandle;
	        this.totalSize = totalSize;
	        this.mapBiomes = mapBiomes;
	        this.width = width;
	        this.height = height;
	    }

	    public void start()
	    {
	        this.runner = new Thread(this);
	        this.runner.start();
	    }
	    
	    @Override
	    public void run()
	    {
			//set the color
	    	while(true)
	    	{
	    		BlockPos2D coords = null;
	    		int sizeLeft;
	    		synchronized(queueLock)
	    		{
	    			sizeLeft = coordsToHandle.size();
	    			if(sizeLeft > 0)
	    			{
	    				coords = coordsToHandle.remove(sizeLeft - 1);
	    			}
	    		}
    			// Send a progress update to let people know the server isn't dying
    			if (sizeLeft % progressUpdate == 0)
    			{
    				source.sendSuccess(new StringTextComponent((int)Math.floor(100 - (((double)sizeLeft / totalSize) * 100)) + "% Done mapping"), true);
    			}
	    		
	    		if(coords != null)
	    		{
	    			if(mapBiomes)
	    			{
	    				getBiomePixel(coords);
	    			} else {
	    				getTerrainPixel(coords);
	    			}
	    		} else {
    				latch.countDown();
    				return;
	    		}
	    	}
	    }

	    private void getBiomePixel(BlockPos2D chunkCoords)
	    {		
			for (int internalX = 0; internalX < 16; internalX++)
			{
				for (int internalZ = 0; internalZ < 16; internalZ++)
				{
					int x = chunkCoords.x * 16 + internalX;
					int z = chunkCoords.z * 16 + internalZ;
					if(x <= width && z <= height)
					{
						int biomeId = provider.getSampler().sample(x, z);
						synchronized(imgLock)
						{
							img.setRGB(x, z, provider.configLookup[biomeId].getBiomeColor());
						}
					}
				}
			}
	    }
	    
	    private void getTerrainPixel(BlockPos2D chunkCoords)
	    {
	    	ForgeChunkBuffer chunk = generator.getChunkWithoutLoadingOrCaching(source.getLevel().getRandom(), ChunkCoordinate.fromChunkCoords(chunkCoords.x, chunkCoords.z));
	    	HighestBlockInfo highestBlockInfo;
			for (int internalX = 0; internalX < 16; internalX++)
			{
				for (int internalZ = 0; internalZ < 16; internalZ++)
				{
					int x = chunkCoords.x * 16 + internalX;
					int z = chunkCoords.z * 16 + internalZ;
					if(x <= width && z <= height)
					{
						highestBlockInfo = getHighestBlockInfoInUnloadedChunk(chunk, internalX, internalZ);

						// Color depth relative to waterlevel
						//int worldHeight = 255;
						//int worldWaterLevel = 63;
						//int min = worldWaterLevel - worldHeight;
						//int max = worldWaterLevel + worldHeight;
						// Color depth relative to 0-255
						int min = 0;
						int max = 255;
						int range = max - min;
						int distance = -min + highestBlockInfo.y;
						float relativeDistance = (float)distance / (float)range;
						int shadePercentage = (int)Math.floor(relativeDistance * 2 * 100);
						int rgbColor = shadeColor(highestBlockInfo.material.internalBlock().getBlock().defaultMaterialColor().col, shadePercentage);
						synchronized(imgLock)
						{
							img.setRGB(x, z, rgbColor);
						}
					}
				}
			}	    	
	    }

		private HighestBlockInfo getHighestBlockInfoInUnloadedChunk(ForgeChunkBuffer chunk, int internalX, int internalZ)
		{
			// TODO: Just use heightmaps?
			BlockState blockInChunk;
			for (int y = chunk.getHighestBlockForColumn(internalX, internalZ); y >= 0; y--)
			{
				blockInChunk = chunk.getChunk().getBlockState(new BlockPos(internalX, y, internalZ));
				if (blockInChunk != null && blockInChunk.getBlock() != Blocks.AIR)
				{
					return new HighestBlockInfo((ForgeMaterialData)ForgeMaterialData.ofBlockState(blockInChunk), y);					
				}
			}
			return new HighestBlockInfo((ForgeMaterialData)LocalMaterials.AIR, 63);
		}
	}
		
	public class HighestBlockInfo
	{
		public final ForgeMaterialData material;
		public final int y;
		
		public HighestBlockInfo(ForgeMaterialData material, int y)
		{
			this.material = material;
			this.y = y;
		}
	}
}
