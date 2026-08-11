package me.mtynnn.valerinutils.integrations.excellenteconomy;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.EarningsChange;
import me.mtynnn.valerinutils.core.EarningsCurrency;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ExcellentEconomyEarningsTracker implements Listener {
    private static final String PLUGIN_NAME = "ExcellentEconomy";
    private static final String EVENT_CLASS =
            "su.nightexpress.excellenteconomy.api.event.ChangeBalanceEvent";
    private static final String CURRENCY_MANAGER_CLASS =
            "su.nightexpress.excellenteconomy.currency.CurrencyManager";
    private static final StackWalker STACK_WALKER = StackWalker.getInstance();

    private final ValerinUtils plugin;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    private Method getUser;
    private Method getUserId;
    private Method getCurrency;
    private Method getCurrencyId;
    private Method getOldAmount;
    private Method getNewAmount;

    public ExcellentEconomyEarningsTracker(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    public boolean start() {
        if (!claimStart()) return false;

        Plugin provider = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (provider == null || !provider.isEnabled()) {
            started.set(false);
            plugin.getLogger().warning("[EarningsTracker] ExcellentEconomy no está disponible; los totales persistidos siguen visibles, pero no se registrarán ingresos nuevos.");
            return false;
        }

        try {
            Class<? extends Event> eventClass = Class.forName(EVENT_CLASS, true, provider.getClass().getClassLoader())
                    .asSubclass(Event.class);
            getUser = eventClass.getMethod("getUser");
            getUserId = getUser.getReturnType().getMethod("getId");
            getCurrency = eventClass.getMethod("getCurrency");
            getCurrencyId = getCurrency.getReturnType().getMethod("getId");
            getOldAmount = eventClass.getMethod("getOldAmount");
            getNewAmount = eventClass.getMethod("getNewAmount");

            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR,
                    (listener, event) -> capture(event), plugin, true);
            plugin.getLogger().info("[EarningsTracker] Registrando ingresos de ExcellentEconomy para money y shards.");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            started.set(false);
            warnOnce("No se pudo enlazar la API ExcellentEconomy 2.8.0; no se registrarán ingresos nuevos", error);
            return false;
        }
    }

    public void stop() {
        if (started.getAndSet(false)) {
            HandlerList.unregisterAll(this);
        }
    }

    boolean claimStart() {
        return started.compareAndSet(false, true);
    }

    private void capture(Event event) {
        if (!started.get() || event instanceof Cancellable cancellable && cancellable.isCancelled()) return;

        try {
            Object user = getUser.invoke(event);
            Object currency = getCurrency.invoke(event);
            UUID playerId = (UUID) getUserId.invoke(user);
            String currencyId = (String) getCurrencyId.invoke(currency);
            double oldAmount = ((Number) getOldAmount.invoke(event)).doubleValue();
            double newAmount = ((Number) getNewAmount.invoke(event)).doubleValue();
            EarningsCurrency trackedCurrency = EarningsChange.currency(currencyId, oldAmount, newAmount);
            if (trackedCurrency == null || isPlayerTransfer()) return;

            EarningsSnapshot snapshot = new EarningsSnapshot(
                    playerId, trackedCurrency, EarningsChange.positiveDelta(oldAmount, newAmount));
            dispatch(event.isAsynchronous() || !Bukkit.isPrimaryThread(),
                    () -> apply(snapshot), task -> Bukkit.getScheduler().runTask(plugin, task));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            warnOnce("No se pudo leer un evento de saldo de ExcellentEconomy", error);
        }
    }

    private void apply(EarningsSnapshot snapshot) {
        Plugin provider = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (!started.get() || provider == null || !provider.isEnabled()) return;
        plugin.getPlayerDataManager().addEarnings(snapshot.playerId(), snapshot.currency(), snapshot.delta());
    }

    private void warnOnce(String message, Throwable error) {
        if (failureLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning("[EarningsTracker] " + message + ": " + error.getMessage());
        }
    }

    static void dispatch(boolean asynchronous, Runnable apply, Consumer<Runnable> scheduler) {
        if (asynchronous) {
            scheduler.accept(apply);
        } else {
            apply.run();
        }
    }

    private static boolean isPlayerTransfer() {
        // ChangeBalanceEvent 2.8.0 has no cause; /pay is emitted from this exact provider operation.
        return STACK_WALKER.walk(frames -> frames.anyMatch(frame ->
                isTransferFrame(frame.getClassName(), frame.getMethodName())));
    }

    static boolean isTransferFrame(String className, String methodName) {
        return CURRENCY_MANAGER_CLASS.equals(className) && "send".equals(methodName);
    }

    private record EarningsSnapshot(UUID playerId, EarningsCurrency currency, double delta) {
    }
}
