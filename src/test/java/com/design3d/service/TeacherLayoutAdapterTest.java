package com.design3d.service;

import com.design3d.model.layout.RoomLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeacherLayoutAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TeacherLayoutAdapter adapter = new TeacherLayoutAdapter(10, 2800);

    @Test
    void adaptsDoorWallAndWindowRecords() throws Exception {
        String json = """
                [
                  [[[100, 200], [80, 0], [0, -80]], 1],
                  [[[0, 200], [100, 200], 12], 2],
                  [[[180, 200], [500, 200], 10], 2],
                  [[[250, 200], [350, 200], 8], 3]
                ]
                """;

        RoomLayout result = adapter.adapt(objectMapper.readTree(json));

        assertThat(result.getUnit()).isEqualTo("mm");
        assertThat(result.getLayout().getWallThickness()).isEqualTo(110);
        assertThat(result.getRooms()).singleElement().satisfies(room -> {
            assertThat(room.getWalls()).hasSize(3);
            assertThat(room.getWalls().stream().flatMap(wall -> wall.getOpenings().stream()))
                    .extracting(RoomLayout.Opening::getType)
                    .containsExactlyInAnyOrder("door", "window");
        });
    }

    @Test
    void rejectsUnknownTypeWithUsefulMessage() throws Exception {
        assertThatThrownBy(() -> adapter.adapt(objectMapper.readTree("[[[[0,0],[1,1],1],9]]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的类型编号: 9");
    }
}
