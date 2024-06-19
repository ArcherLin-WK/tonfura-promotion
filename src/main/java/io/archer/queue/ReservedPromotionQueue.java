package io.archer.queue;

import io.archer.model.Promotion;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.SynchronousQueue;

@ApplicationScoped
public class ReservedPromotionQueue {
    private SynchronousQueue<Promotion> queue = new SynchronousQueue<>();

    public void offer(Promotion promotion) {
        queue.offer(promotion);
        Log.info("Promotion " + promotion.getId() + " is reserved.");
    }

    public Promotion take() throws InterruptedException {
        return queue.take();
    }
}
