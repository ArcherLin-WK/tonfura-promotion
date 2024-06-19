package io.archer.queue;

import io.archer.model.Promotion;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.SynchronousQueue;

@ApplicationScoped
public class IssuedPromotionQueue {
    private SynchronousQueue<Promotion> queue = new SynchronousQueue<>();

    public void offer(Promotion promotion) {
        queue.offer(promotion);
    }

    public Promotion take() throws InterruptedException {
        return queue.take();
    }
}
