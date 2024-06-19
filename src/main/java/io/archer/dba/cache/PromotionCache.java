package io.archer.dba.cache;

import io.archer.exception.PromotionException;
import io.archer.model.Promotion;
import io.quarkus.logging.Log;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.Json;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.UUID;

@ApplicationScoped
public class PromotionCache {
    @Inject
    @RedisClientName("promotion")
    private ReactiveRedisDataSource redisDataSource;

    // prefix key for cache
    private final String key = "promotion";

    // ERROR Messages
    private final String ERROR_DUPLICATED_ISSUE = "the promotion has been issued.";
    private final String ERROR_GETTING_PROMOTION_AMOUNT = "failed to get the promotion amount.";
    private final String ERROR_ISSUE = "failed to issue a promotion.";
    private final String ERROR_NO_RESERVATION = "no reservation";
    private final String ERROR_RAN_OUT = "the promotion amount is ran out.";
    private final String ERROR_RELEASE_LOCK = "failed to release the lock.";
    private final String ERROR_RESERVE = "failed to reserve a promotion.";
    private final String ERROR_REVERSE_AMOUNT = "failed to reverse promotion amount.";

    private Uni<Long> calculateAmount(String activityId) {
        Log.info(activityId + ": start to count the total amount for promotion");
        return redisDataSource.hash(
                String.class,
                String.class,
                String.class
        ).hlen(
                String.join(":", key, activityId)
        ).chain(length ->
                Uni.createFrom().item(Math.round(length * 0.2))
        ).chain(amount ->
                setPromotionAmount(activityId, amount).replaceWith(amount)
        );
    }

    private Uni<Long> decentAmount(String activityId) {
        return redisDataSource.value(
                String.class
        ).get(
                String.join(":", key, "amount", activityId)
        ).chain(value -> {
            if (value != null && !value.isEmpty()) {
                long amount = Long.parseLong(value);
                if (amount > 0) {
                    return Uni.createFrom().item(amount - 1);
                } else {
                    Log.error(activityId + ": " + ERROR_RAN_OUT);
                    return Uni.createFrom().failure(
                            new PromotionException(ERROR_RAN_OUT)
                    );
                }
            } else {
                Log.error(activityId + ": " + ERROR_GETTING_PROMOTION_AMOUNT);
                return Uni.createFrom().failure(
                        new PromotionException(ERROR_GETTING_PROMOTION_AMOUNT)
                );
            }
        }).chain(amount ->
                setPromotionAmount(activityId, amount).replaceWith(amount)
        );
    }

    private Uni<Long> increaseAmount(String activityId) {
        return redisDataSource.value(
                String.class
        ).get(
                String.join(":", key, "amount", activityId)
        ).chain(value -> {
            if (value != null && !value.isEmpty()) {
                long amount = Long.parseLong(value);
                if (amount >= 0) {
                    return Uni.createFrom().item(amount + 1);
                }
                return Uni.createFrom().item(0L);
            } else {
                Log.error(activityId + ": " + ERROR_GETTING_PROMOTION_AMOUNT);
                return Uni.createFrom().failure(
                        new PromotionException(ERROR_GETTING_PROMOTION_AMOUNT)
                );
            }
        }).chain(amount ->
                setPromotionAmount(activityId, amount).replaceWith(amount)
        );
    }

    private Uni<Void> lock(String activityId, String whom) {
        SetArgs args = new SetArgs().nx().px(5000);
        return redisDataSource.value(
                String.class
        ).set(
                String.join(":", "lock", activityId),
                whom,
                new SetArgs().nx().px(5000)
        );
    }

    private Uni<Void> release(String activityId) {
        return redisDataSource.key().del(
                String.join(":", "lock", activityId)
        ).chain(count -> {
            if (count == 0 || count == 1) {
                return Uni.createFrom().nullItem();
            } else {
                Log.error(activityId + ": " + ERROR_RELEASE_LOCK);
                return Uni.createFrom().failure(
                        new PromotionException(ERROR_RELEASE_LOCK)
                );
            }
        });
    }

    private Uni<Void> setPromotionAmount(String activityId, long amount) {
        return redisDataSource.value(
                String.class
        ).set(
                String.join(":", key, "amount", activityId),
                String.valueOf(amount)
        );
    }

    public Uni<Promotion> isReserved(String activityId, String userId) {
        return redisDataSource.hash(
                String.class,
                String.class,
                String.class
        ).hget(
                String.join(":", key, activityId),
                userId
        ).chain(value -> {
            if (value == null || value.isEmpty()) {
                return Uni.createFrom().nullItem();
            } else {
                try {
                    //noinspection unchecked
                    return Uni.createFrom().item(
                            Promotion.valueOf(
                                    Json.decodeValue(
                                            value,
                                            HashMap.class
                                    )
                            )
                    );
                } catch (Exception e) {
                    return Uni.createFrom().failure(e);
                }
            }
        });
    }

    public Uni<Promotion> reserve(String activityId, String userId) {
        return isReserved(
                activityId,
                userId
        ).chain(promotion -> {
            if (promotion == null) {
                HashMap<String, String> values = new HashMap<>();
                values.put("id", UUID.randomUUID().toString());
                values.put("activity", activityId);
                values.put("user", userId);
                values.put("reservedTime", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                return redisDataSource.hash(
                        String.class,
                        String.class,
                        String.class
                ).hset(
                        String.join(":", key, activityId),
                        userId,
                        Json.encode(values)
                ).chain(success -> {
                    if (success) {
                        return Uni.createFrom().item(Promotion.valueOf(values));
                    } else {
                        Log.error(
                                String.join(
                                        " - ",
                                        activityId,
                                        userId
                                ) + ": " + ERROR_RESERVE
                        );
                        return Uni.createFrom().failure(
                                new PromotionException(ERROR_RESERVE)
                        );
                    }
                });
            } else {
                return Uni.createFrom().item(promotion);
            }
        });
    }

    public Uni<Void> prepare(String activityId, String whom) {
        return lock(
                activityId,
                "promotion-timer-" + whom
        ).chain(v ->
                calculateAmount(activityId)
        ).chain(amount -> {
            Log.info(activityId + ": total amount of promotion is " + amount);
            return release(activityId);
        });
    }

    public Uni<Promotion> issue(String activityId, String userId) {
        return isReserved(
                activityId,
                userId
        ).chain(
                lock(activityId, userId)::replaceWith
        ).chain(promotion -> {
            // check the user has reserved and no coupon has been issued
            if (promotion != null) {
                if (promotion.getIssuedTime() == null) {
                    // take one from total amount
                    return decentAmount(activityId)
                            .chain(amount -> {
                                promotion.setCode(new RandomString().next())
                                        .setIssuedTime(Instant.now());
                                return redisDataSource.hash(
                                        String.class,
                                        String.class,
                                        String.class
                                ).hset(
                                        String.join(":", key, activityId),
                                        userId,
                                        Json.encode(promotion.toMap())
                                ).chain(success -> {
                                    // false means data have been update, true means data have been created.
                                    // in here, not allow to create data.
                                    if (!success) {
                                        return Uni.createFrom().item(promotion);
                                    } else {
                                        Log.error(
                                                String.join(
                                                        " - ",
                                                        activityId,
                                                        userId
                                                ) + ": " + ERROR_ISSUE
                                        );
                                        return reclaim(
                                                activityId,
                                                userId
                                        ).chain(() ->
                                                Uni.createFrom().failure(
                                                        new PromotionException(ERROR_ISSUE)
                                                )
                                        );
                                    }
                                });
                            });
                } else {
                    Log.error(
                            String.join(
                                    " - ",
                                    activityId,
                                    userId
                            ) + ": " + ERROR_DUPLICATED_ISSUE
                    );
                    return Uni.createFrom().failure(
                            new PromotionException(ERROR_DUPLICATED_ISSUE)
                    );
                }
            } else {
                Log.error(
                        String.join(
                                " - ",
                                activityId,
                                userId
                        ) + ": " + ERROR_NO_RESERVATION
                );
                return Uni.createFrom().failure(new PromotionException(ERROR_NO_RESERVATION));
            }
        }).eventually(() ->
                release(activityId)
        );
    }

    private Uni<Void> reclaim(String activityId, String userId) {
        return redisDataSource.hash(
                String.class,
                String.class,
                String.class
        ).hdel(
                String.join(":", key, activityId),
                userId
        ).chain(result -> {
            if (result > 0) {
                return increaseAmount(activityId).replaceWithVoid();
            }
            return Uni.createFrom().voidItem();
        });
    }

    private class RandomString {
        // avoid to use 0, 1, o, O, l, I that are hard to recognize.
        private final String characters = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        private final int length = 8;

        public String next() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < length; i++) {
                builder.append(characters.charAt((int) (Math.random() * characters.length())));
            }
            return builder.toString();
        }
    }
}
