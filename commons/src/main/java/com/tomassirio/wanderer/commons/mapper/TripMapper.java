package com.tomassirio.wanderer.commons.mapper;

import com.tomassirio.wanderer.commons.domain.Trip;
import com.tomassirio.wanderer.commons.dto.TripDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(
        uses = {
            CommentMapper.class,
            TripUpdateMapper.class,
            TripSettingsMapper.class,
            TripDetailsMapper.class,
            TripDayMapper.class
        })
public interface TripMapper {

    TripMapper INSTANCE = Mappers.getMapper(TripMapper.class);

    @Mapping(
            target = "id",
            expression = "java(trip.getId() != null ? trip.getId().toString() : null)")
    @Mapping(
            target = "userId",
            expression = "java(trip.getUserId() != null ? trip.getUserId().toString() : null)")
    @Mapping(target = "username", ignore = true)
    @Mapping(
            target = "tripPlanId",
            expression =
                    "java(trip.getTripPlanId() != null ? trip.getTripPlanId().toString() : null)")
    TripDTO toDTO(Trip trip);

    @Mapping(
            target = "id",
            expression =
                    "java(tripDTO.id() != null ? java.util.UUID.fromString(tripDTO.id()) : null)")
    @Mapping(
            target = "userId",
            expression =
                    "java(tripDTO.userId() != null ? java.util.UUID.fromString(tripDTO.userId()) : null)")
    @Mapping(
            target = "tripPlanId",
            expression =
                    "java(tripDTO.tripPlanId() != null ? java.util.UUID.fromString(tripDTO.tripPlanId()) : null)")
    @Mapping(target = "comments", ignore = true)
    @Mapping(target = "tripUpdates", ignore = true)
    @Mapping(target = "tripDays", ignore = true)
    Trip toEntity(TripDTO tripDTO);
}
