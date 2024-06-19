package io.archer.api;

import io.archer.ActivityOptions;
import io.archer.PromotionChecker;
import io.archer.dba.cache.PromotionCache;
import io.archer.exception.PromotionException;
import io.archer.queue.IssuedPromotionQueue;
import io.archer.queue.ReservedPromotionQueue;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;

@Path("/activities")
public class ActivityResource {

    @Inject
    private ActivityOptions options;

    @Inject
    private IssuedPromotionQueue issuedQueue;

    @Inject
    private PromotionCache cache;

    @Inject
    private PromotionChecker checker;

    @Inject
    private ReservedPromotionQueue reservedQueue;


    private boolean isAvailable(OffsetTime start, OffsetTime end) {
        return !OffsetTime.now().isAfter(start) || !OffsetTime.now().isBefore(end);
    }

    @POST
    @Path("/{activityId}/reserve")
    @Consumes("application/json")
    @Produces("application/json")
    public Uni<Response> reserve(@PathParam("activityId") String activityId, JsonObject body) {
        if (
                isAvailable(
                        options.reservingTime(),
                        options.reservingTime().plus(options.reservingDuration())
                )
        ) {
            return Uni.createFrom().item(
                    Response.status(Response.Status.FORBIDDEN)
                            .entity(
                                    Json.createObjectBuilder()
                                            .add(
                                                    "message",
                                                    "The activity is not available for reservation."
                                            ).build()
                            ).build());
        }
        return cache.reserve(
                activityId,
                body.getString("user")
        ).chain(promotion -> {
            reservedQueue.offer(promotion);
            return Uni.createFrom().item(
                    Response.ok(
                            Json.createObjectBuilder()
                                    .add(
                                            "id",
                                            promotion.getId().toString()
                                    ).add(
                                            "reservedTime",
                                            DateTimeFormatter.ISO_INSTANT.format(promotion.getReservedTime())
                                    ).build()
                    ).build());
        }).invoke(r -> {
            long delay = (
                    options.reservingTime()
                            .plus(options.reservingDuration())
                            .toEpochSecond(LocalDate.now()) * 1000
            ) - Instant.now().toEpochMilli();
            checker.setTimer(
                    activityId,
                    delay < 0 ? 0 : delay
            );
        }).onFailure(PromotionException.class).recoverWithItem(cause ->
                Response.status(Response.Status.FORBIDDEN)
                        .entity(
                                Json.createObjectBuilder()
                                        .add(
                                                "message",
                                                cause.getMessage()
                                        ).build()
                        ).build()
        ).onFailure().recoverWithItem(cause ->
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(
                                Json.createObjectBuilder()
                                        .add(
                                                "message",
                                                cause.getMessage()
                                        ).build()
                        ).build()
        );
    }

    @POST
    @Path("/{activityId}/issue")
    @Consumes("application/json")
    @Produces("application/json")
    public Uni<Response> issue(@PathParam("activityId") String activityId, JsonObject body) {
        if (
                isAvailable(
                        options.issuingTime(),
                        options.issuingTime().plus(options.issuingDuration())
                )
        ) {
            return Uni.createFrom().item(
                    Response.status(Response.Status.FORBIDDEN)
                            .entity(
                                    Json.createObjectBuilder()
                                            .add(
                                                    "message",
                                                    "The activity is not available for issuing."
                                            ).build()
                            ).build());
        }
        return cache.issue(
                activityId,
                body.getString("user")
        ).chain(promotion -> {
            issuedQueue.offer(promotion);
            return Uni.createFrom().item(
                    Response.ok(
                            Json.createObjectBuilder()
                                    .add(
                                            "code",
                                            promotion.getCode()
                                    ).add(
                                            "issuedTime",
                                            DateTimeFormatter.ISO_INSTANT.format(promotion.getIssuedTime())
                                    ).build()
                    ).build());
        }).onFailure(PromotionException.class).recoverWithItem(cause ->
                Response.status(Response.Status.FORBIDDEN)
                        .entity(
                                Json.createObjectBuilder()
                                        .add(
                                                "message",
                                                cause.getMessage()
                                        ).build()
                        ).build()
        ).onFailure().recoverWithItem(cause ->
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(
                                Json.createObjectBuilder()
                                        .add(
                                                "message",
                                                cause.getMessage()
                                        ).build()
                        ).build()
        );
    }
}
