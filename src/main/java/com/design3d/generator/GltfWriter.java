package com.design3d.generator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * glTF 2.0 Binary (.glb) 文件写入器
 * <p>
 * GLB 格式: 12-byte header + JSON chunk + Binary chunk
 */
public class GltfWriter {

    private static final int GLTF_MAGIC = 0x46546C67; // "glTF"
    private static final int GLTF_VERSION = 2;
    private static final int CHUNK_TYPE_JSON = 0x4E4F534A;  // "JSON"
    private static final int CHUNK_TYPE_BIN = 0x004E4942;   // "BIN\0"

    /**
     * 写入 .glb 文件
     *
     * @param json     glTF JSON 字符串
     * @param binData  二进制顶点数据
     * @return 完整的 .glb 文件字节数组
     */
    public static byte[] writeGlb(String json, byte[] binData) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 1) 确保 JSON 和 BIN 都 4 字节对齐
        byte[] jsonBytes = json.getBytes("UTF-8");
        jsonBytes = padTo4(jsonBytes);

        byte[] binBytes = binData != null ? padTo4(binData) : new byte[0];

        // 2) 计算总长度
        int totalLength = 12 + 8 + jsonBytes.length + 8 + binBytes.length;

        // 3) 写入 12-byte header
        ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(GLTF_MAGIC);
        header.putInt(GLTF_VERSION);
        header.putInt(totalLength);
        out.write(header.array());

        // 4) 写入 JSON chunk
        ByteBuffer jsonChunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        jsonChunkHeader.putInt(jsonBytes.length);
        jsonChunkHeader.putInt(CHUNK_TYPE_JSON);
        out.write(jsonChunkHeader.array());
        out.write(jsonBytes);

        // 5) 写入 BIN chunk
        ByteBuffer binChunkHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        binChunkHeader.putInt(binBytes.length);
        binChunkHeader.putInt(CHUNK_TYPE_BIN);
        out.write(binChunkHeader.array());
        out.write(binBytes);

        return out.toByteArray();
    }

    /**
     * 4 字节对齐填充
     */
    private static byte[] padTo4(byte[] data) {
        int remainder = data.length % 4;
        if (remainder == 0) return data;
        byte[] padded = new byte[data.length + (4 - remainder)];
        System.arraycopy(data, 0, padded, 0, data.length);
        // 剩余字节填充空格（JSON）或 0（BIN）
        for (int i = data.length; i < padded.length; i++) {
            padded[i] = 0x20; // 空格填充
        }
        return padded;
    }
}
