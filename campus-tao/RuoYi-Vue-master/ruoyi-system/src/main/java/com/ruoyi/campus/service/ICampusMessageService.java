package com.ruoyi.campus.service;

import com.ruoyi.campus.domain.CampusMessage;
import com.ruoyi.campus.domain.vo.CampusConversationPage;

import java.util.List;

/**
 * 站内信服务（校园淘）
 *
 * <p>会话以订单为上下文：买卖双方围绕某一笔订单沟通。发送前<b>必须校验接收方是订单当事方</b>，
 * 接收方由服务端推导，不接受前端直接传 {@code toUserId}。</p>
 *
 * @author campus-tao
 */
public interface ICampusMessageService {

    /**
     * 会话列表（分页）：我参与过的订单，最新在前，带未读数。
     *
     * <p><b>只提供分页版本</b>：会话数随订单线性增长，无界列表会让响应体与聚合耗时一起失控。
     * {@code pageSize} 超过 {@link #MAX_PAGE_SIZE} 会被拒绝而不是静默截断——
     * 静默截断会让调用方以为"这就是全部"。</p>
     *
     * @return 行与总数同源，前端可直接渲染分页控件
     */
    CampusConversationPage selectConversations(Long userId, int pageNum, int pageSize);

    /** 默认每页条数 */
    int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数硬上限（超过即报错，防止一次拉全量） */
    int MAX_PAGE_SIZE = 100;

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
