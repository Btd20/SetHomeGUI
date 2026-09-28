package org.sethomegui.Listeners;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Utils.MenuActions;
import org.sethomegui.Utils.Utils;

import java.util.List;

public class MenuClickListener implements Listener {

    private final SetHomeGUI plugin;

    public MenuClickListener(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        String mainTitle = Utils.color(plugin.getGuisConfig().getString("gui.main-gui.title", "Menu"));

        // Comprobamos que esté interactuando con el menú principal
        if (!title.equals(mainTitle)) return;

        event.setCancelled(true); // Evitamos que muevan los ítems

        // Solo procesamos clicks dentro del menú: el inventario del jugador reutiliza la misma numeración de slots
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        // 1 y 2. Obtenemos el ID interno de forma dinámica: primero desde el PersistentDataContainer
        // del ítem y, como respaldo, desde el slot que realmente ocupa en gui.yml.
        // Así el click sigue funcionando aunque el administrador reubique el ítem.
        YamlDocument guiConfig = plugin.getGuisConfig();
        Section mainSection = guiConfig.getSection("gui.main-gui");

        // Los comandos personalizados definidos en gui.yml sustituyen a la acción interna del ítem
        if (MenuActions.execute(plugin, player, mainSection, event.getSlot())) {
            plugin.getGuiManager().playConfiguredClickSound(player, "main-gui");
            return;
        }

        String clickedItemKey = Utils.resolveMenuAction(plugin, clickedItem, mainSection, event.getSlot());

        // Si el ítem no tiene ninguna acción asociada (ej: decoración), salimos
        if (clickedItemKey == null) return;

        // 3. Ejecutar las lógicas correspondientes de forma dinámica según la KEY del YAML
        switch (clickedItemKey) {

            case "set_home":
                plugin.getGuiManager().playConfiguredClickSound(player, "main-gui");

                // --- 1. COMPROBACIÓN DE MUNDO EN LISTA NEGRA ---
                String currentWorld = player.getWorld().getName();
                List<String> blacklistedWorlds = plugin.getMainConfig().getStringList("blacklisted-worlds");

                if (blacklistedWorlds != null && blacklistedWorlds.contains(currentWorld)) {
                    String worldMsg = plugin.getMainConfig().getString("messages.home-creation-messages.world-blacklisted",
                            "&#ef6603[SetHomeGUI] &cYou cannot set a home in the world &f%world%&#ef6603!");

                    worldMsg = worldMsg.replace("%world%", "&f" + currentWorld);
                    player.sendMessage(Utils.setPlaceholders(player, worldMsg, plugin));
                    player.closeInventory();
                    return;
                }

                // --- COMPROBACIÓN DE LÍMITE DE HOGARES ---
                int currentHomes = plugin.getHomeManager().getHomeCount(player.getUniqueId());
                int maxHomes = plugin.getHomeManager().getPlayerMaxHomes(player);

                if (maxHomes != -1 && currentHomes >= maxHomes) {
                    String limitMsg = plugin.getMainConfig().getString("messages.home-creation-messages.limit-reached",
                            "&#ef6603[SetHomeGUI] &cYou have reached your maximum limit of &f%max% &#ef6603homes!");

                    limitMsg = limitMsg.replace("%max%", "&f" + maxHomes);
                    player.sendMessage(Utils.setPlaceholders(player, limitMsg, plugin));
                    player.closeInventory();
                    return;
                }

                // Cerramos el menú e iniciamos el prompt con reemplazo de placeholders
                player.closeInventory();

                String basePath = "messages.home-creation-messages.";
                String cancelWord = plugin.getMainConfig().getString(basePath + "cancel-word", "cancel");

                String promptMsg = plugin.getMainConfig().getString(basePath + "name-prompt",
                        "&#ef6603[SetHomeGUI] &#f9a805Please type the name for your home in the chat... Or type &f%cancel_word% &#f9a805to exit.");

                promptMsg = promptMsg.replace("%cancel_word%", "&f" + cancelWord);
                player.sendMessage(Utils.setPlaceholders(player, promptMsg, plugin));

                ChatPromptListener.startPrompt(player);
                break;

            case "my_homes":
                plugin.getGuiManager().playConfiguredClickSound(player, "main-gui");
                player.closeInventory();

                // Abrimos el menú paginado de hogares de forma dinámica
                plugin.getGuiManager().openHomesGUI(player);
                break;

            default:
                // Cualquier otra key (como paneles decorativos) no hace nada al clickearse
                break;
        }
    }
}