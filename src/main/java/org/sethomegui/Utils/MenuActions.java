package org.sethomegui.Utils;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.sethomegui.SetHomeGUI;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ejecuta las acciones personalizadas (comandos) que un administrador puede definir
 * sobre CUALQUIER ítem de CUALQUIER menú del plugin desde gui.yml.
 *
 * Ejemplo de uso en gui.yml:
 * <pre>
 *   server-menu:
 *     type: NETHER_STAR
 *     slot: 4
 *     display-name: "&amp;aVolver al menú principal"
 *     close-inventory: true
 *     commands:
 *       - "[player] menu"
 *       - "[console] say %player_name% ha vuelto al menú"
 * </pre>
 *
 * Si un ítem define comandos, estos SUSTITUYEN a su acción interna (back, next_page...).
 */
public final class MenuActions {

    private static final String CONSOLE_PREFIX = "[console]";
    private static final String PLAYER_PREFIX = "[player]";

    private MenuActions() {
    }

    /**
     * Busca en la configuración del menú el ítem que ocupa realmente el slot pulsado.
     * Admite tanto 'slot' como 'slots'.
     */
    public static Section findItemSection(Section menuSection, int clickedSlot) {
        if (menuSection == null) return null;

        Section itemsSection = menuSection.getSection("items");
        if (itemsSection == null) return null;

        for (Object keyObj : itemsSection.getKeys()) {
            Section itemData = itemsSection.getSection(String.valueOf(keyObj));
            if (itemData == null) continue;

            if (itemData.contains("slot") && itemData.getInt("slot") == clickedSlot) {
                return itemData;
            }

            if (itemData.contains("slots")) {
                List<Integer> slots = itemData.getIntList("slots");
                if (slots != null && slots.contains(clickedSlot)) {
                    return itemData;
                }
            }
        }
        return null;
    }

    /**
     * Ejecuta los comandos personalizados del ítem pulsado, si los tiene definidos.
     *
     * @return true si el ítem definía comandos, en cuyo caso el click ya está atendido
     *         y el menú NO debe seguir procesando su acción interna.
     */
    public static boolean execute(SetHomeGUI plugin, Player player, Section menuSection, int clickedSlot) {
        Section itemSection = findItemSection(menuSection, clickedSlot);
        if (itemSection == null) return false;

        List<String> commands = readCommands(itemSection);
        if (commands.isEmpty()) return false;

        // Cerramos el menú antes de ejecutar para que otro plugin pueda abrir el suyo sin conflicto.
        if (itemSection.getBoolean("close-inventory", true)) {
            player.closeInventory();
        }

        for (String command : commands) {
            dispatch(plugin, player, command);
        }
        return true;
    }

    /**
     * Lee la lista 'commands' o, en su defecto, el campo único 'command'.
     */
    private static List<String> readCommands(Section itemSection) {
        List<String> commands = itemSection.getStringList("commands");
        if (commands != null && !commands.isEmpty()) {
            return commands;
        }

        String single = itemSection.getString("command", null);
        if (single != null && !single.trim().isEmpty()) {
            List<String> wrapped = new ArrayList<>();
            wrapped.add(single);
            return wrapped;
        }

        return new ArrayList<>();
    }

    /**
     * Interpreta el prefijo de ejecutor y lanza el comando en el hilo correcto.
     */
    private static void dispatch(SetHomeGUI plugin, Player player, String raw) {
        if (raw == null) return;

        String line = raw.trim();
        if (line.isEmpty()) return;

        // Sin prefijo, el comando lo ejecuta el propio jugador
        boolean asConsole = false;
        String lower = line.toLowerCase(Locale.ROOT);

        if (lower.startsWith(CONSOLE_PREFIX)) {
            asConsole = true;
            line = line.substring(CONSOLE_PREFIX.length()).trim();
        } else if (lower.startsWith(PLAYER_PREFIX)) {
            line = line.substring(PLAYER_PREFIX.length()).trim();
        }

        // La barra inicial es opcional en la configuración
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        if (line.isEmpty()) return;

        final String command = applyPlaceholders(plugin, player, line);

        if (asConsole) {
            // La consola no pertenece a ninguna región: en Folia debe ir al planificador global
            Bukkit.getGlobalRegionScheduler().run(plugin, task ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        } else {
            player.getScheduler().run(plugin, task -> player.performCommand(command), null);
        }
    }

    /**
     * Sustituye los placeholders de un comando.
     *
     * A diferencia de {@link Utils#setPlaceholders}, aquí NO se traducen los códigos de color:
     * convertir '&amp;' en '§' corrompería el comando antes de que el servidor lo interprete.
     */
    public static String applyPlaceholders(SetHomeGUI plugin, Player player, String text) {
        if (text == null) return "";

        String parsed = text
                .replace("%player_name%", player.getName())
                .replace("%player%", player.getName());

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            parsed = PlaceholderAPI.setPlaceholders(player, parsed);
        }

        return parsed;
    }
}
