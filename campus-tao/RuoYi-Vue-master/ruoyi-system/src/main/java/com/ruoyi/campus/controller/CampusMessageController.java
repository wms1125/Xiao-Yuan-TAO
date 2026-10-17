package com.ruoyi.campus.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.domain.CampusMessage;
import com.ruoyi.campus.domain.vo.CampusConversationPage;
import com.ruoyi.campus.service.ICampusMessageService;

import java.util.HashMap;
import java.util.Map;

/**
 * 站内信 Controller（校园淘）
 *
 * <p>会话以订单为上下文。发送接口的<b>接收方由服务端从订单推导</b>——
 * 前端可传 {@code toUserId}（订单跨多卖家时用于指定卖家），但服务端会校验其
 * 必须是订单当事方，因此该入参不是"想发给谁就发给谁"的自由字段。</p>
 *
 * @author campus-tao
 */
@Validated
@RestController
@RequestMapping("/campus/message")
public class CampusMessageController extends BaseController {

    @Autowired
    private ICampusMessageService messageService;

    /**
     * 会话列表（分页，最新在前）。
     *
     * <p>曾经这里是无界列表：用户参与过的订单越多，返回体越大、聚合越慢。
     * 现在是标准的 {@link TableDataInfo}——与我的订单/售后列表保持一致，
     * 且不再有任何"取全量"的入口。</p>
     *
     * @param pageNum 页码，从 1 开始；小于 1 归一为 1
     * @param pageSize 每页条数，超过 {@link ICampusMessageService#MAX_PAGE_SIZE} 直接报错
     */
    @PreAuthorize("@ss.hasPermi('campus:message:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "20") Integer pageSize) {
        CampusConversationPage page = messageService.selectConversations(
                SecurityUtils.getUserId(), pageNum, pageSize);
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setMsg("查询成功");
        rspData.setTotal(page.getTotal());
        rspData.setRows(page.getRows());
        return rspData;
    }

    /** 我的未读消息总数（侧边栏红点用） */
    @PreAuthorize("@ss.hasPermi('campus:message:list')")
    @GetMapping("/unread")
    public AjaxResult unread() {
        Map<String, Object> data = new HashMap<>(2);
        data.put("count", messageService.countUnread(SecurityUtils.getUserId()));
        return AjaxResult.success(data);
    }

    /** 某订单会话的消息（仅买卖双方可见） */
    @PreAuthorize("@ss.hasPermi('campus:message:list')")
    @GetMapping("/order/{orderNo}")
    public AjaxResult orderMessages(@PathVariable String orderNo) {
        return AjaxResult.success(
                messageService.selectByOrderNo(orderNo, SecurityUtils.getUserId()));
    }

    /** 发送消息 */
    @PreAuthorize("@ss.hasPermi('campus:message:add')")
    @PostMapping("/send")
    public AjaxResult send(@RequestBody SendBody body) {
        CampusMessage sent = messageService.send(
                SecurityUtils.getUserId(), body.getOrderNo(), body.getToUserId(), body.getContent());
        return AjaxResult.success("消息已发送", sent);
    }

    /** 把某订单下发给我的未读消息标记为已读 */
    @PreAuthorize("@ss.hasPermi('campus:message:list')")
    @PostMapping("/read/{orderNo}")
    public AjaxResult read(@PathVariable String orderNo) {
        int rows = messageService.markRead(orderNo, SecurityUtils.getUserId());
        Map<String, Object> data = new HashMap<>(2);
        data.put("marked", rows);
        return AjaxResult.success("已标记 " + rows + " 条为已读", data);
    }

    /** 发送请求体 */
    public static class SendBody {
        private String orderNo;
        /** 可选：订单跨多卖家时指定接收的卖家；服务端会校验其必须是订单当事方 */
        private Long toUserId;
        private String content;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public Long getToUserId() {
            return toUserId;
        }

        public void setToUserId(Long toUserId) {
            this.toUserId = toUserId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
