package hotshop.service;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Serializes business operations, including session changes, away from JavaFX. */
public final class ServiceWorker implements AutoCloseable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("hotshop-services").factory());

    /** Queues one operation; failures complete its future exceptionally without stopping subsequent work. */
    public <T> CompletableFuture<T> submit(Callable<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    result.complete(operation.call());
                } catch (Exception exception) {
                    result.completeExceptionally(exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            result.completeExceptionally(new AccountException(AccountException.Code.SESSION,
                    "HotShop has closed", exception));
        }
        return result;
    }

    /** Drains accepted operations before resources or the application lock are released. */
    @Override
    public void close() {
        executor.close();
    }
}
