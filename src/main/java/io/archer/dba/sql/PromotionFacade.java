package io.archer.dba.sql;

import io.archer.model.Promotion;
import io.archer.queue.IssuedPromotionQueue;
import io.archer.queue.ReservedPromotionQueue;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class PromotionFacade {
    @Inject
    @VirtualThreads
    ExecutorService vThreads;

    @Inject
    IssuedPromotionQueue issuedQueue;

    @Inject
    ReservedPromotionQueue reservedQueue;

    void onEvent(@Observes StartupEvent event) {
        vThreads.execute(() -> {
            while (true){
                try {
                    Promotion promotion = reservedQueue.take();
                    if (promotion != null)
                        this.saveReservation(promotion);
                } catch (InterruptedException e) {
                    Log.error("Failed to take the promotion from the reserved queue.", e);
                }
            }
        });
        vThreads.execute(() -> {
            while (true) {
                try {
                    Promotion promotion = issuedQueue.take();
                    if (promotion != null)
                        this.saveIssuance(promotion);
                } catch (InterruptedException e) {
                    Log.error("Failed to take the promotion from the issued queue.", e);
                }
            }
        });
        Log.info("PromotionFacade is ready.");
    }

    public void saveReservation(Promotion promotion) {
        // SQL statement for saving the reservation
        StringBuilder statement = new StringBuilder()
                .append("INSERT INTO promotion (id, user, activity, reservedTime) ")
                .append("VALUES (")
                .append("UUID(), ")
                .append("'" + promotion.getUser() + "',")
                .append("'" + promotion.getActivity() + "',")
                .append("TIMESTAMP('" + DateTimeFormatter.ISO_INSTANT.format(promotion.getReservedTime()) + "')")
                .append(")");
        Log.info("execute SQL: " + statement.toString());
    }

    public void saveIssuance(Promotion promotion) {
        // SQL statement for saving the issuance
        StringBuilder statement = new StringBuilder()
                .append("UPDATE promotion SET ")
                .append("coupon = " + promotion.getCode() + ", ")
                .append("issuedTime = TIMESTAMP('" + DateTimeFormatter.ISO_INSTANT.format(promotion.getIssuedTime()) + "') ")
                .append("WHERE id =" + promotion.getId());
        Log.info("execute SQL: " + statement.toString());
    }
}
