package com.design3d.generator;

import com.design3d.model.layout.RoomLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ObjGeneratorTest {

    @Test
    void generatesGroupedObjWithRealDoorAndWindowVoids() throws Exception {
        String json = Files.readString(Path.of("..", "docs", "sample-room-layout.json"));
        RoomLayout layout = new ObjectMapper().readValue(json, RoomLayout.class);

        String obj = new String(new ObjGenerator().generate(layout), StandardCharsets.UTF_8);

        assertThat(obj).startsWith("# 3DBuildingModelingEngine JSON to OBJ");
        assertThat(obj).contains("g floor", "g wall_living_north_part_", "g sofa_1");
        assertThat(obj.lines().filter(line -> line.startsWith("v ")).count()).isGreaterThan(8);
        assertThat(obj.lines().filter(line -> line.startsWith("f ")).count()).isGreaterThan(6);
        assertThat(obj).doesNotContain("g opening_");
    }
}
