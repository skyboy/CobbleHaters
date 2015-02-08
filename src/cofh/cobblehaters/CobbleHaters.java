package cofh.cobblehaters;

import com.google.common.base.Predicate;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameData;

import gnu.trove.map.hash.TObjectIntHashMap;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.potion.Potion;
import net.minecraft.util.WeightedRandomFishable;
import net.minecraftforge.common.FishingHooks;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerOpenContainerEvent;
import net.minecraftforge.event.world.BlockEvent.HarvestDropsEvent;

import org.apache.logging.log4j.Logger;

@Mod(modid = "CblH8Rs", name = "Cobble Haters", version = "2.0.2.0", dependencies = "")
public class CobbleHaters {

	Logger log;
	TObjectIntHashMap<WeakReference<EntityPlayer>> containers = new TObjectIntHashMap<WeakReference<EntityPlayer>>();
	ArrayList<Item> blacklist = new ArrayList<Item>();
	ArrayList<Item> destroylist = new ArrayList<Item>();
	ArrayList<Block> harvestlist = new ArrayList<Block>();
	HashMap<Class<?>, ArrayList<Item>> entitylist = new HashMap<Class<?>, ArrayList<Item>>();
	Configuration config;
	boolean stopTrades;
	boolean destroyItems;
	boolean blacklistPlace;
	boolean equipmentDrops;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {

		MinecraftForge.EVENT_BUS.register(this);
		log = event.getModLog();
		config = new Configuration(event.getSuggestedConfigurationFile());

		if (config.get("general", "DisableStoneToolEffectiveness", true).getBoolean()) {
			Items.stone_pickaxe.setHarvestLevel("pickaxe", -1);
			Items.stone_axe.setHarvestLevel("axe", -1);
			Items.stone_shovel.setHarvestLevel("shovel", -1);
		}

		stopTrades = config.get("general", "DisableVillagerTrades", true).getBoolean();
		destroyItems = config.get("general", "DestroyItemsOnGuiChange", true).getBoolean();
		blacklistPlace = config.get("general", "BlockBlacklistPlacement", true).getBoolean();
		equipmentDrops = config.get("general", "EquipmentDrops", false).getBoolean();

		config.get("entity_drops", IMob.class.getName(), new String[] {
				"iron_ingot", "gold_nugget"
		});
		config.get("entity_drops", EntityIronGolem.class.getName(), new String[] {
			"iron_ingot"
		});
	}

	@EventHandler
	public void loadComplete(FMLLoadCompleteEvent event) {

		/**
		 * Block drop destruction
		 */
		String[] list = new String[] {
				"cobblestone", "mossy_cobblestone", "stone_pickaxe", "stone_axe",
				"stone_shovel", "stone_hoe", "stone_sword"
		};
		list = config.get("general", "Blacklist", list, "These items will not drop from blocks").getStringList();
		for (String e : list) {
			Item item = GameData.getItemRegistry().getObject(e);
			if (item != null)
				blacklist.add(item);
			else
				log.warn("blacklist entry %s not found", e);
		}

		/**
		 * Item erasure (shares default value)
		 */
		list = config.get("general", "DestroyItems", list, "These items will be destroyed when found").getStringList();
		for (String e : list) {
			Item item = GameData.getItemRegistry().getObject(e);
			if (item != null)
				destroylist.add(item);
			else
				log.warn("destroyItems entry %s not found", e);
		}

		/**
		 * Mining speed reduction
		 */
		list = new String[] {
				"stone", "cobblestone", "mossy_cobblestone", "furnace", "lit_furnace"
		};
		list = config.get("general", "HarvestBlocks", list, "These blocks require silk touch to be mined quickly")
				.getStringList();
		for (String e : list) {
			Block item = GameData.getBlockRegistry().getObject(e);
			if (item != Blocks.air)
				harvestlist.add(item);
			else
				log.warn("harvestBlocks entry %s not found", e);
		}

		/**
		 * Mob drop destruction
		 */
		ConfigCategory droplist = config.getCategory("entity_drops");
		droplist.setComment("Entries in this category are used to stop entities from listed dropping items\n\n" +
				"Format: S:<class.name> = <array of entries>");
		for (Entry<String, Property> e : droplist.entrySet()) {
			try {
				ArrayList<Item> value = new ArrayList<Item>();
				for (String a : e.getValue().getStringList()) {
					Item item = GameData.getItemRegistry().getObject(a);
					if (item != null)
						value.add(item);
					else
						log.warn("entity_drops blacklist entry %s not found", a);
				}
				Class<?> clazz = Class.forName(e.getKey(), false, CobbleHaters.class.getClassLoader());
				entitylist.put(clazz, value);
			} catch (Throwable t) {
				log.warn("Class %s not found for entity_drops category", e.getKey());
				continue;
			}
		}

		/**
		 * Smelting disable
		 */
		if (config.get("general", "DisableSmelting", true).getBoolean())
			FurnaceRecipes.smelting().getSmeltingList().clear();

		if (config.get("general", "DisableNonfishFishing", true).getBoolean()) {
			FishingHooks.removeTreasure(new Predicate<WeightedRandomFishable>() {
				@Override
				public boolean apply(WeightedRandomFishable input) {

					return false;
				}
			});
			FishingHooks.removeJunk(new Predicate<WeightedRandomFishable>() {
				@Override
				public boolean apply(WeightedRandomFishable input) {

					return false;
				}
			});
		}

		config.save();
	}

	private boolean isBad(ItemStack stack) {

		return stack != null && blacklist.contains(stack.getItem());
	}

	private boolean shouldDestroy(ItemStack stack) {

		return stack != null && destroylist.contains(stack.getItem());
	}

	@SubscribeEvent(priority=EventPriority.LOWEST)
	public void stopEquipment(LivingDeathEvent evt) {

		if (equipmentDrops || !(evt.entity instanceof EntityLiving))
			return;
		EntityLiving ent = (EntityLiving) evt.entity;
		for (int i = 5; i-- > 0;)
			if (ent.equipmentDropChances[i] < 1.0f) {
				ent.equipmentDropChances[i] = 0;
			}
	}

	@SubscribeEvent(priority=EventPriority.LOWEST)
	public void stopDrops(LivingDropsEvent evt) {

		if (entitylist.size() == 0)
			return;
		for (Entry<Class<?>, ArrayList<Item>> e : entitylist.entrySet()) {
			if (e.getKey().isInstance(evt.entity)) {
				ArrayList<Item> list = e.getValue();
				for (Iterator<EntityItem> i = evt.drops.iterator(); i.hasNext();) {
					ItemStack stack = i.next().getEntityItem();
					if (stack != null && list.contains(stack.getItem()))
						i.remove();
				}
			}
		}
	}

	@SubscribeEvent(priority=EventPriority.LOWEST)
	public void eatCobble(PlayerOpenContainerEvent evt) {

		if (!destroyItems || evt.entityPlayer.openContainer == evt.entityPlayer.inventoryContainer)
			return;
		int window = evt.entityPlayer.openContainer.windowId;
		if (containers.get(new WeakReference<EntityPlayer>(evt.entityPlayer)) == window)
			return;
		ItemStack[] inventory = evt.entityPlayer.inventory.mainInventory;
		for (int i = inventory.length; i-- > 0;) {
			if (shouldDestroy(inventory[i]))
				inventory[i] = null;
		}
	}

	@SubscribeEvent(priority=EventPriority.LOWEST)
	public void eatCobble(HarvestDropsEvent evt) {

		for (Iterator<ItemStack> i = evt.drops.iterator(); i.hasNext();) {
			ItemStack stack = i.next();
			if (isBad(stack) || shouldDestroy(stack))
				i.remove();
		}
	}

	@SubscribeEvent(priority=EventPriority.LOWEST)
	public void eatCobble(EntityJoinWorldEvent evt) {

		if (evt.entity.getClass().equals(EntityItem.class)) {
			EntityItem ent = (EntityItem) evt.entity;
			ItemStack stack = ent.getEntityItem();
			if (shouldDestroy(stack))
				ent.setDead();
		}
	}

	@SubscribeEvent(priority=EventPriority.HIGHEST)
	public void stopTrade(EntityInteractEvent evt) {

		if (stopTrades && evt.target instanceof EntityVillager) {
			((EntityVillager) evt.target).func_110297_a_(null);
			((EntityVillager) evt.target).setRevengeTarget(evt.entityLiving);
			evt.setCanceled(true);
		}
	}

	@SubscribeEvent(priority=EventPriority.HIGHEST)
	public void stopCobble(PlayerInteractEvent evt) {

		ItemStack stack = evt.entityPlayer.getCurrentEquippedItem();
		if (blacklistPlace && isBad(stack)) {
			if (shouldDestroy(stack))
				evt.entityPlayer.destroyCurrentEquippedItem();
			evt.useItem = Result.DENY;
		}
	}

	@SubscribeEvent(priority=EventPriority.LOWEST)
	public void silkStone(BreakSpeed evt) {

		if (!harvestlist.contains(evt.block))
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
