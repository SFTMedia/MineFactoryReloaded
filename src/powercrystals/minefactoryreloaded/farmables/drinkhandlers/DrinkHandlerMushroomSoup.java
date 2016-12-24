package powercrystals.minefactoryreloaded.farmables.drinkhandlers;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import powercrystals.minefactoryreloaded.api.ILiquidDrinkHandler;

public class DrinkHandlerMushroomSoup implements ILiquidDrinkHandler
{
	@Override
	public void onDrink(EntityLivingBase player)
	{
		player.heal(4);
		player.addPotionEffect(new PotionEffect(MobEffects.HEALTH_BOOST, 5 * 20, 1));
		player.addPotionEffect(new PotionEffect(MobEffects.SATURATION, 15 * 20, 2));
	}
}
