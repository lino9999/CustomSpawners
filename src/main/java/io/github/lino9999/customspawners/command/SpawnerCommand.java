package io.github.lino9999.customspawners.command;

import io.github.lino9999.customspawners.CustomSpawnersPlugin;
import io.github.lino9999.customspawners.api.SpawnerActionEvent.Action;
import io.github.lino9999.customspawners.gui.Menus;
import io.github.lino9999.customspawners.model.Boost;
import io.github.lino9999.customspawners.model.PlacedSpawner;
import io.github.lino9999.customspawners.model.StackKey;
import io.github.lino9999.customspawners.service.SpawnerItems;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class SpawnerCommand implements TabExecutor {
    private static final List<String> SUBCOMMANDS =
            List.of(
                    "help",
                    "shop",
                    "stats",
                    "inspect",
                    "upgrade",
                    "convert",
                    "boost",
                    "give",
                    "reload",
                    "editor",
                    "settings",
                    "spawn",
                    "change",
                    "playerboost",
                    "openshop",
                    "save",
                    "remove");
    private final CustomSpawnersPlugin plugin;

    public SpawnerCommand(CustomSpawnersPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            String sub =
                    command.getName().equals("cspawnershop")
                            ? "shop"
                            : command.getName().equals("cspawnerstats")
                                    ? "stats"
                                    : args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
            String[] values =
                    command.getName().equals("customspawners") && args.length > 0
                            ? Arrays.copyOfRange(args, 1, args.length)
                            : args;
            if (sub.equals("help")) {
                help(sender);
                return true;
            }
            if (!SUBCOMMANDS.contains(sub))
                throw new IllegalArgumentException("Unknown command. Use /csp help.");
            String permission =
                    sub.equals("inspect")
                            ? "use"
                            : sub.equals("playerboost")
                                    ? "adminboost"
                                    : sub.equals("change") ? "change" : sub;
            if (!sender.hasPermission("customspawners." + permission)) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            validateArguments(sub, values);
            switch (sub) {
                case "reload" -> {
                    plugin.reloadPlugin();
                    plugin.messages().send(sender, "reloaded");
                }
                case "save" -> {
                    plugin.saveState();
                    plugin.messages().send(sender, "saved");
                }
                case "give" -> give(sender, values);
                case "openshop" -> {
                    require(values, 1, "openshop <player>");
                    plugin.menus().shop(online(values[0]), 0);
                }
                case "playerboost" -> playerBoost(sender, values);
                case "editor" -> {
                    if (values.length == 0) plugin.menus().editor(player(sender), 0);
                    else {
                        edit(values, true);
                        plugin.messages().send(sender, "edited");
                    }
                }
                case "settings" -> {
                    if (values.length == 0) plugin.menus().settings(player(sender), 0);
                    else {
                        edit(values, false);
                        plugin.messages().send(sender, "edited");
                    }
                }
                default -> playerAction(player(sender), sub, values);
            }
        } catch (Exception ex) {
            plugin.messages()
                    .send(
                            sender,
                            "invalid",
                            Map.of(
                                    "reason",
                                    ex.getMessage() == null
                                            ? ex.getClass().getSimpleName()
                                            : ex.getMessage()));
        }
        return true;
    }

    private void playerAction(Player player, String sub, String[] args) {
        if (sub.equals("shop")) {
            plugin.menus().shop(player, 0);
            return;
        }
        if (sub.equals("stats")) {
            plugin.menus().stats(player);
            return;
        }
        PlacedSpawner spawner = target(player);
        if (spawner == null) return;
        switch (sub) {
            case "inspect" -> plugin.menus().overview(player, spawner);
            case "upgrade" -> {
                boolean levels = args.length > 0 && args[0].equalsIgnoreCase("xp");
                if (args.length > 0 && !levels && !args[0].equalsIgnoreCase("money"))
                    throw new IllegalArgumentException(
                            "Usage: /csp upgrade [money|xp] [stack|tier]");
                if (args.length > 1
                        && !args[1].equalsIgnoreCase("tier")
                        && !args[1].equalsIgnoreCase("stack"))
                    throw new IllegalArgumentException(
                            "Usage: /csp upgrade [money|xp] [stack|tier]");
                if (args.length > 1 && args[1].equalsIgnoreCase("tier"))
                    plugin.service().upgrade(player, spawner, spawner.first(), levels);
                else plugin.service().grow(player, spawner, spawner.first(), levels);
            }
            case "convert" -> {
                require(args, 1, "convert <type>");
                plugin.service().convert(player, spawner, spawner.first(), args[0], false);
            }
            case "change" -> {
                require(args, 1, "change <type> [tier]");
                var type = plugin.catalog().get(args[0]);
                if (type == null || !type.enabled())
                    throw new IllegalArgumentException("Unknown or disabled type.");
                if (plugin.service().blocked(player.getWorld().getName(), type.id())
                        || !plugin.service().typePermission(player, "change", type.id())) {
                    plugin.messages().send(player, "no-permission");
                    return;
                }
                String tier = args.length > 1 ? args[1] : type.firstTier().id();
                var targetTier = plugin.catalog().tier(new StackKey(type.id(), tier));
                if (targetTier == null) throw new IllegalArgumentException("Unknown tier.");
                StackKey replacement = new StackKey(type.id(), targetTier.id());
                if (!plugin.service().access(player, spawner, Action.ACCESS)) return;
                Map<StackKey, Integer> before = Map.copyOf(spawner.stacks());
                if (!plugin.service().event(player, spawner, Action.CONVERT)
                        || !plugin.service().access(player, spawner, Action.ACCESS)
                        || !before.equals(spawner.stacks())
                        || plugin.catalog().get(type.id()) != type
                        || !player.hasPermission("customspawners.change")
                        || !plugin.service().typePermission(player, "change", type.id())
                        || plugin.service().blocked(player.getWorld().getName(), type.id())) return;
                spawner.replace(spawner.first(), replacement);
                plugin.service().changed(spawner);
                plugin.messages().send(player, "converted", Map.of("type", type.name()));
            }
            case "boost" -> {
                if (args.length == 0) plugin.menus().boosts(player, spawner, 1);
                else {
                    require(args, 2, "boost <extra-output> <minutes>");
                    plugin.service()
                            .boost(
                                    player,
                                    spawner,
                                    integer(args[0], 1, 100),
                                    integer(args[1], 1, 1440));
                }
            }
            case "spawn" -> {
                if (plugin.service().access(player, spawner, Action.ACCESS)) {
                    boolean success = plugin.engine().spawnNow(spawner);
                    if (success) plugin.messages().send(player, "spawned");
                    else
                        throw new IllegalArgumentException(
                                "No output was produced. " + plugin.engine().pauseReason(spawner));
                }
            }
            case "remove" -> {
                if (plugin.service().access(player, spawner, Action.BREAK)) {
                    plugin.service().remove(spawner);
                    plugin.messages().send(player, "removed");
                }
            }
            default -> throw new IllegalArgumentException("Unknown player command.");
        }
    }

    private void give(CommandSender sender, String[] args) {
        require(args, 2, "give <player|all> <type|random> [tier] [items] [stack]");
        var enabled = plugin.catalog().all().stream().filter(type -> type.enabled()).toList();
        var type =
                args[1].equalsIgnoreCase("random") && !enabled.isEmpty()
                        ? enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()))
                        : plugin.catalog().get(args[1]);
        if (type == null || !type.enabled())
            throw new IllegalArgumentException("Unknown or disabled type.");
        String tier = args.length > 2 ? args[2] : type.firstTier().id();
        var targetTier = plugin.catalog().tier(new StackKey(type.id(), tier));
        if (targetTier == null) throw new IllegalArgumentException("Unknown tier.");
        StackKey key = new StackKey(type.id(), targetTier.id());
        int count = args.length > 3 ? integer(args[3], 1, 4096) : 1;
        int stack =
                args.length > 4
                        ? integer(args[4], 1, plugin.settings().integer("stacking.max-stack"))
                        : 1;
        var targets =
                args[0].equalsIgnoreCase("all")
                        ? List.copyOf(Bukkit.getOnlinePlayers())
                        : List.of(online(args[0]));
        for (Player player : targets) {
            var item = plugin.items().create(key, stack);
            int remaining = count;
            while (remaining > 0) {
                int amount = Math.min(item.getMaxStackSize(), remaining);
                item.setAmount(amount);
                SpawnerItems.giveOrDrop(player, item.clone());
                remaining -= amount;
            }
        }
        plugin.messages().send(sender, "given", Map.of("player", args[0]));
    }

    private void playerBoost(CommandSender sender, String[] args) {
        require(args, 2, "playerboost <player> <extra-output|0> [duration: 30m|2h|permanent]");
        Player player = online(args[0]);
        int amount = integer(args[1], 0, plugin.settings().integer("boosts.max-amount"));
        long expires =
                args.length > 2 && !args[2].equalsIgnoreCase("permanent")
                        ? Math.addExact(System.currentTimeMillis(), duration(args[2]))
                        : Long.MAX_VALUE;
        plugin.boosts().put(player.getUniqueId(), new Boost(amount, expires));
        plugin.saveState();
        plugin.messages().send(sender, "edited");
    }

    static long duration(String input) {
        if (input == null) throw new IllegalArgumentException("Duration is required.");
        var matcher =
                Pattern.compile("([1-9][0-9]{0,7})([smhd])", Pattern.CASE_INSENSITIVE)
                        .matcher(input);
        if (!matcher.matches())
            throw new IllegalArgumentException(
                    "Duration must be a positive number followed by s, m, h or d.");
        long factor =
                switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                    case "s" -> 1000L;
                    case "m" -> 60000L;
                    case "h" -> 3600000L;
                    default -> 86400000L;
                };
        long result = Math.multiplyExact(Long.parseLong(matcher.group(1)), factor);
        if (result > 365L * 86400000L)
            throw new IllegalArgumentException("Maximum boost duration is 365 days.");
        return result;
    }

    private void edit(String[] args, boolean catalog) throws Exception {
        require(args, 3, (catalog ? "editor" : "settings") + " set <path> <yaml-value>");
        if (!args[0].equalsIgnoreCase("set"))
            throw new IllegalArgumentException("Expected set <path> <yaml-value>.");
        Object value = Menus.parseValue(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        if (catalog) plugin.editCatalog(args[1], value);
        else plugin.editSetting(args[1], value);
    }

    public PlacedSpawner target(Player player) {
        var block = player.getTargetBlockExact(8);
        var spawner = block == null ? null : plugin.service().adopt(block);
        if (spawner == null) plugin.messages().send(player, "not-found");
        return spawner;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        throw new IllegalArgumentException("This command requires a player.");
    }

    private Player online(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) throw new IllegalArgumentException("Player is not online: " + name);
        return player;
    }

    private static int integer(String value, int min, int max) {
        int number = Integer.parseInt(value);
        if (number < min || number > max)
            throw new IllegalArgumentException(
                    "Amount must be between " + min + " and " + max + ".");
        return number;
    }

    private static void require(String[] args, int amount, String syntax) {
        if (args.length < amount) throw new IllegalArgumentException("Usage: /csp " + syntax);
    }

    static void validateArguments(String command, String[] args) {
        int min =
                switch (command) {
                    case "give", "playerboost" -> 2;
                    case "convert", "change", "openshop" -> 1;
                    default -> 0;
                };
        int max =
                switch (command) {
                    case "give" -> 5;
                    case "playerboost" -> 3;
                    case "change", "boost", "upgrade" -> 2;
                    case "convert", "openshop" -> 1;
                    case "editor", "settings" -> Integer.MAX_VALUE;
                    default -> 0;
                };
        if (args.length < min || args.length > max || command.equals("boost") && args.length == 1) {
            throw new IllegalArgumentException(
                    "Invalid arguments for " + command + ". Use /csp help.");
        }
        if (command.equals("upgrade")
                && (args.length > 0
                                && !args[0].equalsIgnoreCase("money")
                                && !args[0].equalsIgnoreCase("xp")
                        || args.length > 1
                                && !args[1].equalsIgnoreCase("tier")
                                && !args[1].equalsIgnoreCase("stack"))) {
            throw new IllegalArgumentException("Usage: /csp upgrade [money|xp] [stack|tier]");
        }
    }

    private void help(CommandSender sender) {
        plugin.messages().send(sender, "help-title");
        sender.sendMessage("/csp shop | stats | inspect | upgrade [money|xp] [stack|tier]");
        sender.sendMessage("/csp convert <type> | boost [amount minutes]");
        if (sender.hasPermission("customspawners.admin")) {
            sender.sendMessage("/csp give <player|all> <type|random> [tier] [items] [stack]");
            sender.sendMessage("/csp editor [set <path> <yaml>] | settings [set <path> <yaml>]");
            sender.sendMessage("/csp playerboost <player> <amount|0> [30m|permanent]");
            sender.sendMessage(
                    "/csp reload | save | spawn | change <type> [tier] | remove | openshop"
                        + " <player>");
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equals("customspawners")) return List.of();
        List<String> candidates = new ArrayList<>();
        if (args.length == 1)
            candidates.addAll(
                    SUBCOMMANDS.stream()
                            .filter(
                                    sub ->
                                            sub.equals("help")
                                                    || sender.hasPermission(
                                                            "customspawners."
                                                                    + (sub.equals("inspect")
                                                                            ? "use"
                                                                            : sub.equals(
                                                                                            "playerboost")
                                                                                    ? "adminboost"
                                                                                    : sub)))
                            .toList());
        else if (args.length == 2 && List.of("give", "openshop", "playerboost").contains(args[0])) {
            Bukkit.getOnlinePlayers().forEach(player -> candidates.add(player.getName()));
            if (args[0].equals("give")) candidates.add("all");
        } else if (args.length == 2 && List.of("change", "convert").contains(args[0])
                || args.length == 3 && args[0].equals("give")) {
            plugin.catalog().all().stream()
                    .filter(type -> type.enabled())
                    .forEach(type -> candidates.add(type.id()));
            if (args[0].equals("give")) candidates.add("random");
        } else if (args.length == 4 && args[0].equals("give")) {
            var type = plugin.catalog().get(args[2]);
            if (type != null) candidates.addAll(type.tiers().keySet());
        } else if (args.length == 2 && args[0].equals("upgrade"))
            candidates.addAll(List.of("money", "xp"));
        else if (args.length == 3 && args[0].equals("upgrade"))
            candidates.addAll(List.of("stack", "tier"));
        else if (args.length == 2 && List.of("editor", "settings").contains(args[0]))
            candidates.add("set");
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }
}
