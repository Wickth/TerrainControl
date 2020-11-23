package com.pg85.otg.gen.carver;

import java.util.BitSet;
import java.util.Random;

import com.pg85.otg.util.gen.ChunkBuffer;
import com.pg85.otg.util.helpers.MathHelper;
import com.pg85.otg.util.materials.LocalMaterials;

public abstract class Carver
{
	protected final int heightLimit;

	public Carver(int heightLimit)
	{
		this.heightLimit = heightLimit;
	}

	public int getBranchFactor()
	{
		return 4;
	}

	protected boolean carveRegion(ChunkBuffer chunk, long seed, int seaLevel, int chunkX, int chunkZ, double x, double y, double z, double yaw, double pitch, BitSet carvingMask)
	{
		Random random = new Random(seed + (long) chunkX + (long) chunkZ);
		double d = chunkX * 16 + 8;
		double e = chunkZ * 16 + 8;
		if (x >= d - 16.0D - yaw * 2.0D && z >= e - 16.0D - yaw * 2.0D && x <= d + 16.0D + yaw * 2.0D && z <= e + 16.0D + yaw * 2.0D)
		{
			int i = Math.max(MathHelper.floor(x - yaw) - chunkX * 16 - 1, 0);
			int j = Math.min(MathHelper.floor(x + yaw) - chunkX * 16 + 1, 16);
			int k = Math.max(MathHelper.floor(y - pitch) - 1, 1);
			int l = Math.min(MathHelper.floor(y + pitch) + 1, this.heightLimit - 8);
			int m = Math.max(MathHelper.floor(z - yaw) - chunkZ * 16 - 1, 0);
			int n = Math.min(MathHelper.floor(z + yaw) - chunkZ * 16 + 1, 16);
			if (this.isRegionUncarvable(chunk, chunkX, chunkZ, i, j, k, l, m, n))
			{
				return false;
			} else
			{
				boolean bl = false;

				for (int o = i; o < j; ++o)
				{
					int p = o + chunkX * 16;
					double f = ((double) p + 0.5D - x) / yaw;

					for (int q = m; q < n; ++q)
					{
						int r = q + chunkZ * 16;
						double g = ((double) r + 0.5D - z) / yaw;
						if (f * f + g * g < 1.0D)
						{

							for (int s = l; s > k; --s)
							{
								double h = ((double) s - 0.5D - y) / pitch;
								if (!this.isPositionExcluded(f, h, g, s))
								{
									bl |= this.carveAtPoint(chunk, carvingMask, random, seaLevel, chunkX, chunkZ, p, r, o, s, q);
								}
							}
						}
					}
				}

				return bl;
			}
		} else
		{
			return false;
		}
	}

	protected boolean carveAtPoint(ChunkBuffer chunk, BitSet carvingMask, Random random, int seaLevel, int mainChunkX, int mainChunkZ, int x, int z, int relativeX, int y, int relativeZ)
	{
		int i = relativeX | relativeZ << 4 | y << 8;
		if (carvingMask.get(i))
		{
			return false;
		} else
		{
			carvingMask.set(i);

			// TODO: top layer searching
			if (y < 11)
			{
				chunk.setBlock(x, y, z, LocalMaterials.LAVA);
			} else
			{
				// TODO: should be cave_air
				chunk.setBlock(x, y, z, LocalMaterials.AIR);
			}

			return true;
		}
	}

	protected boolean isRegionUncarvable(ChunkBuffer chunk, int mainChunkX, int mainChunkZ, int relMinX, int relMaxX, int minY, int maxY, int relMinZ, int relMaxZ)
	{
		for (int i = relMinX; i < relMaxX; ++i)
		{
			for (int j = relMinZ; j < relMaxZ; ++j)
			{
				for (int k = minY - 1; k <= maxY + 1; ++k)
				{
					if (chunk.getBlock(i + mainChunkX * 16, k, j + mainChunkZ * 16).isMaterial(LocalMaterials.WATER))
					{
						return true;
					}

					if (k != maxY + 1 && !this.isOnBoundary(relMinX, relMaxX, relMinZ, relMaxZ, i, j))
					{
						k = maxY;
					}
				}
			}
		}

		return false;
	}

	private boolean isOnBoundary(int minX, int maxX, int minZ, int maxZ, int x, int z)
	{
		return x == minX || x == maxX - 1 || z == minZ || z == maxZ - 1;
	}

	protected boolean canCarveBranch(int mainChunkX, int mainChunkZ, double x, double z, int branch, int branchCount, float baseWidth)
	{
		double d = mainChunkX * 16 + 8;
		double e = mainChunkZ * 16 + 8;
		double f = x - d;
		double g = z - e;
		double h = branchCount - branch;
		double i = baseWidth + 2.0F + 16.0F;
		return f * f + g * g - h * h <= i * i;
	}

	public abstract boolean carve(ChunkBuffer chunk, Random random, int seaLevel, int chunkX, int chunkZ, int mainChunkX, int mainChunkZ, BitSet carvingMask);

	public abstract boolean shouldCarve(Random random, int chunkX, int chunkZ);

	protected abstract boolean isPositionExcluded(double scaledRelativeX, double scaledRelativeY, double scaledRelativeZ, int y);
}