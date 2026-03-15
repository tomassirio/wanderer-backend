package com.tomassirio.wanderer.commons.mapper;

import com.tomassirio.wanderer.commons.domain.Notification;
import com.tomassirio.wanderer.commons.dto.NotificationDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * MapStruct mapper for converting between Notification entities and DTOs.
 *
 * @since 0.10.0
 */
@Mapper
public interface NotificationMapper {

    NotificationMapper INSTANCE = Mappers.getMapper(NotificationMapper.class);

    @Mapping(
            target = "id",
            expression =
                    "java(notification.getId() != null ? notification.getId().toString() : null)")
    @Mapping(
            target = "recipientId",
            expression =
                    "java(notification.getRecipientId() != null ? notification.getRecipientId().toString() : null)")
    @Mapping(
            target = "actorId",
            expression =
                    "java(notification.getActorId() != null ? notification.getActorId().toString() : null)")
    @Mapping(
            target = "type",
            expression =
                    "java(notification.getType() != null ? notification.getType().name() : null)")
    @Mapping(
            target = "referenceId",
            expression =
                    "java(notification.getReferenceId() != null ? notification.getReferenceId().toString() : null)")
    NotificationDTO toDTO(Notification notification);
}

