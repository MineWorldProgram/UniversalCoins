package com.notabadminer.universalcoins.items;

import java.text.DecimalFormat;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ItemCoin extends Item {
	
	public ItemCoin() {
		super();
		setCreativeTab(com.notabadminer.universalcoins.UniversalCoins.tabUniversalCoins);
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list,
			boolean bool) {
		DecimalFormat formatter = new DecimalFormat("###,###,###");
		list.add(formatter.format(stack.stackSize)
				+ (stack.stackSize > 1 ? " Coins" : " Coin")); // TODO localization
	}
	
}
