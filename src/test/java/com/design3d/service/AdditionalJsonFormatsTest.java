package com.design3d.service;

import com.design3d.generator.GltfGenerator;
import com.design3d.generator.ObjGenerator;
import com.design3d.model.layout.RoomLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionalJsonFormatsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final TeacherLayoutAdapter teacher = new TeacherLayoutAdapter(10, 2800);
    private final OcrFurnitureExtractor furnitureExtractor = new OcrFurnitureExtractor();
    private final MixedRecognitionLayoutAdapter mixed = new MixedRecognitionLayoutAdapter(
            teacher, furnitureExtractor, 10);
    private final RoomPolygonLayoutAdapter polygons = new RoomPolygonLayoutAdapter(10, 2800, 240);
    private final LayoutGenerationService service = new LayoutGenerationService(
            new GltfGenerator(2800, 240), new ObjGenerator(), mapper, validator, teacher, mixed, polygons);

    @Test
    void recognizesMixedFormatByStructureAndIgnoresOcr() throws Exception {
        String json = """
                [
                  [[0, 0], [200, 0], 12, 3],
                  [[200, 0], [200, 200], 12, 2],
                  [[[200, 200], [120, 200], [200, 120]], 1],
                  [[0, 200], [80, 200], [0, 120], 1],
                  [[[10,10],[30,10],[30,20],[10,20]], "任意 OCR 文本"]
                ]
                """;
        RoomLayout layout = service.parseAndValidate(json);
        assertThat(layout.getRooms().get(0).getWalls()).hasSize(4);
        assertThat(layout.getRooms().get(0).getName()).contains("未识别到可建模家具");
    }

    @Test
    void extractsFurnitureNameDimensionsPositionAndFiltersFormText() throws Exception {
        String json = """
                [
                  [[0, 0], [600, 0], 12, 3],
                  [[600, 0], [600, 600], 12, 3],
                  [[[100,100],[180,100],[180,125],[100,125]], "双星水池"],
                  [[[105,130],[195,130],[195,150],[105,150]], "1500*750*800"],
                  [[[250,200],[360,200],[360,225],[250,225]], "四层条档货架"],
                  [[[255,230],[365,230],[365,250],[255,250]], "1200×500×1800"],
                  [[[20,500],[130,500],[130,525],[20,525]], "申请人确认签字"]
                ]
                """;

        RoomLayout layout = service.parseAndValidate(json);
        List<RoomLayout.Furniture> furniture = layout.getRooms().get(0).getFurniture();

        assertThat(furniture).hasSize(2);
        assertThat(furniture).filteredOn(item -> "sink".equals(item.getType())).singleElement()
                .satisfies(item -> {
                    assertThat(item.getSize().getWidth()).isEqualTo(1500);
                    assertThat(item.getSize().getDepth()).isEqualTo(750);
                    assertThat(item.getSize().getHeight()).isEqualTo(800);
                });
        assertThat(furniture).extracting(RoomLayout.Furniture::getType)
                .containsExactlyInAnyOrder("sink", "shelf");
    }

    @Test
    void recognizesBothBareAndWrappedRoomPolygonFormats() throws Exception {
        String rooms = """
                [{"name":"AnyBedroom_99","coordinates":[[0,0],[200,0],[200,150],[0,150]]}]
                """;
        RoomLayout bare = service.parseAndValidate(rooms);
        RoomLayout wrapped = service.parseAndValidate("{\"raw_room_data\":" + rooms
                + ",\"floorplan_svg\":\"not used for modeling\"}");
        assertThat(bare.getRooms().get(0).getType()).isEqualTo("bedroom");
        assertThat(wrapped.getRooms().get(0).getWalls()).hasSize(4);
    }

    @Test
    void differentLayoutsProduceDifferentGlbFiles() throws Exception {
        String square = """
                [{"name":"RoomA","coordinates":[[0,0],[100,0],[100,100],[0,100]]}]
                """;
        String rectangle = """
                [{"name":"RoomB","coordinates":[[0,0],[300,0],[300,120],[0,120]]}]
                """;

        byte[] first = service.generateGlbFromJson(square);
        byte[] second = service.generateGlbFromJson(rectangle);

        assertThat(first).isNotEqualTo(second);
    }

    @ParameterizedTest
    @MethodSource("realSamples")
    void convertsEveryProvidedRealSample(Path source) throws Exception {
        if (!Files.isRegularFile(source)) return;
        String json = Files.readString(source);
        RoomLayout layout = service.parseAndValidate(json);
        byte[] glb = service.generateGlb(layout);
        byte[] obj = service.generateObj(layout);
        Path verifiedDirectory = Path.of("target", "verified-models");
        Files.createDirectories(verifiedDirectory);
        Files.write(verifiedDirectory.resolve(source.getFileName().toString().replaceFirst("\\.json$", ".glb")), glb);
        Files.write(verifiedDirectory.resolve(source.getFileName().toString().replaceFirst("\\.json$", ".obj")), obj);
        assertThat(layout.getRooms()).isNotEmpty();
        assertThat(new String(glb, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("glTF");
        assertThat(obj).hasSizeGreaterThan(1000);
    }

    static Stream<Path> realSamples() {
        String recognitionDirectory = System.getProperty("additional.recognition.samples.dir");
        String polygonDirectory = System.getProperty("additional.polygon.samples.dir");
        if (recognitionDirectory == null || polygonDirectory == null) {
            return Stream.of(Path.of("__optional_samples_not_configured__"));
        }
        Path recognition = Path.of(recognitionDirectory);
        Path polygons = Path.of(polygonDirectory);
        return Stream.of(recognition.resolve("1003_04.json"), recognition.resolve("1024_04.json"),
                recognition.resolve("1116_00.json"), polygons.resolve("floorplan-for-3d.json"),
                polygons.resolve("floorplan-response.json"));
    }
}
