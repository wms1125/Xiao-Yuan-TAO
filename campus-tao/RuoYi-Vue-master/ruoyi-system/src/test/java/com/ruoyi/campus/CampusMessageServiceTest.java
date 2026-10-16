package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.domain.CampusMessage;
import com.ruoyi.campus.domain.CampusOrder;
import com.ruoyi.campus.mapper.CampusMessageMapper;
import com.ruoyi.campus.mapper.CampusOrderMapper;
import com.ruoyi.campus.service.impl.CampusMessageServiceImpl;
import com.ruoyi.common.exception.ServiceException;

/**
 * Service 层测试：站内信（订单上下文内的买卖双方沟通）。
 *
 * <p>本模块的**安全重心**不是"消息能发出去"，而是三条越权防线：
 * <ol>
 *   <li><b>发送方必须是订单当事方</b>——否则知道订单号就能给任意订单刷骚扰消息；</li>
 *   <li><b>接收方由服务端从订单推导</b>——前端传的 {@code toUserId} 必须通过
 *       "是否为订单当事方"的校验，否则传任意用户 ID 即可骚扰他人；</li>
 *   <li><b>读取与已读均按 to_user_id / 当事方校验</b>——知道订单号不能读到别人的沟通内容，
 *       也不能把别人的消息标成已读。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 · 站内信 Service")
class CampusMessageServiceTest {

    private static final String ORDER_NO = "CT202601010001";
    private static final long BUYER_ID = 2L;
    private static final long SELLER_ID = 3L;
    /** 与订单无关的第三方 */
    private static final long STRANGER_ID = 99L;

    @Mock
    private CampusMessageMapper messageMapper;

    @Mock
    private CampusOrderMapper orderMapper;

    @InjectMocks
    private CampusMessageServiceImpl messageService;

    private CampusOrder order(Long buyerId) {
        CampusOrder o = new CampusOrder();
        o.setOrderNo(ORDER_NO);
        o.setUserId(buyerId);
        o.setStatus(2);
        return o;
    }

    // ---------------- 发送：接收方推导 ----------------

    @Test
    @DisplayName("卖家发送：接收方固定为买方，忽略前端传入的 toUserId")
    void sellerSendGoesToBuyer() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        // 订单当事方 = 买方 + 卖方
        when(messageMapper.selectPartyUserIds(ORDER_NO)).thenReturn(Arrays.asList(BUYER_ID, SELLER_ID));

        messageService.send(SELLER_ID, ORDER_NO, STRANGER_ID, "已发货，请注意查收");

        ArgumentCaptor<CampusMessage> captor = ArgumentCaptor.forClass(CampusMessage.class);
        verify(messageMapper).insertCampusMessage(captor.capture());
        CampusMessage sent = captor.getValue();
        assertEquals(Long.valueOf(SELLER_ID), sent.getFromUserId());
        // 关键：即使前端传了 toUserId=99，接收方仍被推导为买方
        assertEquals(Long.valueOf(BUYER_ID), sent.getToUserId());
        assertEquals("已发货，请注意查收", sent.getContent());
    }

    @Test
    @DisplayName("买方发送（单一卖家）：接收方推导为该卖家")
    void buyerSendGoesToSoleSeller() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        when(messageMapper.selectPartyUserIds(ORDER_NO)).thenReturn(Arrays.asList(BUYER_ID, SELLER_ID));

        messageService.send(BUYER_ID, ORDER_NO, null, "请问还能便宜点吗");

        ArgumentCaptor<CampusMessage> captor = ArgumentCaptor.forClass(CampusMessage.class);
        verify(messageMapper).insertCampusMessage(captor.capture());
        assertEquals(Long.valueOf(SELLER_ID), captor.getValue().getToUserId());
    }

    @Test
    @DisplayName("负向：非订单当事方发送被拒绝（防止给任意订单刷骚扰消息）")
    void strangerSendRejected() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        when(messageMapper.selectPartyUserIds(ORDER_NO)).thenReturn(Arrays.asList(BUYER_ID, SELLER_ID));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> messageService.send(STRANGER_ID, ORDER_NO, BUYER_ID, "垃圾信息"));

        assertEquals("只有订单的买卖双方可以沟通", ex.getMessage());
        verify(messageMapper, never()).insertCampusMessage(any());
    }

    @Test
    @DisplayName("负向：订单不存在时发送被拒绝")
    void sendRejectsMissingOrder() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> messageService.send(BUYER_ID, ORDER_NO, SELLER_ID, "你好"));

        assertTrue(ex.getMessage().contains("订单不存在"));
        verify(messageMapper, never()).insertCampusMessage(any());
    }

    @Test
    @DisplayName("负向：空内容与超长内容被拒绝")
    void sendRejectsInvalidContent() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        // 注意：内容校验发生在"当事方校验"之前，故此处无需 stub selectPartyUserIds——
        // 若 stub 了反而说明校验顺序被调换了。

        assertThrows(ServiceException.class, () -> messageService.send(BUYER_ID, ORDER_NO, null, "   "));
        assertThrows(ServiceException.class,
                () -> messageService.send(BUYER_ID, ORDER_NO, null, "x".repeat(501)));
        verify(messageMapper, never()).insertCampusMessage(any());
    }

    @Test
    @DisplayName("负向：给自己发消息被拒绝（单一卖家的自买自卖边界）")
    void sendToSelfRejected() {
        // 构造一个 userId 同时是买方与唯一"卖方"的退化场景
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        when(messageMapper.selectPartyUserIds(ORDER_NO)).thenReturn(Collections.singletonList(BUYER_ID));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> messageService.send(BUYER_ID, ORDER_NO, null, "自言自语"));

        // 卖方集合为空 → 无法确定接收方
        assertTrue(ex.getMessage().contains("无法确定消息接收方"));
        verify(messageMapper, never()).insertCampusMessage(any());
    }

    @Test
    @DisplayName("跨多卖家：买方未指定接收方时要求显式指定")
    void multiSellerRequiresExplicitRecipient() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        when(messageMapper.selectPartyUserIds(ORDER_NO))
                .thenReturn(Arrays.asList(BUYER_ID, SELLER_ID, 4L));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> messageService.send(BUYER_ID, ORDER_NO, null, "你好"));

        assertEquals("该订单包含多位卖家的商品，请指定要沟通的卖家", ex.getMessage());
    }

    @Test
    @DisplayName("跨多卖家：指定的接收方不是该订单卖家时被拒绝")
    void multiSellerRejectsNonSeller() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        when(messageMapper.selectPartyUserIds(ORDER_NO))
                .thenReturn(Arrays.asList(BUYER_ID, SELLER_ID, 4L));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> messageService.send(BUYER_ID, ORDER_NO, STRANGER_ID, "你好"));

        assertEquals("指定的接收方不是该订单的卖家", ex.getMessage());
        verify(messageMapper, never()).insertCampusMessage(any());
    }

    @Test
    @DisplayName("跨多卖家：指定合法卖家时发送成功")
    void multiSellerAcceptsValidSeller() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        when(messageMapper.selectPartyUserIds(ORDER_NO))
                .thenReturn(Arrays.asList(BUYER_ID, SELLER_ID, 4L));

        CampusMessage result = messageService.send(BUYER_ID, ORDER_NO, 4L, "你好");

        assertNotNull(result);
        assertEquals(Long.valueOf(4L), result.getToUserId());
    }

    @Test
    @DisplayName("插入时未读标记置 0，且 createTime 由 DB 默认填充")
    void insertMarksUnread() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        when(messageMapper.selectPartyUserIds(ORDER_NO)).thenReturn(Arrays.asList(BUYER_ID, SELLER_ID));

        messageService.send(BUYER_ID, ORDER_NO, null, "你好");

        ArgumentCaptor<CampusMessage> captor = ArgumentCaptor.forClass(CampusMessage.class);
        verify(messageMapper).insertCampusMessage(captor.capture());
        assertEquals(Integer.valueOf(0), captor.getValue().getIsRead());
    }

    // ---------------- 读取：当事方校验 ----------------

    @Test
    @DisplayName("负向：非当事方读取订单会话被拒绝（防泄露他人沟通内容）")
    void strangerCannotReadConversation() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        when(messageMapper.selectPartyUserIds(ORDER_NO)).thenReturn(Arrays.asList(BUYER_ID, SELLER_ID));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> messageService.selectByOrderNo(ORDER_NO, STRANGER_ID));

        assertEquals("无权查看该订单的沟通记录", ex.getMessage());
        verify(messageMapper, never()).selectByOrderNo(ORDER_NO);
    }

    @Test
    @DisplayName("当事方可读取订单会话")
    void partyCanReadConversation() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        when(messageMapper.selectPartyUserIds(ORDER_NO)).thenReturn(Arrays.asList(BUYER_ID, SELLER_ID));

        messageService.selectByOrderNo(ORDER_NO, SELLER_ID);

        verify(messageMapper).selectByOrderNo(ORDER_NO);
    }

    // ---------------- 已读 ----------------

    @Test
    @DisplayName("标记已读：SQL 必须带上 userId，不能把别人的消息标成已读")
    void markReadPassesUserId() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        when(messageMapper.selectPartyUserIds(ORDER_NO)).thenReturn(Arrays.asList(BUYER_ID, SELLER_ID));
        when(messageMapper.markReadByOrder(ORDER_NO, BUYER_ID)).thenReturn(2);

        int rows = messageService.markRead(ORDER_NO, BUYER_ID);

        assertEquals(2, rows);
        verify(messageMapper).markReadByOrder(ORDER_NO, BUYER_ID);
    }

    @Test
    @DisplayName("负向：非当事方标记已读被拒绝")
    void strangerCannotMarkRead() {
        when(orderMapper.selectOrderByOrderNo(ORDER_NO)).thenReturn(order(BUYER_ID));
        when(messageMapper.selectPartyUserIds(ORDER_NO)).thenReturn(Arrays.asList(BUYER_ID, SELLER_ID));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> messageService.markRead(ORDER_NO, STRANGER_ID));

        assertEquals("无权查看该订单的沟通记录", ex.getMessage());
        verify(messageMapper, never()).markReadByOrder(eq(ORDER_NO), any());
    }

    // ---------------- 未读数与会话列表 ----------------

    @Test
    @DisplayName("未读数与会话列表：直接委派 Mapper")
    void unreadAndConversationsDelegate() {
        when(messageMapper.countUnread(BUYER_ID)).thenReturn(5);

        assertEquals(5, messageService.countUnread(BUYER_ID));
        messageService.selectConversations(BUYER_ID);
        verify(messageMapper).selectConversations(BUYER_ID);
    }

    @Test
    @DisplayName("负向：未登录访问会话列表被拒绝")
    void conversationsRejectsNullUser() {
        assertThrows(ServiceException.class, () -> messageService.selectConversations(null));
        verify(messageMapper, never()).selectConversations(any());
    }
}
