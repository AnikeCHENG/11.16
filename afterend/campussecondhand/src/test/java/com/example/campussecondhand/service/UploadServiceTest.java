package com.example.campussecondhand.service;

import com.example.campussecondhand.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 图片落盘的校验与命名（P1-12）。
 *
 * <p>三条必须由服务端兜住的约束：类型白名单（并按文件头嗅探真实格式）、
 * 大小上限、<b>文件名由服务端生成</b>（用户给的名字一律不进磁盘路径）。</p>
 */
class UploadServiceTest {

    @TempDir
    Path tempDir;

    private UploadService service() {
        return new UploadService(tempDir.toString(), 1024 * 1024);
    }

    /** 最小可用的 PNG 文件头 */
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };
    private static final byte[] JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    @Test
    @DisplayName("正常 PNG：按 yyyy/MM 落盘，返回 /uploads 路径，文件名是 32 位十六进制")
    void stores_png_with_server_generated_name() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "我的照片.png", "image/png", PNG);

        String url = service().storeImage(file);

        assertThat(url).matches("/uploads/\\d{4}/\\d{2}/[0-9a-f]{32}\\.png");
        Path stored = tempDir.resolve(url.substring("/uploads/".length()));
        assertThat(stored).exists();
        assertThat(Files.readAllBytes(stored)).isEqualTo(PNG);
    }

    @Test
    @DisplayName("用户给的文件名不参与磁盘路径（防 ../ 穿越）")
    void ignores_client_file_name() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../../evil.png", "image/png", PNG);

        String url = service().storeImage(file);

        assertThat(url).doesNotContain("evil").doesNotContain("..");
        assertThat(tempDir.resolve(url.substring("/uploads/".length())).normalize())
                .startsWith(tempDir);
    }

    @Test
    @DisplayName("空文件、超限文件被拒")
    void rejects_empty_and_oversized() {
        assertThatThrownBy(() -> service().storeImage(
                new MockMultipartFile("file", "a.png", "image/png", new byte[0])))
                .isInstanceOf(BadRequestException.class);

        byte[] tooBig = new byte[1024 * 1024 + 1];
        System.arraycopy(PNG, 0, tooBig, 0, PNG.length);
        assertThatThrownBy(() -> service().storeImage(
                new MockMultipartFile("file", "a.png", "image/png", tooBig)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("1MB");
    }

    @Test
    @DisplayName("非图片类型直接拒绝（SVG 也不在白名单里：它是 XML，可携带脚本）")
    void rejects_non_image_types() {
        for (String type : new String[]{"text/plain", "application/pdf", "image/svg+xml", null}) {
            assertThatThrownBy(() -> service().storeImage(
                    new MockMultipartFile("file", "a.svg", type, PNG)))
                    .as("类型 %s 应被拒绝", type)
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    @DisplayName("声明类型与文件内容不符：拒绝（改扩展名绕过白名单的典型手法）")
    void rejects_content_type_mismatch() {
        // 内容其实是 PNG，却声称是 JPEG
        MockMultipartFile lying = new MockMultipartFile("file", "a.jpg", "image/jpeg", PNG);

        assertThatThrownBy(() -> service().storeImage(lying))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不符");
    }

    @Test
    @DisplayName("文件头不是任何已知图片：拒绝")
    void rejects_non_image_bytes() {
        MockMultipartFile fake = new MockMultipartFile(
                "file", "a.png", "image/png", "<?php echo 1; ?>".getBytes());

        assertThatThrownBy(() -> service().storeImage(fake))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("JPEG 也能存，扩展名按嗅探结果（jpg）")
    void stores_jpeg() {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpeg", "image/jpeg", JPEG);

        assertThat(service().storeImage(file)).endsWith(".jpg");
    }
}
