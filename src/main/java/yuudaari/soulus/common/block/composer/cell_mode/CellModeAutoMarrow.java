package yuudaari.soulus.common.block.composer.cell_mode;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import yuudaari.soulus.Soulus;
import yuudaari.soulus.common.advancement.Advancements;
import yuudaari.soulus.common.block.composer.ComposerCellTileEntity;
import yuudaari.soulus.common.block.upgradeable_block.UpgradeableBlockTileEntity;
import yuudaari.soulus.common.config.ConfigInjected;
import yuudaari.soulus.common.config.ConfigInjected.Inject;
import yuudaari.soulus.common.config.block.ConfigComposerCell;
import yuudaari.soulus.common.config.bones.ConfigBoneType;
import yuudaari.soulus.common.config.bones.ConfigBoneTypes;
import yuudaari.soulus.common.misc.BoneChunks;
import yuudaari.soulus.common.registration.ItemRegistry;
import yuudaari.soulus.common.util.ItemStackMutable;
import yuudaari.soulus.common.util.Range;
import yuudaari.soulus.common.util.Translation;

/**
 * Auto-marrow
 */
@ConfigInjected(Soulus.MODID)
public class CellModeAutoMarrow extends ComposerCellTileEntity.Mode {

	@Inject public static ConfigComposerCell CONFIG;
	@Inject public static ConfigBoneTypes CONFIG_BONE_TYPES;

	@Nullable public String storedChunkType;
	public int storedChunkQuantity;
	public int ticks = 0;

	@Override
	public String getName () {
		return "auto_marrow";
	}

	private ItemStack getChunkStack () {
		final ConfigBoneType config = storedChunkType == null || storedChunkQuantity <= 0 ? null : CONFIG_BONE_TYPES.get(storedChunkType);
		return config == null ? ItemStack.EMPTY : config.getChunkStack();
	}

	@Override
	public boolean isActive () {
		return !cell.isConnected() //
			&& cell.storedItem != null && cell.storedItem.getItem() == ItemRegistry.GEAR_OSCILLATING //
			&& cell.storedQuantity > 0 && cell.storedQuantity <= CONFIG.autoMarrowMaxOscillatingGears;
	}

	@Override
	public boolean isLockingStoredItem () {
		return storedChunkType != null && storedChunkQuantity > 0;
	}

	@Override
	public int getMaxContainedQuantityForOtherModes (final ItemStack stack) {
		if (!isLockingStoredItem())
			return super.getMaxContainedQuantityForOtherModes(stack);

		return CONFIG.autoMarrowMaxOscillatingGears;
	}

	@Override
	public boolean tryInsert (final ItemStackMutable stack, final int requestedQuantity, final boolean isPulling) {
		final Item boneChunk = stack.getItem();
		final ConfigBoneType boneType = CONFIG_BONE_TYPES.getFromChunk(boneChunk.getRegistryName().toString());
		if (boneType == null)
			return false;

		if (storedChunkType != null && storedChunkType != boneType.name)
			return false;

		if (storedChunkType == null)
			ticks = 0;

		storedChunkType = boneType.name;

		final int count = Math.min(requestedQuantity, CONFIG.autoMarrowMaxChunkBuffer - storedChunkQuantity);
		storedChunkQuantity += count;
		stack.shrink(count);

		cell.blockUpdate();

		return true;
	}

	@Override
	public boolean tryExtract (final List<ItemStack> extracted) {
		if (storedChunkType == null || storedChunkQuantity <= 0)
			return false;

		ComposerCellTileEntity.addItemStackToList(getChunkStack(), extracted, storedChunkQuantity);
		storedChunkQuantity = 0;
		storedChunkType = null;
		return true;
	}

	@Override
	public void update () {
		if (storedChunkType == null || storedChunkQuantity <= 0)
			return;

		ticks++;

		final World world = cell.getWorld();
		final BlockPos pos = cell.getPos();

		final ConfigBoneType boneType = CONFIG_BONE_TYPES.get(storedChunkType);
		if (boneType == null)
			return;

		final int particleTime = (int) ((1 - cell.storedQuantity / (double) CONFIG.autoMarrowMaxOscillatingGears) * 8);
		if (particleTime == 0 || ticks % particleTime == 0)
			ComposerCellTileEntity.itemParticles(world, pos, Item.getIdFromItem(boneType.getChunkItem()), 1);

		final double autoMarrowChunksPerTick = CONFIG.autoMarrowTicksPerChunkPerOscillatingGear.get(cell.storedQuantity / (double) CONFIG.autoMarrowMaxOscillatingGears);

		if (ticks < autoMarrowChunksPerTick)
			// we haven't reached marrow time yet
			return;

		if (ticks % 2 > 0)
			// we only update every two ticks no matter what it's set to, for efficiency
			return;

		final int marrowQuantity = (int) Math.min(ticks / autoMarrowChunksPerTick, storedChunkQuantity);
		final Collection<ItemStack> results = BoneChunks.getMarrowingDrops(world.rand, storedChunkType, marrowQuantity);

		ticks = 0;
		storedChunkQuantity -= marrowQuantity;
		if (storedChunkQuantity <= 0)
			storedChunkType = null;

		for (final ItemStack resultStack : results)
			UpgradeableBlockTileEntity.dispenseItem(resultStack, world, pos, EnumFacing.DOWN);

		world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_GRAVEL_HIT, SoundCategory.BLOCKS, 0.5F + 0.5F * (float) world.rand
			.nextInt(2), (world.rand.nextFloat() - world.rand.nextFloat()) * 0.2F + 1.0F);

		ComposerCellTileEntity.itemParticles(world, pos, Item.getIdFromItem(boneType.getChunkItem()), marrowQuantity);

		// update tooltip
		cell.blockUpdate();

		Advancements.COMPOSER_CELL_AUTO_MARROW_TRIGGER.trigger(cell.getOwner(), null);
	}


	////////////////////////////////////
	// NBT
	//

	@Override
	public void onWriteToNBT (final NBTTagCompound compound) {
		compound.setInteger("stored_chunk_quantity", storedChunkQuantity);
		if (storedChunkQuantity > 0 && storedChunkType != null)
			compound.setString("stored_chunk_item", storedChunkType);

		compound.setInteger("auto_marrow_ticks", ticks);
	}

	@Override
	public void onReadFromNBT (final NBTTagCompound compound) {
		storedChunkQuantity = compound.getInteger("stored_chunk_quantity");
		storedChunkType = storedChunkQuantity <= 0 ? null : compound.getString("stored_chunk_item");

		ticks = compound.getInteger("auto_marrow_ticks");
	}


	////////////////////////////////////
	// Rendering
	//

	private static final Range SPIN_SPEED = new Range(10, 160);

	@Override
	public double getSpinSpeed () {
		return SPIN_SPEED.get(cell.storedQuantity / (double) CONFIG.autoMarrowMaxOscillatingGears);
	}


	////////////////////////////////////
	// Tooltip
	//

	@Override
	public void onWailaTooltipHeader (final List<String> currentTooltip, final EntityPlayer player) {
		currentTooltip.add(new Translation("waila." + Soulus.MODID + ":composer_cell.auto_marrow_contained_gears")
			.addArgs(cell.storedQuantity, CONFIG.autoMarrowMaxOscillatingGears, ItemRegistry.GEAR_OSCILLATING.getItemStack().getDisplayName())
			.get());

		if (storedChunkType == null || storedChunkQuantity <= 0)
			currentTooltip.add(Translation.localize("waila." + Soulus.MODID + ":composer_cell.auto_marrow"));
		else
			currentTooltip.add(new Translation("waila." + Soulus.MODID + ":composer_cell.auto_marrow_contained_chunks")
				.addArgs(storedChunkQuantity, CONFIG.autoMarrowMaxChunkBuffer, getChunkStack().getDisplayName())
				.get());
	}

	@Override
	public boolean allowRenderingItemInTooltip () {
		return false;
	}
}
