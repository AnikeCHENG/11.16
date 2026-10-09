package com.example.campussecondhand.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.campussecondhand.entity.Post;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 大厅帖子作者的字段白名单（P0 回归防线）。
 *
 * <p><b>这个漏洞是怎么发生的：</b>{@code Post.author} 早期直接持有
 * {@code User} 实体，而 {@code GET /api/posts} 不需要登录。实测未登录调一次，
 * 每条帖子的作者都会带着 email / phone / qq / wechat / role 一起返回——
 * 遍历一遍大厅等于拿到全站同学的联系方式和后台角色。
 * {@code password} / {@code studentNo} / {@code realName} 当时没漏，
 * 纯粹是因为它们标了 {@code WRITE_ONLY}，属于侥幸而非设计。</p>
 *
 * <p><b>为什么不能改 {@code User} 加 {@code @JsonIgnore}：</b>
 * {@code UserSerializationTest#harmlessFieldsStillSerialized} 断言
 * email 与 phone 必须能序列化——管理后台依赖它们。全局屏蔽会把后台一起打瞎。
 * 所以做法是把 {@code Post.author} 换成显式的 {@link PostAuthorView}。</p>
 *
 * <p><b>本测试要钉死的是「白名单」而不是「黑名单」：</b>
 * 只断言「不含 email」是不够的，将来有人往 {@code PostAuthorView} 加一个
 * {@code qq} 字段，黑名单不会红。所以这里额外用反射锁死了允许的字段集合——
 * 任何人往 view 里加字段都必须先改这个测试，从而被迫面对「这是不是该外泄」。</p>
 */
class PostAuthorViewSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private PostAuthorView fullView() {
        return new PostAuthorView(7L, "alice", "data:image/jpeg;base64,AAAA", "计算机学院", true);
    }

    /** 构造一个字段齐全的 User，验证「即便实体里有，也不该出现在帖子里」 */
    private Post postWithAuthor() {
        Post p = new Post();
        p.setId(100L);
        p.setUserId(7L);
        p.setContent("转让一本数据结构教材");
        p.setAuthor(fullView());
        return p;
    }

    @Test
    @DisplayName("author 只序列化白名单内的字段")
    void authorExposesOnlyWhitelistedFields() throws Exception {
        String json = mapper.writeValueAsString(postWithAuthor());
        ObjectNode author = (ObjectNode) mapper.readTree(json).get("author");

        assertThat(author).isNotNull();
        assertThat(fieldNames(author))
                .containsExactlyInAnyOrder("id", "username", "avatar", "location", "studentVerified");
    }

    @Test
    @DisplayName("作者的个人信息与后台角色绝不出现在大厅帖子里")
    void authorPiiNeverLeaked() throws Exception {
        String json = mapper.writeValueAsString(postWithAuthor());

        // 这些是 User 实体上真实存在的字段名；一旦视图被改回实体，它们就会全部冒出来
        assertThat(json).doesNotContain("email");
        assertThat(json).doesNotContain("phone");
        assertThat(json).doesNotContain("qq");
        assertThat(json).doesNotContain("wechat");
        assertThat(json).doesNotContain("role");
        assertThat(json).doesNotContain("bio");
        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain("studentNo");
        assertThat(json).doesNotContain("realName");
    }

    @Test
    @DisplayName("白名单只有 5 个字段——新增字段必须先改这个测试")
    void viewHasExactlyFiveFields() {
        List<String> declared = Arrays.stream(PostAuthorView.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toList());

        assertThat(declared).containsExactlyInAnyOrder(
                "id", "username", "avatar", "location", "studentVerified");
    }

    @Test
    @DisplayName("认证状态照常返回，否则大厅昵称旁的徽章会失效")
    void studentVerifiedFlagStillSerialized() throws Exception {
        ObjectNode author = (ObjectNode) mapper.readTree(
                mapper.writeValueAsString(postWithAuthor())).get("author");

        assertThat(author.get("studentVerified").asBoolean()).isTrue();
        assertThat(author.get("username").asText()).isEqualTo("alice");
    }

    @Test
    @DisplayName("未认证用户为 false，不是省略字段")
    void unverifiedUserIsExplicitFalse() throws Exception {
        Post p = postWithAuthor();
        p.setAuthor(new PostAuthorView(8L, "bob", null, null, false));

        ObjectNode author = (ObjectNode) mapper.readTree(mapper.writeValueAsString(p)).get("author");

        // 前端是 `v-if="post.author?.studentVerified"` 取值，
        // 字段缺席与 false 行为一致，但显式返回能让前端不必判空，语义也更清楚
        assertThat(author.has("studentVerified")).isTrue();
        assertThat(author.get("studentVerified").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("作者被删除时 author 为显式 null，前端隐藏昵称行")
    void nullAuthorSerializesCleanly() throws Exception {
        Post p = postWithAuthor();
        p.setAuthor(null);

        ObjectNode root = (ObjectNode) mapper.readTree(mapper.writeValueAsString(p));

        // 显式返回 null 而非省略字段：与 Post.product 在非商品动态下的行为一致，
        // 前端 `post.author?.username` 的可选链能直接兜住，无需判空
        assertThat(root.get("author").isNull()).isTrue();
        assertThat(root.get("content").asText()).isEqualTo("转让一本数据结构教材");
    }

    private static Set<String> fieldNames(ObjectNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
