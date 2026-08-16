package com.design3d.service;

import com.design3d.model.layout.RoomLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomPolygonTopologyTest {

    private final ObjectMapper mapper =
            new ObjectMapper();

    /**
     * 验证：
     *
     * 两个房间存在部分重叠共线边时，
     * 不再重复生成墙体。
     */
    @Test
    void mergesPartiallyOverlappingCollinearRoomEdges()
            throws Exception {

        String json = """
                [
                  {
                    "name": "RoomA",
                    "coordinates": [
                      [0,0],
                      [100,0],
                      [100,100],
                      [0,100]
                    ]
                  },
                  {
                    "name": "RoomB",
                    "coordinates": [
                      [0,100],
                      [50,100],
                      [50,200],
                      [0,200]
                    ]
                  }
                ]
                """;

        RoomPolygonLayoutAdapter adapter =
                new RoomPolygonLayoutAdapter(
                        10,
                        2800,
                        150
                );

        RoomLayout layout =
                adapter.adapt(
                        mapper.readTree(json)
                );

        int wallCount =
                layout.getRooms()
                        .stream()
                        .mapToInt(
                                room ->
                                        room.getWalls()
                                                .size()
                        )
                        .sum();

        /*
         * 原始：
         *
         * 4 + 4 = 8 条边
         *
         * 其中存在部分共享边。
         *
         * 规范化后：
         *
         * 6 条物理墙。
         */
        assertThat(
                wallCount
        ).isEqualTo(6);
    }

    /**
     * 验证真实尺寸元数据可以覆盖默认比例。
     */
    @Test
    void usesLayoutMetadataForPhysicalScaleAndWallThickness()
            throws Exception {

        String json = """
                {
                  "layout_meta": {
                    "width_mm": 5000,
                    "depth_mm": 4000,
                    "wall_thickness_mm": 120,
                    "wall_height_mm": 3000
                  },
                  "raw_room_data": [
                    {
                      "name": "DiningHall_1",
                      "coordinates": [
                        [0,0],
                        [100,0],
                        [100,80],
                        [0,80]
                      ]
                    }
                  ]
                }
                """;

        RoomPolygonLayoutAdapter adapter =
                new RoomPolygonLayoutAdapter(
                        10,
                        2800,
                        150
                );

        RoomLayout layout =
                adapter.adapt(
                        mapper.readTree(json)
                );

        assertThat(
                layout.getLayout()
                        .getWidth()
        ).isEqualTo(5000);

        assertThat(
                layout.getLayout()
                        .getDepth()
        ).isEqualTo(4000);

        assertThat(
                layout.getLayout()
                        .getWallThickness()
        ).isEqualTo(120);

        assertThat(
                layout.getLayout()
                        .getHeight()
        ).isEqualTo(3000);
    }
}