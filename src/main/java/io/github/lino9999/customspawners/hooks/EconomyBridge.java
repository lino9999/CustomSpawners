package io.github.lino9999.customspawners.hooks;

import io.github.lino9999.customspawners.config.Settings;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class EconomyBridge {
    private final JavaPlugin plugin;
    private final Settings settings;
    private final Set<String> warnings = new HashSet<>();
    private Object provider;
    private Method balance;
    private Method withdraw;
    private Method deposit;
    private Method enabled;
    private Method responseSuccess;
    private String providerName = "Unavailable";
    private boolean points;

    public EconomyBridge(JavaPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        reload();
    }

    public void reload() {
        provider = null;
        balance = null;
        withdraw = null;
        deposit = null;
        enabled = null;
        responseSuccess = null;
        points = false;
        providerName = "Unavailable";
        String selected = settings.text("economy.provider").trim();
        try {
            if (selected.equalsIgnoreCase("PlayerPoints")) {
                connectPoints();
            } else if (selected.isEmpty() || selected.equalsIgnoreCase("Vault")) {
                connectVault();
            } else {
                warn(
                        "provider",
                        "Unsupported economy provider: "
                                + selected
                                + ". Choose Vault or PlayerPoints.");
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            provider = null;
            warn(
                    "connect",
                    "Could not connect the configured economy provider: "
                            + exception.getClass().getSimpleName());
        }
    }

    private void connectPoints() throws ReflectiveOperationException {
        Plugin pointsPlugin = plugin.getServer().getPluginManager().getPlugin("PlayerPoints");
        if (pointsPlugin == null || !pointsPlugin.isEnabled()) {
            return;
        }
        Object api = pointsPlugin.getClass().getMethod("getAPI").invoke(pointsPlugin);
        Class<?> type = api.getClass();
        balance = type.getMethod("look", UUID.class);
        withdraw = type.getMethod("take", UUID.class, int.class);
        deposit = type.getMethod("give", UUID.class, int.class);
        points = true;
        providerName = "PlayerPoints";
        provider = api;
    }

    private void connectVault() throws ReflectiveOperationException {
        Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            return;
        }
        Class<?> economy =
                Class.forName(
                        "net.milkbowl.vault.economy.Economy",
                        true,
                        vault.getClass().getClassLoader());
        RegisteredServiceProvider<?> service =
                plugin.getServer().getServicesManager().getRegistration(economy);
        if (service == null) {
            return;
        }
        Object api = service.getProvider();
        balance = economy.getMethod("getBalance", OfflinePlayer.class);
        withdraw = economy.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
        deposit = economy.getMethod("depositPlayer", OfflinePlayer.class, double.class);
        enabled = economy.getMethod("isEnabled");
        responseSuccess = withdraw.getReturnType().getMethod("transactionSuccess");
        providerName = "Vault / " + economy.getMethod("getName").invoke(api);
        provider = api;
    }

    public boolean available() {
        if (provider == null) {
            return false;
        }
        Plugin dependency =
                plugin.getServer().getPluginManager().getPlugin(points ? "PlayerPoints" : "Vault");
        if (dependency == null || !dependency.isEnabled()) {
            return false;
        }
        try {
            return points || Boolean.TRUE.equals(enabled.invoke(provider));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            warn(
                    "available",
                    "The economy provider is no longer available; paid actions are blocked.");
            return false;
        }
    }

    public String name() {
        return providerName;
    }

    public double balance(Player player) {
        if (!available()) {
            return 0;
        }
        try {
            Object account = points ? player.getUniqueId() : player;
            double amount = ((Number) balance.invoke(provider, account)).doubleValue();
            return Double.isFinite(amount) ? Math.max(0, amount) : 0;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            warn("balance", "Could not read an economy balance; paid actions are blocked.");
            return 0;
        }
    }

    public boolean withdraw(Player player, double amount) {
        return withdrawReceipt(player, amount).successful();
    }

    public boolean deposit(Player player, double amount) {
        return transact(player, amount, true);
    }

    public Transaction withdrawReceipt(Player player, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return Transaction.failed();
        if (amount == 0) return Transaction.free();
        Binding binding = binding();
        if (binding == null
                || !bindingAvailable(binding)
                || binding.points && Math.ceil(amount) > Integer.MAX_VALUE)
            return Transaction.failed();
        double charged = binding.points ? Math.ceil(amount) : amount;
        Object account = binding.points ? player.getUniqueId() : player;
        try {
            double funds =
                    ((Number) binding.balance.invoke(binding.provider, account)).doubleValue();
            if (!Double.isFinite(funds)
                    || funds < charged
                    || !binding.transfer(account, charged, false)) return Transaction.failed();
            return new Transaction(
                    true,
                    charged,
                    binding.name,
                    () -> {
                        try {
                            return binding.transfer(account, charged, true);
                        } catch (ReflectiveOperationException
                                | LinkageError
                                | RuntimeException exception) {
                            warn(
                                    "refund",
                                    "A refund through the original economy provider failed. Check"
                                        + " its logs before retrying.");
                            return false;
                        }
                    });
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            warn(
                    "transaction",
                    "The economy provider rejected or failed a transaction. Check its logs before"
                        + " retrying.");
            return Transaction.failed();
        }
    }

    private Binding binding() {
        return provider == null
                ? null
                : new Binding(
                        provider,
                        balance,
                        withdraw,
                        deposit,
                        enabled,
                        responseSuccess,
                        points,
                        providerName);
    }

    private boolean bindingAvailable(Binding binding) {
        Plugin dependency =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin(binding.points ? "PlayerPoints" : "Vault");
        if (dependency == null || !dependency.isEnabled()) return false;
        try {
            return binding.points || Boolean.TRUE.equals(binding.enabled.invoke(binding.provider));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return false;
        }
    }

    private boolean transact(Player player, double amount, boolean credit) {
        if (!Double.isFinite(amount) || amount < 0) {
            return false;
        }
        if (amount == 0) {
            return true;
        }
        Binding binding = binding();
        if (binding == null
                || !bindingAvailable(binding)
                || binding.points && Math.ceil(amount) > Integer.MAX_VALUE) {
            return false;
        }
        double charged = binding.points ? Math.ceil(amount) : amount;
        try {
            Object account = binding.points ? player.getUniqueId() : player;
            return binding.transfer(account, charged, credit);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            warn(
                    "transaction",
                    "The economy provider rejected or failed a transaction. Check its logs before"
                        + " retrying.");
            return false;
        }
    }

    private record Binding(
            Object provider,
            Method balance,
            Method withdraw,
            Method deposit,
            Method enabled,
            Method responseSuccess,
            boolean points,
            String name) {
        private boolean transfer(Object account, double amount, boolean credit)
                throws ReflectiveOperationException {
            Method operation = credit ? deposit : withdraw;
            Object result =
                    points
                            ? operation.invoke(provider, account, (int) amount)
                            : operation.invoke(provider, account, amount);
            return points
                    ? Boolean.TRUE.equals(result)
                    : Boolean.TRUE.equals(responseSuccess.invoke(result));
        }
    }

    public static final class Transaction {
        private final boolean successful;
        private final double amount;
        private final String provider;
        private final BooleanSupplier rollback;
        private boolean attempted;
        private boolean refunded;

        Transaction(boolean successful, double amount, String provider, BooleanSupplier rollback) {
            this.successful = successful;
            this.amount = amount;
            this.provider = provider;
            this.rollback = rollback;
        }

        public static Transaction free() {
            return new Transaction(true, 0, "No charge", () -> true);
        }

        public static Transaction failed() {
            return new Transaction(false, 0, "Unavailable", () -> false);
        }

        public boolean successful() {
            return successful;
        }

        public double amount() {
            return amount;
        }

        public String provider() {
            return provider;
        }

        public synchronized boolean refund() {
            if (!successful) return false;
            if (!attempted) {
                attempted = true;
                refunded = rollback.getAsBoolean();
            }
            return refunded;
        }
    }

    private void warn(String key, String message) {
        if (warnings.add(key)) {
            plugin.getLogger().warning(message);
        }
    }
}
