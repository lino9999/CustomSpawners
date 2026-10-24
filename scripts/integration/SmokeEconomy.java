package io.github.lino9999.customspawners.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.List;

final class SmokeEconomy {
    double balance = 10000;
    int withdrawals;
    int refunds;
    boolean allowWithdrawal = true;
    Runnable onWithdrawal;
    private final Constructor<?> response;
    private final Object success;
    private final Object failure;

    @SuppressWarnings({"unchecked", "rawtypes"})
    SmokeEconomy(JavaPlugin plugin) throws Exception {
        var vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled())
            throw new IllegalStateException("Vault is not enabled");
        ClassLoader loader = vault.getClass().getClassLoader();
        Class<?> economy = Class.forName("net.milkbowl.vault.economy.Economy", true, loader);
        Class<?> resultType =
                Class.forName("net.milkbowl.vault.economy.EconomyResponse", true, loader);
        Class<? extends Enum> responseType =
                (Class<? extends Enum>)
                        Class.forName(
                                "net.milkbowl.vault.economy.EconomyResponse$ResponseType",
                                true,
                                loader);
        success = Enum.valueOf(responseType, "SUCCESS");
        failure = Enum.valueOf(responseType, "FAILURE");
        response =
                resultType.getConstructor(double.class, double.class, responseType, String.class);
        Object provider =
                Proxy.newProxyInstance(
                        loader,
                        new Class<?>[] {economy},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getName" -> "SmokeEconomy";
                                    case "isEnabled", "hasAccount", "createPlayerAccount" -> true;
                                    case "getBalance" -> balance;
                                    case "has" ->
                                            balance
                                                    >= ((Number) args[args.length - 1])
                                                            .doubleValue();
                                    case "withdrawPlayer" -> {
                                        double amount =
                                                ((Number) args[args.length - 1]).doubleValue();
                                        if (!allowWithdrawal || amount > balance)
                                            yield result(0, false);
                                        balance -= amount;
                                        withdrawals++;
                                        if (onWithdrawal != null) {
                                            Runnable callback = onWithdrawal;
                                            onWithdrawal = null;
                                            callback.run();
                                        }
                                        yield result(amount, true);
                                    }
                                    case "depositPlayer" -> {
                                        double amount =
                                                ((Number) args[args.length - 1]).doubleValue();
                                        balance += amount;
                                        refunds++;
                                        yield result(amount, true);
                                    }
                                    case "fractionalDigits" -> 2;
                                    case "currencyNamePlural", "currencyNameSingular" -> "credits";
                                    case "format" -> String.valueOf(args[0]);
                                    case "hasBankSupport" -> false;
                                    case "getBanks" -> List.of();
                                    case "toString" -> "SmokeEconomy";
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == args[0];
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    "Unexpected economy call: " + method);
                                });
        Bukkit.getServicesManager()
                .register((Class) economy, provider, plugin, ServicePriority.Highest);
    }

    private Object result(double amount, boolean ok) throws Exception {
        return response.newInstance(
                amount, balance, ok ? success : failure, ok ? "" : "Rejected by smoke fixture");
    }
}
