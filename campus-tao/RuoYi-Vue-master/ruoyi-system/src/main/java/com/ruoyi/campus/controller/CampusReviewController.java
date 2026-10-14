package com.ruoyi.campus.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.campus.domain.CampusReview;
import com.ruoyi.campus.dto.CampusReviewAddDTO;
import com.ruoyi.campus.service.ICampusReviewService;

/**
 * 评价 Controller（校园淘）
 */
@Validated
@RestController
@RequestMapping("/campus/review")
public class CampusReviewController extends BaseController {
    @Autowired
    private ICampusReviewService reviewService;

    @PreAuthorize("@ss.hasPermi('campus:review:add')")
    @PostMapping("/add")
    public AjaxResult add(@Valid @RequestBody CampusReviewAddDTO body) {
        reviewService.addReview(SecurityUtils.getUserId(), SecurityUtils.getUsername(),
                body.getOrderId(), body.getProductId(), body.getRating(),
                body.getContent(), body.getImages());
        return AjaxResult.success("评价成功");
    }

    @Anonymous
    @GetMapping("/product/{productId}")
    public AjaxResult listByProduct(@PathVariable Long productId) {
        List<CampusReview> list = reviewService.selectByProductId(productId);
        return AjaxResult.success(list);
    }
}
