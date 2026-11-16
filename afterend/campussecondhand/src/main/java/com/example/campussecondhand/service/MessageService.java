package com.example.campussecondhand.service;

import com.example.campussecondhand.entity.Message;
import com.example.campussecondhand.entity.User;
import com.example.campussecondhand.repository.MessageRepository;
import com.example.campussecondhand.repository.ProductRepository;
import com.example.campussecondhand.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    public List<Message> findByReceiverId(Long userId) {
        List<Message> messages = messageRepository.findByReceiverId(userId);
        messages.forEach(m -> {
            if (m.getSender() == null) {
                m.setSender(userRepository.selectById(m.getSenderId()));
            }
        });
        return messages;
    }

    public List<Message> findBySenderId(Long userId) {
        List<Message> messages = messageRepository.findBySenderId(userId);
        messages.forEach(m -> {
            if (m.getReceiver() == null) {
                m.setReceiver(userRepository.selectById(m.getReceiverId()));
            }
        });
        return messages;
    }

    public List<Message> findConversation(Long user1Id, Long user2Id) {
        List<Message> messages = messageRepository.findConversation(user1Id, user2Id);
        messageRepository.markConversationRead(user2Id, user1Id);
        messages.forEach(m -> {
            if (m.getSender() == null) {
                m.setSender(userRepository.selectById(m.getSenderId()));
            }
            if (m.getReceiver() == null) {
                m.setReceiver(userRepository.selectById(m.getReceiverId()));
            }
        });
        return messages;
    }

    public List<java.util.Map<String, Object>> findConversations(Long userId) {
        List<Message> received = messageRepository.findByReceiverId(userId);
        List<Message> sent = messageRepository.findBySenderId(userId);
        java.util.Map<Long, java.util.Map<String, Object>> convMap = new java.util.HashMap<>();
        received.forEach(m -> {
            User other = userRepository.selectById(m.getSenderId());
            m.setSender(other);
            putConversation(convMap, userId, m.getSenderId(),
                    other != null ? other.getUsername() : "用户" + m.getSenderId(),
                    other != null ? other.getAvatar() : null,
                    other != null && other.isStudentVerifiedUser(), m, true);
        });
        sent.forEach(m -> {
            User other = userRepository.selectById(m.getReceiverId());
            m.setReceiver(other);
            putConversation(convMap, userId, m.getReceiverId(),
                    other != null ? other.getUsername() : "用户" + m.getReceiverId(),
                    other != null ? other.getAvatar() : null,
                    other != null && other.isStudentVerifiedUser(), m, false);
        });
        return convMap.values().stream()
                .sorted((a, b) -> {
                    Object t1 = a.get("lastMessageTime");
                    Object t2 = b.get("lastMessageTime");
                    if (t1 == null && t2 == null) return compareLastId(b, a);
                    if (t1 == null) return 1;
                    if (t2 == null) return -1;
                    int cmp = ((java.time.LocalDateTime) t2).compareTo((java.time.LocalDateTime) t1);
                    // created_time 只精确到秒：同一秒内多条消息按自增 id 兜底，避免预览/排序错乱
                    return cmp != 0 ? cmp : compareLastId(b, a);
                })
                .map(m -> {
                    // 内部排序字段，不返回给前端
                    m.remove("lastMessageId");
                    return m;
                })
                .toList();
    }

    private static int compareLastId(java.util.Map<String, Object> a, java.util.Map<String, Object> b) {
        long idA = a.get("lastMessageId") instanceof Number n ? n.longValue() : 0L;
        long idB = b.get("lastMessageId") instanceof Number n ? n.longValue() : 0L;
        return Long.compare(idA, idB);
    }

    private void putConversation(java.util.Map<Long, java.util.Map<String, Object>> convMap, Long meId, Long otherId, String username, String avatar, boolean studentVerified, Message msg, boolean fromThem) {
        java.util.Map<String, Object> conv = convMap.computeIfAbsent(otherId, k -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
map.put("otherUserId", otherId);
        map.put("userId", otherId);
        map.put("username", username);
        map.put("avatar", avatar);
        // 聊天窗口顶部的认证徽章需要这个字段
        map.put("studentVerified", studentVerified);
            map.put("lastMessage", "");
            map.put("lastMessageTime", null);
            map.put("lastMessageId", 0L);
            map.put("unreadCount", 0);
            return map;
        });
        long lastId = conv.get("lastMessageId") instanceof Number n ? n.longValue() : 0L;
        long msgId = msg.getId() == null ? 0L : msg.getId();
        boolean newer = msgId > 0 ? msgId > lastId
                : msg.getCreatedTime() != null && (conv.get("lastMessageTime") == null
                    || msg.getCreatedTime().isAfter((java.time.LocalDateTime) conv.get("lastMessageTime")));
        if (newer) {
            // 预览不暴露图片裸路径：图片消息显示「[图片] 配文」
            conv.put("lastMessage", previewOf(msg));
            conv.put("lastMessageTime", msg.getCreatedTime());
            conv.put("lastMessageId", msgId);
        }
        if (fromThem && msg.getIsRead() != null && msg.getIsRead() == 0) {
            conv.put("unreadCount", ((Number) conv.get("unreadCount")).intValue() + 1);
        }
    }

    public Message findById(Long id) {
        return messageRepository.selectById(id);
    }

    public int countUnread(Long userId) {
        return messageRepository.countUnread(userId);
    }

    /** 单条消息内容长度上限 */
    public static final int MAX_CONTENT_LENGTH = 1000;

    /** 图片消息配文长度上限 */
    public static final int MAX_CAPTION_LENGTH = 200;

    /** 消息类型：图片 */
    public static final int TYPE_IMAGE = 1;

    /**
     * 图片消息 content 的合法形态。图片必须先经 {@code POST /api/upload/image}
     * 落盘拿到 {@code /uploads/yyyy/MM/<32位hex>.<ext>} 路径，再把路径作为消息发出；
     * 这里拒绝任何外部 URL 与路径穿越，防止图片消息被当成任意外链/资源注入位。
     * 扩展名与 {@code UploadService#TYPE_TO_EXT} 的落盘命名保持一致。
     */
    private static final java.util.regex.Pattern UPLOAD_PATH =
            java.util.regex.Pattern.compile("^/uploads/\\d{4}/\\d{2}/[0-9a-f]{32}\\.(jpg|png|gif|webp)$");

    /** 会话列表「最后一条消息」预览：图片消息不暴露裸路径 */
    public static String previewOf(Message msg) {
        if (msg != null && msg.getMsgType() != null && msg.getMsgType() == TYPE_IMAGE) {
            String caption = msg.getCaption();
            return caption == null || caption.isBlank() ? "[图片]" : "[图片] " + caption.trim();
        }
        return msg == null ? "" : msg.getContent();
    }

    /**
     * 发送消息。
     *
     * <p>此前本方法不做任何校验：内容可为空、可超长、可以给自己发消息、
     * 接收人不存在也照发入库，导致脏数据与前端莫名失败。此处集中兜住。</p>
     *
     * @throws IllegalArgumentException 校验不通过，调用方转为 400 业务响应
     */
    public Message send(Message message) {
        Long senderId = message.getSenderId();
        Long receiverId = message.getReceiverId();

        if (senderId == null || receiverId == null) {
            throw new IllegalArgumentException("发送人或接收人为空");
        }
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("不能给自己发消息");
        }
        if (userRepository.selectById(receiverId) == null) {
            throw new IllegalArgumentException("接收用户不存在");
        }

        String content = message.getContent();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        content = content.trim();
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("消息内容不能超过 " + MAX_CONTENT_LENGTH + " 个字符");
        }

        // 消息类型：缺省/非法值一律归为文本，只有显式 1 才是图片
        boolean isImage = message.getMsgType() != null && message.getMsgType() == TYPE_IMAGE;
        if (!isImage) {
            message.setMsgType(0);
            message.setCaption(null); // 文本消息不携带配文
        } else {
            // 图片消息：content 必须是本站上传目录里的图片路径
            if (!UPLOAD_PATH.matcher(content).matches()) {
                throw new IllegalArgumentException("图片消息无效，请重新上传图片");
            }
            String caption = message.getCaption() == null ? "" : message.getCaption().trim();
            if (caption.length() > MAX_CAPTION_LENGTH) {
                throw new IllegalArgumentException("图片配文不能超过 " + MAX_CAPTION_LENGTH + " 个字符");
            }
            message.setCaption(caption.isEmpty() ? null : caption);
        }

        // 附带商品上下文时校验商品确实存在，避免挂到已删除商品上
        if (message.getProductId() != null
                && productRepository.selectById(message.getProductId()) == null) {
            message.setProductId(null);
        }

        message.setContent(content);
        message.setCreatedTime(LocalDateTime.now());
        message.setIsRead(0);
        messageRepository.insert(message);
        return message;
    }

    /**
     * 把「我收到的」一条消息标记为已读。
     *
     * <p>userId 不是可选项：消息 id 是自增的，只按 id 更新等于把
     * 「标记已读」开放给所有登录用户，别人可以把你的未读消息清空。
     * 把接收人写进 UPDATE 的 WHERE 后，越权请求影响 0 行。</p>
     *
     * @return true 表示确实更新了属于该用户的消息
     */
    public boolean markAsRead(Long id, Long userId) {
        if (id == null || userId == null) {
            return false;
        }
        return messageRepository.markAsRead(id, userId) > 0;
    }

    public boolean markAllAsRead(Long userId) {
        if (userId == null) {
            return false;
        }
        // 单条 UPDATE 完成，避免「查出全部消息再逐条全字段更新」的 N+1 写入
        messageRepository.markAllAsRead(userId);
        return true;
    }

    public int delete(Long id) {
        return messageRepository.deleteById(id);
    }
}
