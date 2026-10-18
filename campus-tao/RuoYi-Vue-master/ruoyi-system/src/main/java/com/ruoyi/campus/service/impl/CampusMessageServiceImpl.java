package com.ruoyi.campus.service.impl;

import com.ruoyi.campus.domain.CampusMessage;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.domain.vo.CampusConversationPage;
import com.ruoyi.campus.mapper.CampusMessageMapper;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.service.ICampusMessageService;
import com.ruoyi.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ruoyi.campus.service.ICampusMessageService.DEFAULT_PAGE_SIZE;
import static com.ruoyi.campus.service.ICampusMessageService.MAX_PAGE_SIZE;

/**
 * 站内信服务实现（校园淘）
 *
 * @author campus-tao
 */
@Service
public class CampusMessageServiceImpl implements ICampusMessageService {

    /** 单条消息长度上限（与表定义 varchar(500) 对齐，留余量） */
    private static final int MAX_CONTENT_LENGTH = 500;

    @Autowired
    private CampusMessageMapper messageMapper;

    @Autowired
    private CampusOrderMapper orderMapper;

    @Override
    public CampusConversationPage selectConversations(Long userId, int pageNum, int pageSize) {
        requireUser(userId);

        // 页码归一：小于 1 一律当第 1 页，不接受负数页码把 offset 算成负数
        int page = Math.max(pageNum, 1);
        int size = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        if (size > MAX_PAGE_SIZE) {
            // 报错而非静默截断：截断会让调用方误以为拿到的就是全部会话
            throw new ServiceException("每页最多 " + MAX_PAGE_SIZE + " 条会话");
        }

        long total = messageMapper.countConversations(userId);
        // 越界页不再打一次数据库：offset 已超过总数时结果必为空，
        // 直接返回空行 + 正确 total（翻页控件据此能跳到末页）
        long offset = (long) (page - 1) * size;
        if (offset >= total) {
            return new CampusConversationPage(total, Collections.emptyList());
        }
        List<Map<String, Object>> rows = messageMapper.selectConversationsPaged(userId, offset, size);
        return new CampusConversationPage(total, rows);
    }

    @Override
    public List<CampusMessage> selectByOrderNo(String orderNo, Long userId) {
        requireUser(userId);
        requireOrder(orderNo);
        // 只有订单当事方才可读该会话——否则知道订单号就能读到别人的沟通内容
        if (!isParty(orderNo, userId)) {
            throw new ServiceException("无权查看该订单的沟通记录");
        }
        return messageMapper.selectByOrderNo(orderNo);
    }

    @Override
    public int countUnread(Long userId) {
        requireUser(userId);
        return messageMapper.countUnread(userId);
    }

    @Override
    public int countUnreadByOrder(String orderNo, Long userId) {
        requireUser(userId);
        return messageMapper.countUnreadByOrder(orderNo, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampusMessage send(Long userId, String orderNo, Long toUserId, String content) {
        requireUser(userId);
        requireOrder(orderNo);
        if (content == null || content.trim().isEmpty()) {
            throw new ServiceException("消息内容不能为空");
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new ServiceException("消息内容过长（最多 " + MAX_CONTENT_LENGTH + " 字）");
        }

        // 发送方必须是订单当事方：防止给任意订单发消息刷骚扰内容
        if (!isParty(orderNo, userId)) {
            throw new ServiceException("只有订单的买卖双方可以沟通");
        }

        Long recipient = resolveRecipient(orderNo, userId, toUserId);
        if (recipient == null) {
            throw new ServiceException("无法确定消息接收方（该订单的卖家信息可能已失效）");
        }
        if (recipient.equals(userId)) {
            throw new ServiceException("不能给自己发送消息");
        }

        CampusMessage message = new CampusMessage();
        message.setOrderNo(orderNo);
        message.setFromUserId(userId);
        message.setToUserId(recipient);
        message.setContent(trimmed);
        message.setIsRead(0);
        messageMapper.insertCampusMessage(message);
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markRead(String orderNo, Long userId) {
        requireUser(userId);
        requireOrder(orderNo);
        if (!isParty(orderNo, userId)) {
            throw new ServiceException("无权查看该订单的沟通记录");
        }
        return messageMapper.markReadByOrder(orderNo, userId);
    }

    /**
     * 推导接收方。
     *
     * <p>我是买方 → 接收方为卖方；我是卖方 → 接收方为买方。
     * 订单跨多个卖家时买方需指定 {@code toUserId}，但该值必须通过当事方校验——
     * 这是防止"传任意用户 ID 即可骚扰他人"的关键闸门。</p>
     */
    private Long resolveRecipient(String orderNo, Long userId, Long toUserId) {
        CampusOrder order = orderMapper.selectOrderByOrderNo(orderNo);
        Long buyerId = order.getUserId();
        boolean iAmBuyer = buyerId.equals(userId);

        if (!iAmBuyer) {
            // 我是卖方，接收方固定为买方，不接受 toUserId（防止卖方冒充买方互发）
            return buyerId;
        }

        // 我是买方：接收方应为卖方
        List<Long> parties = messageMapper.selectPartyUserIds(orderNo);
        Set<Long> sellers = new HashSet<>();
        for (Long p : parties) {
            if (p != null && !p.equals(buyerId)) {
                sellers.add(p);
            }
        }
        if (sellers.isEmpty()) {
            return null;
        }
        if (sellers.size() == 1) {
            return sellers.iterator().next();
        }
        // 跨多个卖家：必须显式指定，且必须是其中之一
        if (toUserId == null) {
            throw new ServiceException("该订单包含多位卖家的商品，请指定要沟通的卖家");
        }
        if (!sellers.contains(toUserId)) {
            throw new ServiceException("指定的接收方不是该订单的卖家");
        }
        return toUserId;
    }

    /** 是否为订单当事方（买方或该订单任一商品的卖方） */
    private boolean isParty(String orderNo, Long userId) {
        for (Long p : messageMapper.selectPartyUserIds(orderNo)) {
            if (p != null && p.equals(userId)) {
                return true;
            }
        }
        return false;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new ServiceException("未登录或登录态已失效");
        }
    }

    private void requireOrder(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new ServiceException("订单号不能为空");
        }
        if (orderMapper.selectOrderByOrderNo(orderNo) == null) {
            throw new ServiceException("订单不存在: " + orderNo);
        }
    }
}
