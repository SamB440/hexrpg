package me.cheesyfreezy.hexrpg.rpg.items.applicable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import me.cheesyfreezy.hexrpg.rpg.tools.Feature;
import me.cheesyfreezy.hexrpg.tools.ConfigFile;
import me.cheesyfreezy.hexrpg.tools.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import me.cheesyfreezy.hexrpg.rpg.items.RPGAttributeType;
import me.cheesyfreezy.hexrpg.rpg.items.applicable.scroll.RPGScroll;
import me.cheesyfreezy.hexrpg.rpg.items.applicable.socketeffect.RPGEffectSocket;
import me.cheesyfreezy.hexrpg.rpg.items.combatitem.RPGCombatItem;
import me.cheesyfreezy.hexrpg.rpg.mechanics.EffectSocketService;
import me.cheesyfreezy.hexrpg.rpg.tools.ItemType;

public class ApplicableService<T> {
	private Map<ApplicableType, Map<Object, Applicable<?, T>>> singletons = new HashMap<>();
	private Map<ApplicableType, Map<Object, Predicate<T>>> predicates = new HashMap<>();
	
	@SuppressWarnings("unchecked")
	public void register() {
		if(Feature.getFeature("scrolls").isEnabled()) {
			ConfigFile scrollsConfig = ConfigFile.getConfig("scrolls.yml");
			for(String scrollKey : scrollsConfig.getRootKeys()) {
				String cPath = scrollKey + ".";

				double successRate = scrollsConfig.getDouble(cPath + "rates.success");
				double destroyRate = scrollsConfig.getDouble(cPath + "rates.destroy");

				String displayName = ChatColor.translateAlternateColorCodes('&', scrollsConfig.getString(cPath + "appearance.display-name"));
				ArrayList<String> baseLore = new ArrayList<>();
				for(String loreLine : scrollsConfig.getStringList(cPath + "appearance.lore")) {
					baseLore.add(ChatColor.translateAlternateColorCodes('&', loreLine));
				}

				Map<RPGAttributeType, Object> modifiers = new HashMap<>();
				for(String modifierKey : scrollsConfig.getConfigurationSection(cPath + "attribute-changes").getKeys(false)) {
					RPGAttributeType attributeType = null;
					try {
						attributeType = RPGAttributeType.valueOf(modifierKey.toUpperCase().replace("-", "_"));
					} catch(IllegalArgumentException e) {
						Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "An error occurred while trying to register the scrolls attribute changes for scroll '" + scrollKey + "'. Please check your scrolls.yml to see if the attributes are named correctly.");
						Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "(modifierKey=" + modifierKey + ",scrollKey=" + scrollKey + ")");
						break;
					}

					modifiers.put(attributeType, scrollsConfig.getObject(cPath + "attribute-changes." + modifierKey));
				}

				List<String> canBeAppliedOnKeys = new ArrayList<>(scrollsConfig.getConfigurationSection(cPath + "can-be-applied-on").getKeys(false));
				Predicate<RPGCombatItem>[] predicateConditions = (Predicate<RPGCombatItem>[]) new Predicate<?>[canBeAppliedOnKeys.size()];
				for(int i = 0; i < canBeAppliedOnKeys.size(); i++) {
					String canBeAppliedOnKey = canBeAppliedOnKeys.get(i);

					switch(canBeAppliedOnKey) {
						case "armor":
							predicateConditions[i] = (item) -> !ItemType.isArmor(item.getMaterial()) || ItemType.isArmor(item.getMaterial()) == scrollsConfig.getBoolean(cPath + "can-be-applied-on." + canBeAppliedOnKey);
							break;
						case "weapon":
							predicateConditions[i] = (item) -> !ItemType.isWeapon(item.getMaterial()) || ItemType.isWeapon(item.getMaterial()) == scrollsConfig.getBoolean(cPath + "can-be-applied-on." + canBeAppliedOnKey);
							break;
						case "arrow":
							predicateConditions[i] = (item) -> !ItemType.isArrow(item.getMaterial()) || ItemType.isArrow(item.getMaterial()) == scrollsConfig.getBoolean(cPath + "can-be-applied-on." + canBeAppliedOnKey);
							break;
						case "identified-item":
							predicateConditions[i] = (item) -> item.isIdentified() == scrollsConfig.getBoolean(cPath + "can-be-applied-on." + canBeAppliedOnKey);
							break;
						case "unidentified-item":
							predicateConditions[i] = (item) -> !item.isIdentified() == scrollsConfig.getBoolean(cPath + "can-be-applied-on." + canBeAppliedOnKey);
							break;
					}
				}

				addReference(ApplicableType.SCROLL, (Applicable<?, T>) new RPGScroll(scrollKey, successRate, destroyRate, displayName, baseLore, modifiers), (item) -> {
					for(Predicate<RPGCombatItem> predicate : predicateConditions) {
						if(!predicate.test((RPGCombatItem) item)) {
							return false;
						}
					}
					return true;
				});
			}
		}

		if(Feature.getFeature("effect-sockets").isEnabled()) {
			ConfigFile effectSocketConfigFile = ConfigFile.getConfig(EffectSocketService.FILE_NAME);
			for(String effectSocketKey : effectSocketConfigFile.getRootKeys()) {
				try {
					addReference(ApplicableType.EFFECT_SOCKET, (Applicable<?, T>) new RPGEffectSocket(effectSocketKey, LanguageManager.getMessageList("applicables.effect-socket-lore", null, true)), (item) -> ((RPGCombatItem) item).isIdentified());
				} catch(NoSuchFieldError e) {
					Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "The applicable '" + effectSocketKey + "' is invalid. Please check your effect_sockets.yml file or ask the developer for help.");
				}
			}
		}
	}
	
	public void addReference(ApplicableType type, Applicable<?, T> applicable, Predicate<T> predicate) {
		if(!singletons.containsKey(type)) {
			singletons.put(type, new HashMap<>());
		}
		
		if(!predicates.containsKey(type)) {
			predicates.put(type, new HashMap<>());
		}
		
		singletons.get(type).put(applicable.getSubType(), applicable);
		predicates.get(type).put(applicable.getSubType(), predicate);
	}
	
	public Applicable<?, T> getReference(ApplicableType type, Object subType) {
		for(Applicable<?, T> specificSingletonValue : singletons.get(type).values()) {
			if(specificSingletonValue.getSubType().equals(subType)) {
				return specificSingletonValue;
			}
		}
		
		return null;
	}
	
	public Predicate<T> getCondition(Applicable<Object, T> applicable) {
		return predicates.get(applicable.getType()).get(applicable.getSubType());
	}

	public Map<Object, Applicable<?, T>> getSingletons(ApplicableType type) {
		return singletons.get(type);
	}
}