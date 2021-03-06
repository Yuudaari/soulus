package yuudaari.soulus.common.block.composer;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import yuudaari.soulus.common.block.upgradeable_block.UpgradeableBlockTileEntity;

public abstract class HasRenderItemTileEntity extends UpgradeableBlockTileEntity {

	@SideOnly(Side.CLIENT)
	public abstract double getPrevItemRotation ();

	@SideOnly(Side.CLIENT)
	public abstract double getItemRotation ();

	@SideOnly(Side.CLIENT)
	public abstract ItemStack getStoredItem ();

	@SideOnly(Side.CLIENT)
	public boolean shouldComplexRotate () {
		return false;
	}

	@SideOnly(Side.CLIENT)
	public double getSpinSpeed () {
		return 0;
	}

	@SideOnly(Side.CLIENT)
	public double getSwingSpeed () {
		return 0;
	}
}
