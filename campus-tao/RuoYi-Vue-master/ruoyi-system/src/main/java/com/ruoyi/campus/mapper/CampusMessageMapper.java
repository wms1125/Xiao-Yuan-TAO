package com.ruoyi.campus.mapper;

import com.ruoyi.campus.domain.CampusMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 站内信 Mapper（校园淘）
 *
 * @author campus-tao
 */
public interface CampusMessageMapper {

    /** 订单会话内的消息（双方可见，按时间正序） */
    List<CampusMessage> selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 会话列表（分页）：我参与过的订单（作为买方或卖方），带最后一条消息与未读数。
     *
     * <p>分页在<b>内层聚合</b>上做，limit 参数是 {@code offset,limit} 两个值，
     * 由 Service 负责越界保护。不再提供任何无界版本——调用方不可能取到全量列表。</p>
     */
    List<Map<String, Object>> selectConversationsPaged(@Param("userId") Long userId,
            @Param("offset") long offset, @Param("limit") int limit);

    /** 会话总数：where 必须与 {@link #selectConversationsPaged} 的内层逐字一致 */
    long countConversations(@Param("userId") Long userId);

    /** 我的未读消息总数 */
    int countUnread(@Param("userId") Long userId);

    /** 某订单下发给我的未读数 */
    int countUnreadByOrder(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    /** 该订单涉及的用户 ID（买方 + 所有卖方），用于发送前的当事方校验 */
    List<Long> selectPartyUserIds(@Param("orderNo") String orderNo);

    int insertCampusMessage(CampusMessage message);

    /**
     * 把某订单下发给我的未读消息标记为已读。<b>SQL 带 to_user_id</b>，
     * 不可能把别人的消息标成已读。
     */
    int markReadByOrder(@Param("orderNo") String orderNo, @Param("userId") Long userId);
}
