package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusMessage;

import java.util.List;
import java.util.Map;

/**
 * 站内信服务（校园淘）
 *
 * <p>会话以订单为上下文：买卖双方围绕某一笔订单沟通。发送前<b>必须校验接收方是订单当事方</b>，
 * 接收方由服务端推导，不接受前端直接传 {@code toUserId}。</p>
 *
 * @author campus-tao
 */
public interface ICampusMessageService {

    /** 会话列表（我参与过的订单，最新在前，带未读数） */
    List<Map<String, Object>> selectConversations(Long userId);

    /** 某订单会话的消息（双方可见，按时间正序） */
    List<CampusMessage> selectByOrderNo(String orderNo, Long userId);

    /** 我的未读消息总数 */
    int countUnread(Long userId);

    /** 某订单下发给我的未读数 */
    int countUnreadByOrder(String orderNo, Long userId);

    /**
     * 发送消息。
     *
     * <p>接收方规则：我是买方 → 接收方为该订单的卖方；我是卖方 → 接收方为买方。
     * 订单跨多个卖家时，买方需通过 {@code toUserId} 指定给哪位卖家，
     * 但该值<b>必须通过"是否为订单当事方"的校验</b>，否则拒绝。</p>
     */
    CampusMessage send(Long userId, String orderNo, Long toUserId, String content);

    /** 把某订单下发给我的未读消息标记为已读，返回被标记的条数 */
    int markRead(String orderNo, Long userId);
}
