package universalcoins;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import universalcoins.proxy.CommonProxy;

public class UCTab extends CreativeTabs {

	private ItemStack stack = new ItemStack(CommonProxy.tradestation);

	public UCTab(String label) {
		super(label);
	}

	@Override
	public ItemStack getTabIconItem() {
		return stack;
	}

}
