package com.example.campussecondhand.service;

import com.example.campussecondhand.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 图片落盘（P1-12）。
 *
 * <p><b>此前图片是 base64 直接入库的：</b>{@code products.images} / {@code posts.images}
 * 是 MEDIUMTEXT、{@code users.avatar} 也是 MEDIUMTEXT，前端用 canvas 压到 800px 后
 * {@code toDataURL('image/jpeg', 0.7)} 塞进 JSON。后果：</p>
 * <ul>
 *   <li>一条商品详情响应里带着整张 base64 图（头像也是），动辄几十 KB —— 我在冒烟时
 *       一次 {@code GET /api/orders/create} 的响应被打印出三千多字符，几乎全是头像；</li>
 *   <li>为了容纳 base64，请求体上限被抬到 50MB（见 application.properties）；</li>
 *   <li>列表接口反复把同一张图从数据库搬进内存再序列化。</li>
 * </ul>
 *
 * <p><b>现在只存路径</b>（{@code /uploads/2026/10/<32位随机>.jpg}），文件按年月分目录落盘，
 * 由 {@code WebMvcConfig} 以静态资源对外提供。旧的 base64 行不需要清洗也能正常显示——
 * {@code <img src>} 对 data URL 和路径一视同仁。</p>
 *
 * <p><b>安全约束（都在这里，不指望前端）：</b></p>
 * <ol>
 *   <li>类型白名单 + <b>文件头嗅探</b>：客户端声明的 Content-Type 可以随便伪造，
 *       把 .jsp/.svg 改成 image/png 很容易，所以按魔术字节判断真实格式，
 *       且扩展名取自嗅探结果而不是用户文件名；</li>
 *   <li>大小上限；</li>
 *   <li><b>文件名由服务端生成</b>（UUID），用户提供的名字一律不进磁盘路径 ——
 *       否则 {@code ../../} 之类就能写出目录之外；</li>
 *   <li>落盘目录做 normalize + startsWith 校验，双保险防穿越。</li>
 * </ol>
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    /** 允许的图片类型 → 落盘扩展名。只认这四种，SVG 也在外（它是 XML，可携带脚本） */
    private static final Map<String, String> TYPE_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp");

    private static final DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path root;
    private final long maxBytes;

    public UploadService(@Value("${app.upload.dir:uploads}") String uploadDir,
                         @Value("${app.upload.max-bytes:2097152}") long maxBytes) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    /** 对外暴露落盘根目录，供 WebMvcConfig 注册静态资源映射 */
    public Path getRoot() {
        return root;
    }

    /**
     * 保存一张图片。
     *
     * @param file 上传的文件
     * @return 可直接存库、也可直接给 {@code <img src>} 用的路径，如
     *         {@code /uploads/2026/10/9f2c....jpg}
     * @throws BadRequestException 空文件、超限、类型不在白名单、或内容与声明的类型不符
     */
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择要上传的图片");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("图片不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }

        String declared = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT).trim();
        if (!TYPE_TO_EXT.containsKey(declared)) {
            throw new BadRequestException("只支持 JPG / PNG / GIF / WebP 格式的图片");
        }

        byte[] head = readHead(file);
        String sniffed = sniff(head);
        if (sniffed == null) {
            throw new BadRequestException("文件内容不是有效的图片");
        }
        if (!sniffed.equals(declared)) {
            // 声明的类型与真实内容不一致：典型的"改扩展名绕过白名单"，
            // 以嗅探结果为准并明确拒绝，而不是悄悄按真实类型存下来
            log.warn("上传文件的声明类型与内容不符: declared={}, sniffed={}", declared, sniffed);
            throw new BadRequestException("文件内容与图片格式不符");
        }

        String datePath = LocalDate.now().format(DIR_FORMAT);
        Path dir = root.resolve(datePath).normalize();
        if (!dir.startsWith(root)) {
            // 日期由系统生成，正常永远成立；这里是防"将来有人改成用请求参数拼路径"
            throw new BadRequestException("非法的上传路径");
        }

        // 文件名完全由服务端生成，用户提供的名字不参与
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + TYPE_TO_EXT.get(sniffed);
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new BadRequestException("非法的上传路径");
        }

        try {
            Files.createDirectories(dir);
            file.transferTo(target);
        } catch (IOException e) {
            log.error("图片落盘失败: target={}", target, e);
            throw new IllegalStateException("图片保存失败", e);
        }

        String url = "/uploads/" + datePath + "/" + fileName;
        log.info("图片已保存: {} ({} bytes)", url, file.getSize());
        return url;
    }

    /** 只读文件头用于嗅探，不把整张图读进内存 */
    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(12);
        } catch (IOException e) {
            throw new BadRequestException("无法读取上传的文件");
        }
    }

    /**
     * 按魔术字节判断真实图片格式。
     *
     * <p>不引入 ImageIO 是因为它只认它支持的编码器，且对损坏文件的异常处理更复杂；
     * 这里只需要"是不是这四种之一"，几个字节前缀就够了。</p>
     *
     * @return MIME 类型；不是已知图片时返回 null
     */
    static String sniff(byte[] head) {
        if (head == null || head.length < 4) {
            return null;
        }
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "image/png";
        }
        if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
            return "image/gif";
        }
        if (head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
