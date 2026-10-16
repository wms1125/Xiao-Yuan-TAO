package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.campus.controller.CampusMessageController;
import com.ruoyi.campus.controller.CampusMessageController.SendBody;
import com.ruoyi.campus.domain.CampusMessage;
import com.ruoyi.campus.service.ICampusMessageService;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * Controller 层测试：站内信接口。
 *
 * <p>核心断言是**身份来源与接收方不可信**：所有接口的用户身份取自登录态；
 * 发送接口即便前端传了 {@code toUserId}，也**必须**原样转交服务端做当事方校验——
 * Controller 不得自行"信任"该字段。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("校园淘 · 站内信 Controller")
class CampusControllerMessageTest {

    private static final long STUDENT_ID = 2L;
    private static final String ORDER_NO = "CT202601010001";

    @Mock
    private ICampusMessageService messageService;

    @InjectMocks
    private CampusMessageController messageController;

    @BeforeEach
    void setUp() {
        CampusTestSupport.loginAs(STUDENT_ID, "ry");
    }

    @AfterEach
    void tearDown() {
        CampusTestSupport.clear();
    }

    private SendBody body(String orderNo, Long toUserId, String content) {
        SendBody b = new SendBody();
        b.setOrderNo(orderNo);
        b.setToUserId(toUserId);
        b.setContent(content);
        return b;
    }

    @Test
    @DisplayName("会话列表：以登录用户为作用域")
    void listUsesLoginUser() {
        AjaxResult result = messageController.list();

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(messageService).selectConversations(STUDENT_ID);
    }

    @Test
    @DisplayName("未读数：以登录用户为作用域")
    void unreadUsesLoginUser() {
        when(messageService.countUnread(STUDENT_ID)).thenReturn(3);

        AjaxResult result = messageController.unread();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals(3, data.get("count"));
    }

    @Test
    @DisplayName("订单会话：以登录用户为作用域查询")
    void orderMessagesUsesLoginUser() {
        messageController.orderMessages(ORDER_NO);

        verify(messageService).selectByOrderNo(ORDER_NO, STUDENT_ID);
    }

    @Test
    @DisplayName("发送：userId 取自登录态，toUserId 原样转交服务端校验")
    void sendPassesToUserIdThroughForValidation() {
        CampusMessage sent = new CampusMessage();
        when(messageService.send(STUDENT_ID, ORDER_NO, 99L, "你好")).thenReturn(sent);

        AjaxResult result = messageController.send(body(ORDER_NO, 99L, "你好"));

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        // 关键：Controller 不校验、不改写 toUserId，把它交给服务端判"是否为订单当事方"
        verify(messageService).send(STUDENT_ID, ORDER_NO, 99L, "你好");
    }

    @Test
    @DisplayName("发送：跨多卖家时 toUserId 可为空，由服务端推导")
    void sendAllowsNullToUserId() {
        messageController.send(body(ORDER_NO, null, "你好"));

        verify(messageService).send(STUDENT_ID, ORDER_NO, null, "你好");
    }

    @Test
    @DisplayName("标记已读：以登录用户为作用域，返回被标记条数")
    void readUsesLoginUser() {
        when(messageService.markRead(ORDER_NO, STUDENT_ID)).thenReturn(2);

        AjaxResult result = messageController.read(ORDER_NO);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals(2, data.get("marked"));
        verify(messageService).markRead(ORDER_NO, STUDENT_ID);
    }

    @Test
    @DisplayName("负向：没有任何接口接受请求体中的 userId")
    void noEndpointAcceptsUserId() {
        // 会话列表与未读数都不接收 userId 入参（编译期保证）；
        // 此处验证其行为确实只以登录态为作用域。
        messageController.unread();
        verify(messageService).countUnread(STUDENT_ID);
        verify(messageService, never()).countUnread(999L);
    }

    @Test
    @DisplayName("发送：orderNo 与 content 原样透传，不做改写")
    void sendPassesOrderNoAndContentThrough() {
        messageController.send(body("CT202601010002", 5L, "含空格的内容 "));

        verify(messageService).send(STUDENT_ID, "CT202601010002", 5L, "含空格的内容 ");
    }
}
