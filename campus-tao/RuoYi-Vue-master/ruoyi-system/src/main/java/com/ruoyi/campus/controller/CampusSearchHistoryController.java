package com.ruoyi.campus.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.service.ICampusSearchHistoryService;

import java.util.HashMap;
import java.util.Map;

/**
 * 搜索历史 Controller（校园淘）
 *
 * <p>搜索历史是<b>买家私有数据</b>，用户身份一律取自登录态，不接受前端传 userId。</p>
 *
 * @author campus-tao
 */
@Validated
@RestController
@RequestMapping("/campus/searchhistory")
public class CampusSearchHistoryController extends BaseController {

    @Autowired
    private ICampusSearchHistoryService historyService;

    /** 最近搜索（默认 10 条，用于商城页搜索框下拉） */
    @PreAuthorize("@ss.hasPermi('campus:searchhistory:list')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(value = "limit", required = false) Integer limit) {
        int safeLimit = limit == null ? 10 : limit;
        return AjaxResult.success(historyService.selectRecent(SecurityUtils.getUserId(), safeLimit));
    }

    /**
     * 记录一次搜索。空搜索词不记录。
     *
     * <p>放在独立接口而不是耦合在商品搜索接口里，是为了让"记录历史"失败
     * 不影响"搜索"本身的可用性——搜索是主流程，记录是旁路。</p>
     */
    @PreAuthorize("@ss.hasPermi('campus:searchhistory:add')")
    @PostMapping("/record")
    public AjaxResult record(@RequestBody RecordBody body) {
        historyService.record(SecurityUtils.getUserId(), body.getKeyword());
        return AjaxResult.success("已记录");
    }

    /** 删除一条历史 */
    @PreAuthorize("@ss.hasPermi('campus:searchhistory:remove')")
    @DeleteMapping("/{historyId}")
    public AjaxResult remove(@PathVariable Long historyId) {
        historyService.remove(historyId, SecurityUtils.getUserId());
        return AjaxResult.success("已删除");
    }

    /** 清空全部历史 */
    @PreAuthorize("@ss.hasPermi('campus:searchhistory:remove')")
    @DeleteMapping("/clear")
    public AjaxResult clear() {
        int rows = historyService.clear(SecurityUtils.getUserId());
        Map<String, Object> data = new HashMap<>(2);
        data.put("cleared", rows);
        return AjaxResult.success("已清空 " + rows + " 条", data);
    }

    /** 记录请求体 */
    public static class RecordBody {
        private String keyword;

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }
    }
}
