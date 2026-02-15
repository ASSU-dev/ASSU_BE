package com.assu.server.domain.map.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelectedPlacePayload {
    private String placeId;
    private String name;
    private String address;
    private String roadAddress;
    private Double latitude;
    private Double longitude;
}
