package cofh.cobblehaters;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.potion.Potion;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.event.entity.player.PlayerOpenContainerEvent;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;

@Mod(modid = "CblH8Rs", name = "Cobble Haters", version = "1.0.1.0", dependencies = "")
public class CobbleHaters {

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {

		blacklist = Arrays.asList(Item.getItemFromBlock(Blocks.cobblestone),
			Item.getItemFromBlock(Blocks.mossy_cobblestone),
			Items.stone_pickaxe, Items.stone_axe, Items.stone_shovel,
			Items.stone_hoe, Items.stone_sword);
		Items.stone_pickaxe.setHarvestLevel("pickaxe", -1);
		Items.stone_axe.setHarvestLevel("axe", -1);
		Items.stone_shovel.setHarvestLevel("shovel", -1);
		MinecraftForge.EVENT_BUS.register(this);
	}

	@EventHandler
	public void loadComplete(FMLLoadCompleteEvent event) {

		FurnaceRecipes.smelting().getSmeltingList().clear();
	}

	private List<Item> blacklist;

	private boolean isBad(ItemStack stack) {

		return stack != null && blacklist.contains(stack.getItem());
	}

	private TObjectIntHashMap<WeakReference<EntityPlayer>> containers = new TObjectIntHashMap<WeakReference<EntityPlayer>>();

	@SubscribeEvent
	public void stopRareDrops(LivingDeathEvent evt) {

		if (!(evt.entity instanceof EntityLiving))
			return;
		EntityLiving ent = (EntityLiving)evt.entity;
		for (int i = 5; i --> 0; )
			if (ent.equipmentDropChances[i] < 1.0f) {
				ent.equipmentDropChances[i] = 0;
			}
	}

	@SubscribeEvent
	public void eatCobble(PlayerOpenContainerEvent evt) {

		if (evt.entityPlayer.openContainer == evt.entityPlayer.inventoryContainer)
			return;
		int window = evt.entityPlayer.openContainer.windowId;
		if (containers.get(new WeakReference<EntityPlayer>(evt.entityPlayer)) == window)
			return;
		ItemStack[] inventory = evt.entityPlayer.inventory.mainInventory;
		for (int i = inventory.length; i --> 0; ) {
			if (isBad(inventory[i]))
				inventory[i] = null;
		}
	}

	@SubscribeEvent
	public void eatCobble(HarvestDropsEvent evt) {

		Iterator<ItemStack> i = evt.drops.iterator();
		while (i.hasNext()) {
			ItemStack stack = i.next();
			if (isBad(stack))
				i.remove();
		}
	}

	@SuppressWarnings("unchecked")
	@SubscribeEvent
	public void eatCobble(EntityJoinWorldEvent evt) {

		if (evt.entity.getClass().equals(EntityItem.class)) {
			EntityItem ent = (EntityItem)evt.entity;
			ItemStack stack = ent.getEntityItem();
			if (isBad(stack))
				ent.setDead();
		} else if (evt.entity instanceof EntityVillager) {
			EntityVillager ent = (EntityVillager)evt.entity;
			MerchantRecipeList list = ent.getRecipes(null);
			list.clear();
			list.add(new MerchantRecipe(new ItemStack(Blocks.cobblestone), null, new ItemStack(Items.wheat_seeds)));
		}
	}

	@SubscribeEvent
	public void stopCobble(PlayerInteractEvent evt) {

		if (evt.action != Action.RIGHT_CLICK_BLOCK)
			return;
		ItemStack stack = evt.entityPlayer.getCurrentEquippedItem();
		if (stack != null && blacklist.contains(stack.getItem())) {
			evt.entityPlayer.destroyCurrentEquippedItem();
			evt.useItem = Result.DENY;
		}
	}

	@SubscribeEvent
	public void silkStone(BreakSpeed evt) {

		if (!evt.block.equals(Blocks.stone))
			return;

		EntityPlayer entity = evt.entityPlayer;
		if (EnchantmentHelper.getSilkTouchModifier(entity))
			return;
		float f = 1.0f;


		if (entity.isPotionActive(Potion.digSpeed)) {
			f *= 1.0F + (entity.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2F;
		}

		if (entity.isPotionActive(Potion.digSlowdown)) {
			f *= 1.0F - (entity.getActivePotionEffect(Potion.digSlowdown).getAmplifier() + 1) * 0.2F;
		}

		if (entity.isInsideOfMaterial(Material.water) && !EnchantmentHelper.getAquaAffinityModifier(entity)) {
			f /= 5.0F;
		}

		if (!entity.onGround) {
			f /= 5.0F;
		}

		if (ForgeHooks.canHarvestBlock(evt.block, entity, evt.metadata))
			f /= 4;
		evt.newSpeed = f;
	}

}
