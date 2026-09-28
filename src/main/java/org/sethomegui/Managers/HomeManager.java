package org.sethomegui.Managers;

import dev.dejvokep.boostedyaml.YamlDocument;
import org.bukkit.Location;
import org.sethomegui.SetHomeGUI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class HomeManager {

    private final SetHomeGUI plugin;

    public HomeManager(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    /**
     * Obtiene el documento de datos de un jugador desde el backend activo
     * (archivos YAML por defecto, o MySQL si está configurado en config.yml).
     *
     * El documento devuelto se comporta como cualquier YamlDocument: al llamar a save()
     * los datos se persisten solos en el backend que corresponda.
     */
    public YamlDocument getPlayerFile(UUID uuid) {
        return plugin.getStorageManager().get(uuid);
    }

    /**
     * Elimina por completo los datos de un jugador del backend activo.
     */
    public void deletePlayerData(UUID uuid) {
        plugin.getStorageManager().delete(uuid);
    }

    /**
     * Guarda un nuevo hogar respetando la estructura exacta solicitada
     */
    public void saveHome(UUID uuid, String homeName, Location loc) {
        YamlDocument config = getPlayerFile(uuid);
        if (config == null) return;

        // 1. Obtener o crear la lista de indexación "homes"
        List<String> homeList = config.getStringList("homes");
        if (homeList == null) {
            homeList = new ArrayList<>();
        }

        // Evitar duplicados en el índice si sobreescribe el nombre
        if (!homeList.contains(homeName)) {
            homeList.add(homeName);
        }
        config.set("homes", homeList);

        // 2. Almacenar los datos posicionales bajo la clave del nombre del hogar
        config.set(homeName + ".world", loc.getWorld().getName());
        config.set(homeName + ".x", loc.getX());
        config.set(homeName + ".y", loc.getY());
        config.set(homeName + ".z", loc.getZ());
        config.set(homeName + ".yaw", (double) loc.getYaw());
        config.set(homeName + ".pitch", (double) loc.getPitch());

        try {
            config.save();
        } catch (IOException e) {
            // Leemos la plantilla desde el config.yml usando tu lector de BoostedYAML
            String saveErrorMsg = plugin.getMainConfig().getString(
                    "messages.system-errors.save-error",
                    "Could not save home data for UUID %uuid%"
            );

            // Reemplazamos el marcador %uuid% por la variable local y lo mandamos al logger
            plugin.getLogger().severe(saveErrorMsg.replace("%uuid%", uuid.toString()));
            e.printStackTrace();
        }
    }

    /** Valor que el resto del plugin interpreta como "sin límite". */
    public static final int UNLIMITED = -1;

    /** Permiso que otorga hogares ilimitados, por encima de cualquier límite numérico. */
    public static final String PERMISSION_UNLIMITED = "sethome.maxhomes.unlimited";

    /** Prefijo de los permisos de límite numérico (ej: sethome.maxhomes.10). */
    private static final String PERMISSION_PREFIX = "sethome.maxhomes.";

    /**
     * Calcula el límite máximo de hogares de un jugador basado en sus permisos y la config general.
     * Si devuelve -1 ({@link #UNLIMITED}), significa que el jugador tiene hogares ilimitados.
     *
     * Orden de prioridad:
     *   1. sethome.maxhomes.unlimited  -> ilimitado, gana siempre
     *   2. sethome.maxhomes.&lt;numero&gt; -> el valor más alto concedido
     *   3. default-max-homes del config.yml (-1 también significa ilimitado)
     */
    public int getPlayerMaxHomes(org.bukkit.entity.Player player) {
        // 1. El permiso de ilimitado gana siempre, sin importar los límites numéricos que tenga.
        //    Usamos hasPermission para que respete herencia de grupos y negaciones del gestor de permisos.
        if (player.hasPermission(PERMISSION_UNLIMITED)) {
            return UNLIMITED;
        }

        int maxFromPermission = Integer.MIN_VALUE; // Centinela: aún no encontramos ningún permiso

        // Escaneamos los permisos efectivos del jugador (funciona perfectamente con LuckPerms)
        for (org.bukkit.permissions.PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            // Un permiso NEGADO (valor false) aparece igualmente en la lista efectiva
            // y no debe otorgar ningún límite.
            if (!attachment.getValue()) continue;

            String permission = attachment.getPermission().toLowerCase(Locale.ROOT);
            if (!permission.startsWith(PERMISSION_PREFIX)) continue;

            String suffix = permission.substring(PERMISSION_PREFIX.length());

            try {
                int val = Integer.parseInt(suffix);

                // Cualquier valor negativo (sethome.maxhomes.-1) significa ilimitado,
                // igual que en default-max-homes.
                if (val < 0) {
                    return UNLIMITED;
                }

                // Si el jugador pertenece a varios grupos con límites distintos,
                // conservamos el valor más alto otorgado.
                if (val > maxFromPermission) {
                    maxFromPermission = val;
                }
            } catch (NumberFormatException ignored) {
                // Sufijo no numérico (por ejemplo el comodín '*'): no define ningún límite
            }
        }

        // 2. Si se encontró un permiso sethome.maxhomes.<numero>, este tiene prioridad sobre la config
        if (maxFromPermission != Integer.MIN_VALUE) {
            return maxFromPermission;
        }

        // 3. Sin permisos específicos manda la configuración (-1 = ilimitado)
        return plugin.getMainConfig().getInt("default-max-homes", 3);
    }

    /**
     * Obtiene la cantidad actual de hogares de un jugador
     */
    public int getHomeCount(UUID uuid) {
        YamlDocument config = getPlayerFile(uuid);
        if (config == null) return 0;
        List<String> homeList = config.getStringList("homes");
        return homeList != null ? homeList.size() : 0;
    }
}