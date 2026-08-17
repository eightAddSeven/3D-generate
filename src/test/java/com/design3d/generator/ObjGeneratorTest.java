package com.design3d.generator;

import com.design3d.model.layout.RoomLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

class ObjGeneratorTest {

    @Test
    void generatesGroupedObjWithRealDoorAndWindowVoids() throws Exception {
        String json;
        try (var input = Objects.requireNonNull(
                ObjGeneratorTest.class.getResourceAsStream("/sample-room-layout.json"),
                "sample-room-layout.json not found")) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        RoomLayout layout = new ObjectMapper().readValue(json, RoomLayout.class);

        String obj = new String(new ObjGenerator().generate(layout), StandardCharsets.UTF_8);

        assertThat(obj).startsWith("# 3DBuildingModelingEngine JSON to OBJ");
        assertThat(obj).contains("g floor", "g wall_living_north_part_", "g sofa_1");
        assertThat(obj.lines().filter(line -> line.startsWith("v ")).count()).isGreaterThan(8);
        assertThat(obj.lines().filter(line -> line.startsWith("f ")).count()).isGreaterThan(6);
        assertThat(obj).doesNotContain("g opening_");
    }
}
