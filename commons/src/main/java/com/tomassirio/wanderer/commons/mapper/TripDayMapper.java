package com.tomassirio.wanderer.commons.mapper;

import com.tomassirio.wanderer.commons.domain.TripDay;
import com.tomassirio.wanderer.commons.dto.TripDayDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface TripDayMapper {

    TripDayMapper INSTANCE = Mappers.getMapper(TripDayMapper.class);

    @Mapping(
            target = "id",
            expression = "java(tripDay.getId() != null ? tripDay.getId().toString() : null)")
    @Mapping(
            target = "tripId",
            expression =
                    "java(tripDay.getTrip() != null && tripDay.getTrip().getId() != null ? tripDay.getTrip().getId().toString() : null)")
    TripDayDTO toDTO(TripDay tripDay);
}
