package universalcoins.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.util.Constants;
import universalcoins.UniversalCoins;
import universalcoins.net.ATMWithdrawalMessage;
import universalcoins.net.UCButtonMessage;
import universalcoins.util.UniversalAccounts;

public class TileATM extends TileEntity implements IInventory, ISidedInventory {
	private NonNullList<ItemStack> inventory = NonNullList.withSize(2, ItemStack.EMPTY);
	public static final int itemCoinSlot = 0;
	public static final int itemCardSlot = 1;
	public String blockOwner = "";
	public String playerName = "";
	public String playerUID = "";
	public boolean inUse = false;
	public boolean depositCoins = false;
	public boolean withdrawCoins = false;
	public boolean accountError = false;
	public int coinWithdrawalAmount = 0;
	public String cardOwner = "";
	public String accountNumber = "none";
	public long accountBalance = 0;

	public void inUseCleanup() {
		if (world.isRemote)
			return;
		inUse = false;
		withdrawCoins = false;
		depositCoins = false;
		accountNumber = "none";
		accountBalance = 0;
		updateTE();
	}

	@Override
	public int getSizeInventory() {
		return inventory.size();
	}

	@Override
	public ItemStack getStackInSlot(int slot) {
		if (slot >= inventory.size()) {
			return ItemStack.EMPTY;
		}
		return inventory.get(slot);
	}

	@Override
	public ItemStack decrStackSize(int slot, int size) {
		ItemStack stack = getStackInSlot(slot);
		if (!stack.isEmpty()) {
			if (stack.getCount() <= size) {
				setInventorySlotContents(slot, ItemStack.EMPTY);
			} else {
				stack = stack.splitStack(size);
				if (stack.getCount() == 0) {
					setInventorySlotContents(slot, ItemStack.EMPTY);
				}
			}
		}
		fillCoinSlot();
		return stack;
	}

	// @Override
	public ItemStack getStackInSlotOnClosing(int slot) {
		if (!this.inventory.get(slot).isEmpty()) {
			ItemStack itemstack = this.inventory.get(slot);
			this.inventory.set(slot, ItemStack.EMPTY);
			return itemstack;
		} else {
			return ItemStack.EMPTY;
		}
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		inventory.set(slot, stack);
		int coinValue = 0;
		if (!stack.isEmpty()) {
			if (slot == itemCoinSlot && depositCoins && !accountNumber.matches("none")) {
				switch (stack.getUnlocalizedName()) {
				case "item.iron_coin":
					coinValue = UniversalCoins.coinValues[0];
					break;
				case "item.gold_coin":
					coinValue = UniversalCoins.coinValues[1];
					break;
				case "item.emerald_coin":
					coinValue = UniversalCoins.coinValues[2];
					break;
				case "item.diamond_coin":
					coinValue = UniversalCoins.coinValues[3];
					break;
				case "item.obsidian_coin":
					coinValue = UniversalCoins.coinValues[4];
					break;
				}
				long depositAmount = Math.min(stack.getCount(), (Long.MAX_VALUE - accountBalance) / coinValue);
				if (!world.isRemote) {
					UniversalAccounts.getInstance().creditAccount(accountNumber, depositAmount * coinValue);
					accountBalance = UniversalAccounts.getInstance().getAccountBalance(accountNumber);
				}
				inventory.get(slot).setCount((int)(inventory.get(slot).getCount() - depositAmount));
				if (inventory.get(slot).getCount() == 0) {
					inventory.set(slot, ItemStack.EMPTY);
				}
			}
			if (slot == itemCardSlot && !world.isRemote) {
				if (!inventory.get(itemCardSlot).hasTagCompound()) {
					return;
				}
				accountNumber = inventory.get(itemCardSlot).getTagCompound().getString("Account");
				cardOwner = inventory.get(itemCardSlot).getTagCompound().getString("Owner");
				accountBalance = UniversalAccounts.getInstance().getAccountBalance(accountNumber);
			}
		}
	}

	@Override
	public String getName() {
		return UniversalCoins.proxy.atm.getLocalizedName();
	}

	@Override
	public boolean hasCustomName() {
		return false;
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	public void sendButtonMessage(int functionID, boolean shiftPressed) {
		UniversalCoins.snw
				.sendToServer(new UCButtonMessage(pos.getX(), pos.getY(), pos.getZ(), functionID, shiftPressed));
	}


	@Override
	public SPacketUpdateTileEntity getUpdatePacket() {
		return new SPacketUpdateTileEntity(this.pos, getBlockMetadata(), getUpdateTag());
	}

	// required for sync on chunk load
	public NBTTagCompound getUpdateTag() {
		NBTTagCompound nbt = new NBTTagCompound();
		writeToNBT(nbt);
		return nbt;
	}

	public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
		readFromNBT(pkt.getNbtCompound());
	}

	public void sendServerUpdatePacket(int withdrawalAmount) {
		UniversalCoins.snw.sendToServer(new ATMWithdrawalMessage(pos.getX(), pos.getY(), pos.getZ(), withdrawalAmount));
	}

	public void updateTE() {
		markDirty();
		world.notifyBlockUpdate(getPos(), world.getBlockState(pos), world.getBlockState(pos), 3);
	}

	@Override
	public void readFromNBT(NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);

		NBTTagList tagList = tagCompound.getTagList("Inventory", Constants.NBT.TAG_COMPOUND);
		for (int i = 0; i < tagList.tagCount(); i++) {
			NBTTagCompound tag = (NBTTagCompound) tagList.getCompoundTagAt(i);
			byte slot = tag.getByte("Slot");
			if (slot >= 0 && slot < inventory.size()) {
				inventory.set(slot, new ItemStack(tag));
			}
		}
		try {
			blockOwner = tagCompound.getString("BlockOwner");
		} catch (Throwable ex2) {
			blockOwner = null;
		}
		try {
			inUse = tagCompound.getBoolean("InUse");
		} catch (Throwable ex2) {
			inUse = false;
		}
		try {
			depositCoins = tagCompound.getBoolean("DepositCoins");
		} catch (Throwable ex2) {
			depositCoins = false;
		}
		try {
			withdrawCoins = tagCompound.getBoolean("WithdrawCoins");
		} catch (Throwable ex2) {
			withdrawCoins = false;
		}
		try {
			coinWithdrawalAmount = tagCompound.getInteger("CoinWithdrawalAmount");
		} catch (Throwable ex2) {
			coinWithdrawalAmount = 0;
		}
		try {
			cardOwner = tagCompound.getString("CardOwner");
		} catch (Throwable ex2) {
			cardOwner = "";
		}
		try {
			accountNumber = tagCompound.getString("accountNumber");
		} catch (Throwable ex2) {
			accountNumber = "none";
		}
		try {
			accountBalance = tagCompound.getLong("accountBalance");
		} catch (Throwable ex2) {
			accountBalance = 0;
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		super.writeToNBT(tagCompound);
		NBTTagList itemList = new NBTTagList();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.get(i);
			NBTTagCompound tag = new NBTTagCompound();
			tag.setByte("Slot", (byte) i);
			stack.writeToNBT(tag);
			itemList.appendTag(tag);
		}
		tagCompound.setTag("Inventory", itemList);
		tagCompound.setString("BlockOwner", blockOwner);
		tagCompound.setBoolean("InUse", inUse);
		tagCompound.setBoolean("DepositCoins", depositCoins);
		tagCompound.setBoolean("WithdrawCoins", withdrawCoins);
		tagCompound.setInteger("CoinWithdrawalAmount", coinWithdrawalAmount);
		tagCompound.setString("CardOwner", cardOwner);
		tagCompound.setString("accountNumber", accountNumber);
		tagCompound.setLong("accountBalance", accountBalance);

		return tagCompound;
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer entityplayer) {
		return world.getTileEntity(pos) == this
				&& entityplayer.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64;
	}

	@Override
	public boolean isItemValidForSlot(int var1, ItemStack var2) {
		return true;
	}

	public void onButtonPressed(int functionId) {
		if (world.isRemote)
			return;
		accountError = false; // reset error state
		// handle function IDs sent from CardStationGUI
		// function1 - new card
		// function2 - transfer account
		// function3 - deposit
		// function4 - withdraw
		// function5 - get account info
		// function6 - destroy invalid card
		if (functionId == 1) {
			accountNumber = UniversalAccounts.getInstance().getOrCreatePlayerAccount(playerUID);
			inventory.set(itemCardSlot, new ItemStack(UniversalCoins.proxy.uc_card, 1));
			inventory.get(itemCardSlot).setTagCompound(new NBTTagCompound());
			inventory.get(itemCardSlot).getTagCompound().setString("Name", playerName);
			inventory.get(itemCardSlot).getTagCompound().setString("Owner", playerUID);
			inventory.get(itemCardSlot).getTagCompound().setString("Account", accountNumber);
			accountBalance = UniversalAccounts.getInstance().getAccountBalance(accountNumber);
			cardOwner = playerUID;
		}
		if (functionId == 2) {
			if (!(UniversalAccounts.getInstance().getPlayerAccount(playerUID).matches(""))) {
				UniversalAccounts.getInstance().transferPlayerAccount(playerUID);
				inventory.set(itemCardSlot, new ItemStack(UniversalCoins.proxy.uc_card, 1));
				inventory.get(itemCardSlot).setTagCompound(new NBTTagCompound());
				inventory.get(itemCardSlot).getTagCompound().setString("Name", playerName);
				inventory.get(itemCardSlot).getTagCompound().setString("Owner", playerUID);
				inventory.get(itemCardSlot).getTagCompound().setString("Account",
						UniversalAccounts.getInstance().getPlayerAccount(playerUID));
				accountBalance = UniversalAccounts.getInstance().getAccountBalance(accountNumber);
				cardOwner = playerUID;
			}
		}
		if (functionId == 3) {
			// set to true if player presses deposit button, reset on any other
			// button press
			depositCoins = true;
			withdrawCoins = false;
			// set account number if not already set and we have a card present
			if (accountNumber.matches("none") && !inventory.get(itemCardSlot).isEmpty()) {
				accountNumber = inventory.get(itemCardSlot).getTagCompound().getString("Account");
			}
		} else {
			depositCoins = false;
		}
		if (functionId == 4) {
			withdrawCoins = true;
			depositCoins = false;
			fillCoinSlot();
		} else
			withdrawCoins = false;
		if (functionId == 5) {
			String storedAccount = UniversalAccounts.getInstance().getPlayerAccount(playerUID);
			if (!storedAccount.matches("")) {
				accountNumber = storedAccount;
				cardOwner = playerUID; // needed for new card auth
				accountBalance = UniversalAccounts.getInstance().getAccountBalance(accountNumber);
			}
		}
		if (functionId == 6) {
			inventory.set(itemCardSlot, ItemStack.EMPTY);
			inUseCleanup();
		}
	}

	public void startCoinWithdrawal(int amount) {
		if (UniversalAccounts.getInstance().debitAccount(accountNumber, amount)) {
			coinWithdrawalAmount = amount;
			accountBalance = UniversalAccounts.getInstance().getAccountBalance(accountNumber);
		}
	}

	public void fillCoinSlot() {
		if (inventory.get(itemCoinSlot).isEmpty() && coinWithdrawalAmount > 0) {
			if (coinWithdrawalAmount > UniversalCoins.coinValues[4]) {
				inventory.set(itemCoinSlot, new ItemStack(UniversalCoins.proxy.obsidian_coin));
				inventory.get(itemCoinSlot).setCount((int) Math.min(coinWithdrawalAmount / UniversalCoins.coinValues[4],
						64));
				coinWithdrawalAmount -= inventory.get(itemCoinSlot).getCount() * UniversalCoins.coinValues[4];
			} else if (coinWithdrawalAmount > UniversalCoins.coinValues[3]) {
				inventory.set(itemCoinSlot, new ItemStack(UniversalCoins.proxy.diamond_coin));
				inventory.get(itemCoinSlot).setCount((int) Math.min(coinWithdrawalAmount / UniversalCoins.coinValues[3],
						64));
				coinWithdrawalAmount -= inventory.get(itemCoinSlot).getCount() * UniversalCoins.coinValues[3];
			} else if (coinWithdrawalAmount > UniversalCoins.coinValues[2]) {
				inventory.set(itemCoinSlot, new ItemStack(UniversalCoins.proxy.emerald_coin));
				inventory.get(itemCoinSlot).setCount((int) Math.min(coinWithdrawalAmount / UniversalCoins.coinValues[2],
						64));
				coinWithdrawalAmount -= inventory.get(itemCoinSlot).getCount() * UniversalCoins.coinValues[2];
			} else if (coinWithdrawalAmount > UniversalCoins.coinValues[1]) {
				inventory.set(itemCoinSlot, new ItemStack(UniversalCoins.proxy.gold_coin));
				inventory.get(itemCoinSlot).setCount((int) Math.min(coinWithdrawalAmount / UniversalCoins.coinValues[1],
						64));
				coinWithdrawalAmount -= inventory.get(itemCoinSlot).getCount() * UniversalCoins.coinValues[1];
			} else if (coinWithdrawalAmount > UniversalCoins.coinValues[0]) {
				inventory.set(itemCoinSlot, new ItemStack(UniversalCoins.proxy.iron_coin));
				inventory.get(itemCoinSlot).setCount((int) Math.min(coinWithdrawalAmount / UniversalCoins.coinValues[0],
						64));
				coinWithdrawalAmount -= inventory.get(itemCoinSlot).getCount() * UniversalCoins.coinValues[0];
			}
		}
		if (coinWithdrawalAmount <= 0) {
			withdrawCoins = false;
			coinWithdrawalAmount = 0;
		}
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack itemStack : inventory) {
			if (itemStack != null && !itemStack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ITextComponent getDisplayName() {
		return new TextComponentString(UniversalCoins.proxy.atm.getLocalizedName());
	}

	@Override
	public int[] getSlotsForFace(EnumFacing side) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean canInsertItem(int index, ItemStack itemStackIn, EnumFacing direction) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ItemStack removeStackFromSlot(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void openInventory(EntityPlayer player) {
		// TODO Auto-generated method stub

	}

	@Override
	public void closeInventory(EntityPlayer player) {
		// TODO Auto-generated method stub

	}

	@Override
	public int getField(int id) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setField(int id, int value) {
		// TODO Auto-generated method stub

	}

	@Override
	public int getFieldCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub

	}
}