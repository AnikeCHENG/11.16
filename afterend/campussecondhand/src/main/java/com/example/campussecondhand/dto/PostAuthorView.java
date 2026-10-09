package com.example.campussecondhand.dto;

/**
 * 帖子作者的对外视图。
 *
 * <p><b>存在的唯一理由：不要把 {@code User} 实体直接序列化给前端。</b></p>
 *
 * <p>大厅帖子接口 {@code GET /api/posts} 不需要登录，而 {@code Post.author}
 * 早期直接持有 {@code User} 实体，导致每条帖子的作者
 * {@code email / phone / qq / wechat / role / bio / location}
 * 全部随响应返回——任何人遍历一遍大厅就能拿到全站同学的联系方式，
 * 而管理后台之外的任何页面都不需要这些字段。</p>
 *
 * <p>这里改为显式挑出前端真正会读的字段（{@link VerifiedBadge} 需要的
 * 认证状态、{@code PostCard} 需要的昵称头像位置），从根上消除
 * 「加一个字段就默认外泄」的隐患：以后 {@code User} 再加字段，
 * 也不会自动出现在别人的帖子里。</p>
 *
 * <p>注意 {@code User.studentNo} / {@code realName} 标了
 * {@code JsonProperty.Access.WRITE_ONLY}，本视图因此也<b>不含</b>学号与真实姓名——
 * 作者的学号属于个人信息，认证徽章只需要一个布尔标记就够了。</p>
 */
public class PostAuthorView {

    /** 作者用户 ID，前端点击昵称跳个人主页时用 */
    private Long id;

    /** 昵称（users 表暂无 nickname 列，当前以 username 兜底） */
    private String username;

    /** 头像（base64 或外链） */
    private String avatar;

    /** 归属地，如「计算机学院」；为空时前端隐藏位置行 */
    private String location;

    /** 是否已通过学生认证——大厅昵称旁渲染 🎓 徽章的唯一依据 */
    private boolean studentVerified;

    public PostAuthorView() {
    }

    public PostAuthorView(Long id, String username, String avatar, String location,
                          boolean studentVerified) {
        this.id = id;
        this.username = username;
        this.avatar = avatar;
        this.location = location;
        this.studentVerified = studentVerified;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public boolean isStudentVerified() { return studentVerified; }
    public void setStudentVerified(boolean studentVerified) { this.studentVerified = studentVerified; }
}
