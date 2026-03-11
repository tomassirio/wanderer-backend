package com.tomassirio.wanderer.commons.mapper;

import com.tomassirio.wanderer.commons.domain.TripPlan;
import com.tomassirio.wanderer.commons.dto.TripPlanDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface TripPlanMapper {

    TripPlanMapper INSTANCE = Mappers.getMapper(TripPlanMapper.class);

    @Mapping(
            target = "id",
            expression = "java(tripPlan.getId() != null ? tripPlan.getId().toString() : null)")
    @Mapping(
            target = "userId",
            expression =
                    "java(tripPlan.getUserId() != null ? tripPlan.getUserId().toString() : null)")
    @Mapping(source = "createdTimestamp", target = "createdTimestamp")
    TripPlanDTO toDTO(TripPlan tripPlan);

    @Mapping(
            target = "id",
            expression =
                    "java(tripPlanDTO.id() != null ? java.util.UUID.fromString(tripPlanDTO.id()) : null)")
    @Mapping(
            target = "userId",
            expression =
                    "java(tripPlanDTO.userId() != null ? java.util.UUID.fromString(tripPlanDTO.userId()) : null)")
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "encodedPolyline", ignore = true)
    @Mapping(target = "plannedPolyline", ignore = true)
    @Mapping(target = "polylineUpdatedAt", ignore = true)
    TripPlan toEntity(TripPlanDTO tripPlanDTO);
}
