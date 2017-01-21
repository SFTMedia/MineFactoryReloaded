package powercrystals.minefactoryreloaded.block.transport;

import cofh.lib.util.helpers.BlockHelper;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleDigging;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import powercrystals.minefactoryreloaded.MFRRegistry;
import powercrystals.minefactoryreloaded.MineFactoryReloadedCore;
import powercrystals.minefactoryreloaded.api.rednet.IRedNetInputNode;
import powercrystals.minefactoryreloaded.api.rednet.connectivity.RedNetConnectionType;
import powercrystals.minefactoryreloaded.block.BlockFactory;
import powercrystals.minefactoryreloaded.core.IEntityCollidable;
import powercrystals.minefactoryreloaded.core.IRotateableTile;
import powercrystals.minefactoryreloaded.core.MFRUtil;
import powercrystals.minefactoryreloaded.gui.MFRCreativeTab;
import powercrystals.minefactoryreloaded.item.ItemPlasticBoots;
import powercrystals.minefactoryreloaded.setup.MFRThings;
import powercrystals.minefactoryreloaded.tile.transport.TileEntityConveyor;

public class BlockConveyor extends BlockFactory implements IRedNetInputNode {

	//TODO replace with use of EnumDyeColor
	public static final String[] _names = { "white", "orange", "magenta", "lightblue", "yellow", "lime",
			"pink", "gray", "lightgray", "cyan", "purple", "blue", "brown", "green", "red", "black", "default" };
	private static final int[] colors = new int[17];
	static {
		for (int i = 16; i-- > 0;)
			colors[i] = MFRUtil.COLORS[i];
		colors[16] = 0xf6a82c;
	}

	public static final PropertyEnum<ConveyorDirection> DIRECTION = PropertyEnum.create("direction", ConveyorDirection.class);
	public static final PropertyEnum<Speed> SPEED = PropertyEnum.create("speed", Speed.class);
	private static final AxisAlignedBB CONVEYOR_COLLISION_AABB = new AxisAlignedBB(0.125D, 0.0D, 0.125D, 0.875D, 0.01D, 0.875D);
	private static final AxisAlignedBB CONVEYOR_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.125D, 1.0D);
	private static final AxisAlignedBB HILL_CONVEYOR_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.125D, 1.0D);
	private static final AxisAlignedBB CONVEYOR_SELECTION_AABB = new AxisAlignedBB(0.05D, 0.0D, 0.05D, 0.95D, 0.1D, 0.95D);
	private static final AxisAlignedBB HILL_CONVEYOR_SELECTION_AABB = new AxisAlignedBB(0.1D, 0.0D, 0.1D, 0.9D, 0.1D, 0.9D);

	public BlockConveyor() {

		super(Material.CIRCUITS);
		setHardness(0.5F);
		setUnlocalizedName("mfr.conveyor");
		setCreativeTab(MFRCreativeTab.tab);
	}

	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {

		ConveyorDirection direction = state.getValue(DIRECTION);
		return direction.isUphill() || direction.isDownhill() ? HILL_CONVEYOR_AABB : CONVEYOR_AABB;
	}

	@Override
	public boolean isSideSolid(IBlockState base_state, IBlockAccess world, BlockPos pos, EnumFacing side) {

		return false;
	}

	@Override
	protected BlockStateContainer createBlockState() {

		return new BlockStateContainer(this, DIRECTION, SPEED);
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {

		return getDefaultState().withProperty(DIRECTION, ConveyorDirection.byMetadata(meta));
	}

	@Override
	public int getMetaFromState(IBlockState state) {

		return state.getValue(DIRECTION).getMetadata();
	}

	@Override
	public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {

		TileEntity te = world.getTileEntity(pos);
		if (te instanceof TileEntityConveyor) {
			TileEntityConveyor conveyor = (TileEntityConveyor) te;
			Speed speed = Speed.STOPPED;
			if (conveyor.getConveyorActive()) {
				speed = conveyor.isFast() ? Speed.FAST : Speed.SLOW;
			}
			state = state.withProperty(SPEED, speed);
		}
		return state;
	}

	@Override
	public BlockRenderLayer getBlockLayer() {

		return BlockRenderLayer.CUTOUT;
	}

	@Override
	public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, ItemStack stack) {
		
		return getDefaultState().withProperty(DIRECTION, ConveyorDirection.byFacing(placer.getHorizontalFacing()));
	}

	@Override
	public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase entity, ItemStack stack) {

		super.onBlockPlacedBy(world, pos, state, entity, stack);
		if (entity == null) {
			return;
		}

		TileEntity te = world.getTileEntity(pos);
		if (te instanceof TileEntityConveyor) {
			((TileEntityConveyor) te).setDyeColor(stack.getItemDamage() == 16 ? null : EnumDyeColor.byMetadata(stack.getItemDamage()));
		}
	}

	@Override
	public void onBlockAdded(World world, BlockPos pos, IBlockState state) {

		neighborChanged(state, world, pos, this);
	}

	@Override
	public void onEntityCollidedWithBlock(World world, BlockPos pos, IBlockState state, Entity entity) {

		boolean isItem = entity instanceof EntityItem || entity instanceof EntityXPOrb;
		if(!isItem)
			for(Class<?> blacklist : MFRRegistry.getConveyerBlacklist())
				if(blacklist.isInstance(entity))
					return;

		if(!(isItem || entity instanceof EntityLivingBase || entity instanceof EntityTNTPrimed))
			return;

		TileEntity conveyor = world.getTileEntity(pos);
		if(!(conveyor instanceof TileEntityConveyor && ((TileEntityConveyor) conveyor).getConveyorActive()))
			return;

		if(!world.isRemote) {
			if(entity instanceof EntityItem)
				specialRoute(world, pos, (EntityItem) entity);
			else if(entity instanceof EntityPlayer)
				return;
		}

		if(entity instanceof EntityLivingBase)
			l:{
				ItemStack item = ((EntityLivingBase) entity).getItemStackFromSlot(EntityEquipmentSlot.FEET);
				if(item == null)
					break l;
				if(item.getItem() instanceof ItemPlasticBoots)
					return;
			}

		if(entity.getEntityData().getLong("mfr:conveyor") == world.getTotalWorldTime()) {
			return;
		}
		entity.getEntityData().setLong("mfr:conveyor", world.getTotalWorldTime());

		double mult = ((TileEntityConveyor) conveyor).isFast() ? 2.1 : 1.05;
		mult *= world.getBlockState(pos.down()).getBlock().slipperiness;

		double xVelocity = 0;
		double yVelocity = 0;
		double zVelocity = 0;

		ConveyorDirection direction = state.getValue(DIRECTION);

		switch(direction.getFacing()) {
			case EAST:
				xVelocity = 0.1D * mult;
				break;
			case SOUTH:
				zVelocity = 0.1D * mult;
				break;
			case WEST:
				xVelocity = -0.1D * mult;
				break;
			case NORTH:
				zVelocity = -0.1D * mult;
				break;
		}

		if(direction.isUphill()) {
			yVelocity = 0.152D * mult;
			double yO;
			if(xVelocity != 0) {
				yO = Math.abs(entity.getEntityBoundingBox().maxX - entity.getEntityBoundingBox().minX) / 2;
				yO = MathHelper.clamp_double(Math.abs(entity.posX - pos.getX() + (direction.getFacing() == EnumFacing.WEST ? 1 : 0))
						+ Math.abs(xVelocity) + yO, 0, 1);
			} else {
				yO = Math.abs(entity.getEntityBoundingBox().maxZ - entity.getEntityBoundingBox().minZ) / 2;
				yO = MathHelper.clamp_double(Math.abs(entity.posZ - pos.getZ() + (direction.getFacing() == EnumFacing.NORTH ? 1 : 0))
						+ Math.abs(zVelocity) + yO, 0, 1);
			}
			setYPos(entity, pos.getY() + yO + .1);
		} else if((entity.posY - pos.getY() < 0.1) && entity.posY - pos.getY() > -0.1) {
			setYPos(entity, pos.getY() + .1);
		} else if(direction.isDownhill()) {
			yVelocity = -0.11 * mult;
			entity.fallDistance -= .13;
		}

		if(direction.isUphill() || direction.isDownhill()) {
			entity.onGround = false;
			entity.motionY = yVelocity / 2;
		}

		repositionEntity(world, pos, entity, xVelocity, yVelocity, zVelocity);

		l:
		{
			if(direction.isUphill()) {
				if(entity.posY < pos.getY() + 1)
					break l;
			} else
				switch(direction) {
					case EAST:
						if(entity.posX < pos.getX() + 1)
							break l;
						break;
					case WEST:
						if(entity.posX > pos.getX())
							break l;
						break;
					case SOUTH:
						if(entity.posZ < pos.getZ() + 1)
							break l;
						break;
					case NORTH:
						if(entity.posZ > pos.getZ())
							break l;
						break;
				}
			if(!BlockHelper.getAdjacentBlock(world, pos, direction.getFacing()).getBlock().equals(MFRThings.conveyorBlock)) {
				if(direction.isUphill() | direction.isDownhill()) {
					double d = .25;
					if(!BlockHelper.getAdjacentBlock(world, pos.add(0, direction.getYOffset(), 0), direction.getFacing()).equals(MFRThings.conveyorBlock)) {
						d = 1;
					}
					entity.motionY = yVelocity * d;
					entity.motionX = xVelocity * d;
					entity.motionZ = zVelocity * d;
				} else {
					entity.motionX = xVelocity;
					entity.motionZ = zVelocity;
				}
			}
		}

		entity.fallDistance *= 0.9;
		if(entity instanceof EntityItem) {
			((EntityItem) entity).setPickupDelay(40);
		}
	}

	private void setYPos(Entity ent, double y) {

		double xT = ent.lastTickPosX, yT = ent.lastTickPosY, zT = ent.lastTickPosZ;
		if (ent instanceof EntityLivingBase) {
			ent.setPositionAndUpdate(ent.posX, y, ent.posZ);
		} else {
			ent.setLocationAndAngles(ent.posX, y, ent.posZ, ent.rotationYaw, ent.rotationPitch);
		}
		ent.lastTickPosX = xT;
		ent.lastTickPosY = yT;
		ent.lastTickPosZ = zT;
	}

	private void repositionEntity(World world, BlockPos pos, Entity ent, double xO, double yO, double zO) {

			if (!world.getCollisionBoxes(ent.getEntityBoundingBox()).isEmpty() || !world.getCollisionBoxes(ent.getEntityBoundingBox().offset(xO, yO, zO)).isEmpty()) {
				return;
			}
			if (isZero(ent.motionX) && isZero(ent.motionZ)) {
				if (xO == 0)
					xO += (pos.getX() - (ent.posX - .5)) / 20;
				if (zO == 0)
					zO += (pos.getZ() - (ent.posZ - .5)) / 20;
			} else {
				xO += ent.motionX;
				zO += ent.motionZ;
			}
			double eY = yO != 0 ? ent.prevPosY : ent.posY;
			double xT = ent.lastTickPosX, yT = ent.lastTickPosY, zT = ent.lastTickPosZ;
			if (ent instanceof EntityLivingBase) {
				ent.setPositionAndUpdate(ent.prevPosX + xO, eY + yO, ent.prevPosZ + zO);
			} else {
				ent.setLocationAndAngles(ent.prevPosX + xO, eY + yO, ent.prevPosZ + zO, ent.rotationYaw, ent.rotationPitch);
			}
			ent.lastTickPosX = xT;
			ent.lastTickPosY = yT;
			ent.lastTickPosZ = zT;
			if (yO != 0) {
				ent.motionY = 0;
			}
			ent.motionX *= .5;
			ent.motionZ *= .5;
	}

	public static boolean isZero(double x) {

		return -.025 <= x & x <= .025;
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBox(IBlockState state, World world, BlockPos pos) {

		return CONVEYOR_COLLISION_AABB;
	}

	@Override
	public AxisAlignedBB getSelectedBoundingBox(IBlockState state, World worldIn, BlockPos pos) {

		ConveyorDirection direction = state.getValue(DIRECTION);
		return direction.isUphill() || direction.isDownhill() ? HILL_CONVEYOR_SELECTION_AABB : CONVEYOR_SELECTION_AABB;
	}

	@Override
	public boolean isOpaqueCube(IBlockState state) {

		return false;
	}

	@Override
	public boolean isNormalCube(IBlockState state) {

		return false;
	}

	@Override
	public boolean isFullCube(IBlockState state) {

		return false;
	}

	@Override
	public boolean canPlaceBlockAt(World world, BlockPos pos) {

		return canBlockStay(world, pos);
	}

	public boolean canBlockStay(World world, BlockPos pos) {

		return world.isSideSolid(pos.down(), EnumFacing.UP);
	}

	@Override
	protected boolean activated(World world, BlockPos pos, EntityPlayer player, EnumFacing side, EnumHand hand, ItemStack heldItem) {

		if (MFRUtil.isHoldingUsableTool(player, pos)) {
			TileEntity te = world.getTileEntity(pos);
			if (te instanceof IRotateableTile) {
				((IRotateableTile) te).rotate(side);
			}
			MFRUtil.usedWrench(player, pos);
			return true;
		} else if (heldItem != null && heldItem.getItem().equals(Items.GLOWSTONE_DUST)) {
			TileEntity te = world.getTileEntity(pos);
			if (te instanceof TileEntityConveyor && !((TileEntityConveyor) te).isFast()) {
				((TileEntityConveyor) te).setFast(true);
				MFRUtil.notifyBlockUpdate(world, pos);
				if (!player.capabilities.isCreativeMode)
					heldItem.stackSize--;
				return true;
			}
		}
		return false;
	}

	@Override
	public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block) {

		if (!canBlockStay(world, pos)) {
			dropBlockAsItem(world, pos, world.getBlockState(pos), 0);
			world.setBlockToAir(pos);
			return;
		}

		TileEntity tec = world.getTileEntity(pos);
		if (tec instanceof TileEntityConveyor) {
			((TileEntityConveyor) tec).updateConveyorActive();
		}
	}

	@Override
	public boolean hasTileEntity(IBlockState state) {

		return true;
	}

	@Override
	public TileEntity createTileEntity(World world, IBlockState state) {

		return new TileEntityConveyor();
	}

	@Override
	public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {

		return getItemFromBlock(world, pos);
	}

	private ItemStack getItemFromBlock(IBlockAccess world, BlockPos pos) {
		TileEntity te = world.getTileEntity(pos);
		int meta = 16;
		if (te instanceof TileEntityConveyor) {
			EnumDyeColor dyeColor = ((TileEntityConveyor) te).getDyeColor();
			meta = dyeColor == null ? 16 : dyeColor.getMetadata();
		}

		return new ItemStack(this, 1, meta);
	}

	@Override
	public ArrayList<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {

		ArrayList<ItemStack> ret = new ArrayList<ItemStack>();
		if (world.getBlockState(pos).getBlock().equals(this)) {
			ret.add(getItemFromBlock(world, pos));
			if (((TileEntityConveyor) world.getTileEntity(pos)).isFast())
				ret.add(new ItemStack(Items.GLOWSTONE_DUST, 1));
		}
		return ret;
	}

	@Override
	public boolean canProvidePower(IBlockState state) {

		return false;
	}

	// IRedNetOmniNode
	@Override
	public RedNetConnectionType getConnectionType(World world, BlockPos pos, EnumFacing side) {

		return RedNetConnectionType.PlateSingle;
	}

	@Override
	public void onInputsChanged(World world, BlockPos pos, EnumFacing side, int[] inputValues) {

	}

	@Override
	public void onInputChanged(World world, BlockPos pos, EnumFacing side, int inputValue) {

		TileEntity te = world.getTileEntity(pos);
		if (te instanceof TileEntityConveyor) {
			((TileEntityConveyor) te).onRedNetChanged(inputValue);
		}
	}

	private void specialRoute(World world, BlockPos pos, EntityItem entityitem) {

		TileEntity teBelow = world.getTileEntity(pos.down());
		if (teBelow == null || entityitem.isDead) {
			return;
		} else if (teBelow instanceof IEntityCollidable) {
			((IEntityCollidable) teBelow).onEntityCollided(entityitem);
		} else if (teBelow instanceof TileEntityHopper) {
			if (!((TileEntityHopper) teBelow).isOnTransferCooldown()) {
				ItemStack toInsert = entityitem.getEntityItem().copy();
				toInsert.stackSize = 1;
				toInsert = TileEntityHopper.putStackInInventoryAllSlots((IInventory) teBelow, toInsert, EnumFacing.UP);
				if (toInsert == null) {
					entityitem.getEntityItem().stackSize--;
					((TileEntityHopper) teBelow).setTransferCooldown(8);
				}
			}
		}
		if (entityitem.getEntityItem().stackSize <= 0) {
			entityitem.setDead();
		}
	}

	public enum ConveyorDirection implements IStringSerializable {

		EAST(0, "east", EnumFacing.EAST, 0),
		SOUTH(1, "south", EnumFacing.SOUTH, 0),
		WEST(2, "west", EnumFacing.WEST, 0),
		NORTH(3, "north", EnumFacing.NORTH, 0),
		ASCENDING_EAST(4, "ascending_east", EnumFacing.EAST, 1),
		ASCENDING_SOUTH(5, "ascending_south", EnumFacing.SOUTH, 1),
		ASCENDING_WEST(6, "ascending_west", EnumFacing.WEST, 1),
		ASCENDING_NORTH(7, "ascending_north", EnumFacing.NORTH, 1),
		DESCENDING_EAST(8, "descending_east", EnumFacing.EAST, -1),
		DESCENDING_SOUTH(9, "descending_south", EnumFacing.SOUTH, -1),
		DESCENDING_WEST(10, "descending_west", EnumFacing.WEST, -1),
		DESCENDING_NORTH(11, "descending_north", EnumFacing.NORTH, -1);

		static {

			EAST.reverse = WEST;
			SOUTH.reverse = NORTH;
			WEST.reverse = EAST;
			NORTH.reverse = SOUTH;
			ASCENDING_EAST.reverse = DESCENDING_WEST;
			ASCENDING_SOUTH.reverse = DESCENDING_NORTH;
			ASCENDING_WEST.reverse = DESCENDING_EAST;
			ASCENDING_NORTH.reverse = DESCENDING_SOUTH;
			DESCENDING_EAST.reverse = ASCENDING_WEST;
			DESCENDING_SOUTH.reverse = ASCENDING_NORTH;
			DESCENDING_WEST.reverse = ASCENDING_EAST;
			DESCENDING_NORTH.reverse = ASCENDING_SOUTH;
		}

		private final int meta;
		private final String name;
		private final EnumFacing facing;
		private int yOffset;
		private ConveyorDirection reverse;

		private static final ConveyorDirection[] META_LOOKUP = new ConveyorDirection[values().length];

		ConveyorDirection(int meta, String name, EnumFacing facing, int yOffset) {

			this.meta = meta;
			this.name = name;
			this.facing = facing;
			this.yOffset = yOffset;
		}

		@Override
		public String getName() {

			return name;
		}

		public EnumFacing getFacing() {

			return facing;
		}

		public boolean isUphill() {

			return yOffset > 0;
		}

		public boolean isDownhill() {

			return yOffset < 0;
		}

		public int getMetadata() {

			return this.meta;
		}

		public static ConveyorDirection byMetadata(int meta) {

			if (meta < 0 || meta >= META_LOOKUP.length)
			{
				meta = 0;
			}

			return META_LOOKUP[meta];
		}

		public ConveyorDirection getReverse() {
			return reverse;
		}

		public static ConveyorDirection byFacing(EnumFacing facing) {

			for (ConveyorDirection conveyorDirection : values()) {
				if (conveyorDirection.yOffset == 0 && conveyorDirection.facing == facing) {
					return conveyorDirection;
				}
			}
			return EAST;
		}

		static {
			for (ConveyorDirection conveyorDirection : values()) {
				META_LOOKUP[conveyorDirection.getMetadata()] = conveyorDirection;
			}
		}

		public int getYOffset() {
			return yOffset;
		}
	}

	public enum Speed implements IStringSerializable {

		STOPPED("stopped"),
		SLOW("slow"),
		FAST("fast");

		private String name;

		Speed(String name) {

			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}
	}


}
