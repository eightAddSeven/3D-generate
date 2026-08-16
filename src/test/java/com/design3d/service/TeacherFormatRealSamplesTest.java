package com.design3d.service;

import com.design3d.generator.GltfGenerator;
import com.design3d.generator.ObjGenerator;
import com.design3d.model.layout.RoomLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Optional smoke test for the teacher's real files; pass -Dteacher.samples.dir=... to enable it. */
class TeacherFormatRealSamplesTest {

    @ParameterizedTest
    @ValueSource(strings = {"000005", "000010", "000105", "000198"})
    void convertsRealRecognitionResult(String baseName) throws Exception {
        String sampleDirectory = System.getProperty("teacher.samples.dir");
        Assumptions.assumeTrue(sampleDirectory != null && !sampleDirectory.isBlank());
        Path source = Path.of(sampleDirectory, baseName + ".json");
        Assumptions.assumeTrue(Files.isRegularFile(source));

        ObjectMapper mapper = new ObjectMapper();
        TeacherLayoutAdapter adapter = new TeacherLayoutAdapter(10, 2800);
        RoomLayout layout = adapter.adapt(mapper.readTree(Files.readString(source)));
        byte[] glb = new GltfGenerator(2800, 240).generate(layout);
        byte[] obj = new ObjGenerator().generate(layout);

        assertThat(layout.getRooms().get(0).getWalls()).isNotEmpty();
        assertThat(glb).hasSizeGreaterThan(1000);
        assertThat(new String(glb, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("glTF");
        assertThat(obj).hasSizeGreaterThan(1000);
    }
}
