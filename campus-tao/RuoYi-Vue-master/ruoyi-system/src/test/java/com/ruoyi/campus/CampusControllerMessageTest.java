package com.ruoyi.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
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
import com.ruoyi.campus.domain.vo.CampusConversationPage;
import com.ruoyi.campus.service.ICampusMessageService;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.exception.ServiceException;

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
    @DisplayName("会话列表：以登录用户为作用域，按分页参数查询")
    void listUsesLoginUser() {
        when(messageService.selectConversations(STUDENT_ID, 1, 20))
                .thenReturn(new CampusConversationPage(1L, Collections.emptyList()));

        TableDataInfo result = messageController.list(1, 20);

        assertEquals(HttpStatus.SUCCESS, result.getCode());
        verify(messageService).selectConversations(STUDENT_ID, 1, 20);
    }

    @Test
    @DisplayName("会话列表：返回标准表格分页结构，total 与 rows 同源")
    void listReturnsTableDataInfo() {
        List<Map<String, Object>> rows = Collections.singletonList(
                Collections.singletonMap("orderNo", ORDER_NO));
        when(messageService.selectConversations(STUDENT_ID, 1, 20))
                .thenReturn(new CampusConversationPage(7L, rows));

        TableDataInfo result = messageController.list(1, 20);

        assertEquals(HttpStatus.SUCCESS, result.getCode());
        assertEquals(7L, result.getTotal());
        // 行不能丢：total 与 rows 必须是同一次查询的结果
        assertEquals(rows, result.getRows());
    }

    @Test
    @DisplayName("会话列表：越界分页参数照样传服务端，由其归一后返回空行")
    void listPassesOutOfRangePageDown() {
        when(messageService.selectConversations(STUDENT_ID, 0, 0))
                .thenReturn(new CampusConversationPage(0L, Collections.emptyList()));

        assertEquals(0L, messageController.list(0, 0).getTotal());
        // Controller 不自己"修正"参数——归一规则只有 Service 一处，避免两处漂移
        verify(messageService).selectConversations(STUDENT_ID, 0, 0);
    }

    @Test
    @DisplayName("负向：超大每页条数原样下传，由服务端拒绝（Controller 不静默截断）")
    void listPassesOversizedPageSizeDown() {
        when(messageService.selectConversations(STUDENT_ID, 1, ICampusMessageService.MAX_PAGE_SIZE + 1))
                .thenThrow(new ServiceException("每页最多 " + ICampusMessageService.MAX_PAGE_SIZE + " 条会话"));

        // 若 Controller 自行截断到 100，这里拿到的是"正常分页结果"，
        // 调用方永远不知道自己的请求被改了——所以截断必须由服务端显式报错。
        ServiceException ex = assertThrows(ServiceException.class,
                () -> messageController.list(1, ICampusMessageService.MAX_PAGE_SIZE + 1));
        assertEquals("每页最多 " + ICampusMessageService.MAX_PAGE_SIZE + " 条会话", ex.getMessage());
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
