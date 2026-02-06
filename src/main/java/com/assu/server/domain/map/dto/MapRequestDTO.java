package com.assu.server.domain.map.dto;

import lombok.*;

public class MapRequestDTO {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ViewOnMapDTO {
        private double lng1;
        private double lat1;
        private double lng2;
        private double lat2;
        private double lng3;
        private double lat3;
        private double lng4;
        private double lat4;
    }

}
