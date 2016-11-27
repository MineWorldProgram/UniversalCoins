package universalcoins.tileentity;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import universalcoins.UniversalCoins;

import java.util.Objects;
import java.util.UUID;

public class TileProtected extends TileEntity {

	public UUID blockOwnerId = null;
	public UUID playerId = null;
	public String blockOwner = "none";
	public String playerName = "";
	public boolean inUse = false;

	@Override
	public void readFromNBT(NBTTagCompound tagCompound) {
		super.readFromNBT(tagCompound);
		try {
			inUse = tagCompound.getBoolean("InUse");
		} catch (Throwable ex2) {
			inUse = false;
		}
		try {
			playerName = tagCompound.getString("playerName");
		} catch (Throwable ex2) {
			playerName = "none";
		}
		if (tagCompound.hasUniqueId("playerId")) {
			playerId = tagCompound.getUniqueId("playerId");
		} else if (!playerName.isEmpty() && !playerName.equals("none")) {
			playerId = findUUID(playerName);
			if (playerId != null)
				markDirty();
		}
		try {
			blockOwner = tagCompound.getString("blockOwner");
		} catch (Throwable ex2) {
			blockOwner = "none";
		}
		if(tagCompound.hasUniqueId("blockOwnerId")) {
			blockOwnerId = tagCompound.getUniqueId("blockOwnerId");
		} else if (!blockOwner.isEmpty() && !blockOwner.equals("none")) {
			blockOwnerId = findUUID(blockOwner);
			if (blockOwnerId != null)
				markDirty();
		}
	}

	public static GameProfile findProfile(String playerName) {
		return UniversalCoins.server.getPlayerProfileCache().getGameProfileForUsername(playerName);
	}

	public static UUID findUUID(String playerName, TileEntity tile) {
		GameProfile profile = findProfile(playerName);
		if (profile == null) {
			System.err.println("Failed to find the "+playerName+"'s UUID! POS:"+tile.getPos()+" DIM:"+tile.getWorld());
			return null;
		} else {
			return profile.getId();
		}
	}

	public final UUID findUUID(String playerName) {
		return findUUID(playerName, this);
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
		super.writeToNBT(tagCompound);
		tagCompound.setBoolean("InUse", inUse);
		tagCompound.setString("playerName", playerName);
		tagCompound.setString("blockOwner", blockOwner);
		if(playerId != null) {
			tagCompound.setUniqueId("playerId", playerId);
		} else {
			tagCompound.removeTag("playerId");
		}
		if(blockOwnerId != null) {
			tagCompound.setUniqueId("blockOwnerId", blockOwnerId);
		} else {
			tagCompound.removeTag("blockOwnerId");
		}
		return tagCompound;
	}

	//@Override
	public SPacketUpdateTileEntity getUpdatePacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		writeToNBT(nbt);
		return new SPacketUpdateTileEntity(pos, 1, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
		readFromNBT(pkt.getNbtCompound());
	}
}
