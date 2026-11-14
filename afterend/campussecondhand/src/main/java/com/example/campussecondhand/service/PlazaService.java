package com.example.campussecondhand.service;

import com.example.campussecondhand.repository.DashboardRepository;
import com.example.campussecondhand.repository.PostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 广场侧栏数据：热门话题、活跃用户。
 *
 * <p>前端 {@code api/plaza.js} 一直在请求 {@code /api/plaza/hot-topics} 与
 * {@code /api/plaza/active-users}，但后端从未提供这两个接口，侧栏长期 404，
 * 只能显示「暂时无法加载热门话题」。这里补齐实现。</p>
 *
 * <p>话题统计口径：扫描最近若干条动态的 {@code tags}（逗号分隔），
 * 按出现次数排序取前 N；活跃用户直接复用首页统计用的「最近活跃用户」查询。</p>
 */
@Service
public class PlazaService {

    /** 参与话题统计的动态条数上限：够用且不会把整表拉进内存 */
    private static final int TAG_SCAN_LIMIT = 500;

    /** 热门话题返回条数 */
    public static final int HOT_TOPICS_LIMIT = 10;

    /** 活跃用户返回条数 */
    public static final int ACTIVE_USERS_LIMIT = 6;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private DashboardRepository dashboardRepository;

    /**
     * 热门话题列表。
     *
     * @param limit 返回条数
     * @return 每项形如 {@code {tag: "考研", count: 12}}
     */
    public List<Map<String, Object>> hotTopics(int limit) {
        List<String> rawTags = postRepository.findRecentTags(TAG_SCAN_LIMIT);
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (String raw : rawTags) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String tag : raw.split(",")) {
                String trimmed = tag.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                counter.merge(trimmed, 1, Integer::sum);
            }
        }

        List<Map<String, Object>> topics = new ArrayList<>();
        counter.entrySet().stream()
                // 次数多的在前；次数相同按话题名字典序，保证同一份数据每次返回顺序一致
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(Math.max(limit, 0))
                .forEach(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("tag", entry.getKey());
                    item.put("count", entry.getValue());
                    topics.add(item);
                });
        return topics;
    }

    /**
     * 活跃用户列表。
     *
     * @param limit 返回条数
     * @return 每项形如 {@code {userId: 7, username: "alice", avatar: null, subtitle: "11-11 20:15"}}
     */
    public List<Map<String, Object>> activeUsers(int limit) {
        List<Map<String, Object>> rows = dashboardRepository.activeUsers(Math.max(limit, 0));
        List<Map<String, Object>> users = new ArrayList<>();
        if (rows == null) {
            return users;
        }
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            // 前端用 userId 作为 key（首页用的是 id，两处字段名不同，这里按广场约定输出 userId）
            item.put("userId", row.get("id"));
            item.put("username", row.get("username"));
            item.put("avatar", row.get("avatar"));
            item.put("subtitle", formatSubtitle(row.get("lastActiveTime")));
            users.add(item);
        }
        return users;
    }

    /**
     * 最近活跃时间 → 侧栏副标题。
     *
     * <p>MyBatis 返回的 {@code lastActiveTime} 可能是 {@code Timestamp}，
     * 也可能被映射成 {@code String}，两种都要能显示。</p>
     */
    private static String formatSubtitle(Object lastActiveTime) {
        if (lastActiveTime instanceof Date date) {
            return new SimpleDateFormat("MM-dd HH:mm").format(date);
        }
        if (lastActiveTime == null) {
            return "最近活跃";
        }
        String text = String.valueOf(lastActiveTime);
        return text.length() > 16 ? text.substring(0, 16) : text;
    }
}
