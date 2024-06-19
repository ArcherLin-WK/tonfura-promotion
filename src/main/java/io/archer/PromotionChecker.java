package io.archer;

import io.archer.dba.cache.PromotionCache;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;

@ApplicationScoped
public class PromotionChecker {
    @Inject
    private PromotionCache cache;

    @Inject
    private Vertx vertx;

    private HashMap<String, Long> tasks = new HashMap<>();

    public synchronized void setTimer(String activityId, long delay) {
        if (tasks.containsKey(activityId)) {
            return;
        }
        long tid = vertx.setTimer(delay, id -> {
            Future.fromCompletionStage(
                    cache.prepare(activityId, id.toString()).subscribe().asCompletionStage()
            );
        });
        tasks.put(activityId, tid);
        Log.info(activityId + " is to count amount after " + delay + " ms." + ", Timer id: " + tid);
    }

    public synchronized void cancelTimer(String activityId) {
        if (tasks.containsKey(activityId)) {
            if (vertx.cancelTimer(tasks.get(activityId)))
                tasks.remove(activityId);
        }
    }

}
